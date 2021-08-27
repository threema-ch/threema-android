/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package ch.threema.app.voip;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.ThreadUtils;

import java.util.List;
import java.util.Set;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.app.voip.util.AppRTCUtils;
import ch.threema.app.voip.util.VoipUtil;

/**
 * VoipBluetoothManager manages functions related to Bluetoth devices in
 * Threema voice calls.
 */
public class VoipBluetoothManager {
	private static final Logger logger = LoggerFactory.getLogger(VoipBluetoothManager.class);

	// Timeout interval for starting or stopping audio to a Bluetooth SCO device.
	private static final int BLUETOOTH_SCO_TIMEOUT_MS = 4000;
	// Maximum number of SCO connection attempts.
	private static final int MAX_SCO_CONNECTION_ATTEMPTS = 2;

	// Bluetooth connection state.
	public enum State {
		// Bluetooth is not available; no adapter or Bluetooth is off.
		UNINITIALIZED,
		// Bluetooth error happened when trying to start Bluetooth.
		ERROR,
		// Bluetooth proxy object for the Headset profile exists, but no connected headset devices,
		// SCO is not started or disconnected.
		HEADSET_UNAVAILABLE,
		// Bluetooth proxy object for the Headset profile connected, connected Bluetooth headset
		// present, but SCO is not started or disconnected.
		HEADSET_AVAILABLE,
		// Bluetooth audio SCO connection with remote device is closing.
		SCO_DISCONNECTING,
		// Bluetooth audio SCO connection with remote device is initiated.
		SCO_CONNECTING,
		// Bluetooth audio SCO connection with remote device is established.
		SCO_CONNECTED
	}

	private final Context apprtcContext;
	private final VoipAudioManager voipAudioManager;
	private final AudioManager audioManager;
	private final Handler handler;

	int scoConnectionAttempts;
	private State bluetoothState;
	private Long bluetoothAudioConnectedAt;
	private final BluetoothProfile.ServiceListener bluetoothServiceListener;
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothHeadset bluetoothHeadset;
	private BluetoothDevice bluetoothDevice;
	private final BroadcastReceiver bluetoothHeadsetReceiver;

	// Runs when the Bluetooth timeout expires. We use that timeout after calling
	// startScoAudio() or stopScoAudio() because we're not guaranteed to get a
	// callback after those calls.
	private final Runnable bluetoothTimeoutRunnable = new Runnable() {
		@Override
		public void run() {
			bluetoothTimeout();
		}
	};

	/**
	 * Implementation of an interface that notifies BluetoothProfile IPC clients when they have been
	 * connected to or disconnected from the service.
	 */
	private class BluetoothServiceListener implements BluetoothProfile.ServiceListener {
		@Override
		// Called to notify the client when the proxy object has been connected to the service.
		// Once we have the profile proxy object, we can use it to monitor the state of the
		// connection and perform other operations that are relevant to the headset profile.
		public void onServiceConnected(int profile, BluetoothProfile proxy) {
			if (profile != BluetoothProfile.HEADSET || bluetoothState == State.UNINITIALIZED) {
				return;
			}
			logger.debug("BluetoothServiceListener.onServiceConnected: BT state=" + bluetoothState);
			// Android only supports one connected Bluetooth Headset at a time.
			bluetoothHeadset = (BluetoothHeadset) proxy;
			updateAudioDeviceState();
			logger.debug("onServiceConnected done: BT state=" + bluetoothState);
		}

		@Override
		/** Notifies the client when the proxy object has been disconnected from the service. */
		public void onServiceDisconnected(int profile) {
			if (profile != BluetoothProfile.HEADSET || bluetoothState == State.UNINITIALIZED) {
				return;
			}
			logger.debug("BluetoothServiceListener.onServiceDisconnected: BT state=" + bluetoothState);
			stopScoAudio();
			bluetoothHeadset = null;
			bluetoothDevice = null;
			bluetoothState = State.HEADSET_UNAVAILABLE;
			updateAudioDeviceState();
			logger.debug("onServiceDisconnected done: BT state=" + bluetoothState);
		}
	}

	// Intent broadcast receiver which handles changes in Bluetooth device availability.
	// Detects headset changes and Bluetooth SCO state changes.
	private class BluetoothHeadsetBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (bluetoothState == State.UNINITIALIZED) {
				return;
			}

			final String action = intent.getAction();
			if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
				this.onConnectionStateChange(intent);
			} else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
				this.onAudioStateChange(intent);
			} else {
				logger.warn("Unknown bluetooth broadcast action: {}", action);
			}

			logger.debug("onReceive done: BT state={}", bluetoothState);
		}

		/**
		 * Change in connection state of the Headset profile. Note that the
		 * change does not tell us anything about whether we're streaming
		 * audio to BT over SCO. Typically received when user turns on a BT
		 * headset while audio is active using another audio device.
		 */
		private void onConnectionStateChange(Intent intent) {
			final int state =
					intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
			logger.debug("BluetoothHeadsetBroadcastReceiver.onReceive: "
					+ "a=ACTION_CONNECTION_STATE_CHANGED, "
					+ "s=" + headsetStateToString(state) + ", "
					+ "sb=" + isInitialStickyBroadcast() + ", "
					+ "BT state: " + bluetoothState);
			switch (state) {
				case BluetoothHeadset.STATE_CONNECTED:
					scoConnectionAttempts = 0;
					updateAudioDeviceState();
					break;
				case BluetoothHeadset.STATE_CONNECTING:
				case BluetoothHeadset.STATE_DISCONNECTING:
					// No action needed
					break;
				case BluetoothHeadset.STATE_DISCONNECTED:
					// Bluetooth is probably powered off during the call.
					stopScoAudio();
					updateAudioDeviceState();
					break;
			}
		}

		/**
		 * Change in the audio (SCO) connection state of the Headset profile.
		 * Typically received after call to startScoAudio() has finalized.
		 */
		private void onAudioStateChange(Intent intent) {
			final int state = intent.getIntExtra(
				BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);

			logger.debug("BluetoothHeadsetBroadcastReceiver.onReceive: "
				+ "a=ACTION_AUDIO_STATE_CHANGED, "
				+ "s=" + headsetStateToString(state) + ", "
				+ "sb=" + isInitialStickyBroadcast() + ", "
				+ "BT state: " + bluetoothState);

			// Switch BluetoothHeadsetBroadcastReceiver.onReceive: a=ACTION_AUDIO_STATE_CHANGED, s=A_DISCONNECTED, sb=false, BT state: HEADSET_AVAILABLE
			// Btn BluetoothHeadsetBroadcastReceiver.onReceive: a=ACTION_AUDIO_STATE_CHANGED, s=A_DISCONNECTED, sb=false, BT state: SCO_CONNECTED

			if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
				cancelTimer();
				if (bluetoothState == State.SCO_CONNECTING) {
					logger.debug("+++ Bluetooth audio SCO is now connected");
					bluetoothState = State.SCO_CONNECTED;
					bluetoothAudioConnectedAt = System.nanoTime();
					scoConnectionAttempts = 0;
					updateAudioDeviceState();
				} else {
					logger.warn("Unexpected state BluetoothHeadset.STATE_AUDIO_CONNECTED");
				}
			} else if (state == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
				logger.debug("+++ Bluetooth audio SCO is now connecting...");
			} else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
				logger.debug("+++ Bluetooth audio SCO is now disconnected");
				if (isInitialStickyBroadcast()) {
					logger.debug("Ignore STATE_AUDIO_DISCONNECTED initial sticky broadcast.");
				} else if (bluetoothState == State.SCO_CONNECTED) {
					// The bluetooth device is now disconnected after previously having been
					// connected. We now have two options: Either switch audio to the built-in
					// speaker / earpiece, or end the call.
					//
					// Since many bluetooth headsets just have one button, and because the user
					// should be able to hang up the call with that, we'll end the call.
					//
					// Note that some bluetooth devices connect the audio for a very short while
					// and then disconnect it again. This is probably if Android would allow audio
					// connections, but it's turned off inside the device. (This happens e.g. on
					// the Asus ZenWatch devices when bluetooth audio is turned off.) In that
					// case we simply stop bluetooth.
					//
					// There are also headsets that detect whether they are being worn or not.
					// Based on the data from support tickets, this process takes roughly a second.
					// Therefore we set the threshold at 1500 ms to avoid ending the call if the
					// headset disconnects after 1050 ms.
					Long msElapsed = null;
					long msElapsedThreshold = 1500;
					if (bluetoothAudioConnectedAt != null) {
						msElapsed = (System.nanoTime() - bluetoothAudioConnectedAt) / 1000 / 1000;
					}
					logger.info("Time elapsed since bluetooth audio connected: {} ms", msElapsed);

					if (msElapsed == null || msElapsed < msElapsedThreshold) {
						logger.info("Bluetooth headset disconnected. Switching to phone audio.");
						VoipBluetoothManager.this.stop();
						VoipBluetoothManager.this.updateAudioDeviceState();
					} else {
						logger.info("Bluetooth headset disconnected after {} ms. Ending call.", msElapsed);
						VoipUtil.sendVoipCommand(ThreemaApplication.getAppContext(), VoipCallService.class, VoipCallService.ACTION_HANGUP);
					}
				} else {
					// The output device was probably switched via UI
					VoipBluetoothManager.this.updateAudioDeviceState();
				}
			}
		}
	}

	/**
	 * Construction.
	 */
	static VoipBluetoothManager create(Context context, VoipAudioManager audioManager) {
		logger.debug("create" + AppRTCUtils.getThreadInfo());
		return new VoipBluetoothManager(context, audioManager);
	}

	protected VoipBluetoothManager(Context context, VoipAudioManager audioManager) {
		logger.debug("ctor");
		ThreadUtils.checkIsOnMainThread();
		this.apprtcContext = context;
		this.voipAudioManager = audioManager;
		this.audioManager = getAudioManager(context);
		this.bluetoothState = State.UNINITIALIZED;
		this.bluetoothServiceListener = new BluetoothServiceListener();
		this.bluetoothHeadsetReceiver = new BluetoothHeadsetBroadcastReceiver();
		this.handler = new Handler(Looper.getMainLooper());
	}

	/**
	 * Returns the internal state.
	 */
	public State getState() {
		ThreadUtils.checkIsOnMainThread();
		return bluetoothState;
	}

	/**
	 * Activates components required to detect Bluetooth devices and to enable
	 * BT SCO (audio is routed via BT SCO) for the headset profile. The end
	 * state will be HEADSET_UNAVAILABLE but a state machine has started which
	 * will start a state change sequence where the final outcome depends on
	 * if/when the BT headset is enabled.
	 * Example of state change sequence when start() is called while BT device
	 * is connected and enabled:
	 * UNINITIALIZED --> HEADSET_UNAVAILABLE --> HEADSET_AVAILABLE -->
	 * SCO_CONNECTING --> SCO_CONNECTED <==> audio is now routed via BT SCO.
	 * Note that the AppRTCAudioManager is also involved in driving this state
	 * change.
	 */
	public void start() {
		ThreadUtils.checkIsOnMainThread();
		logger.debug("start");
		if (!hasPermission(apprtcContext, android.Manifest.permission.BLUETOOTH)) {
			logger.warn("Process (pid=" + Process.myPid() + ") lacks BLUETOOTH permission");
			return;
		}
		if (this.bluetoothState != State.UNINITIALIZED) {
			logger.warn("Invalid BT state");
			return;
		}
		this.bluetoothHeadset = null;
		this.bluetoothDevice = null;
		this.scoConnectionAttempts = 0;
		// Get a handle to the default local Bluetooth adapter.
		this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (this.bluetoothAdapter == null) {
			logger.warn("Device does not support Bluetooth");
			return;
		}
		// Ensure that the device supports use of BT SCO audio for off call use cases.
		if (!this.audioManager.isBluetoothScoAvailableOffCall()) {
			logger.error("Bluetooth SCO audio is not available off call");
			return;
		}
		this.logBluetoothAdapterInfo(bluetoothAdapter);
		// Establish a connection to the HEADSET profile (includes both Bluetooth Headset and
		// Hands-Free) proxy object and install a listener.
		if (!this.getBluetoothProfileProxy(
			this.apprtcContext, this.bluetoothServiceListener, BluetoothProfile.HEADSET)) {
			logger.error("BluetoothAdapter.getProfileProxy(HEADSET) failed");
			return;
		}

		// Register receivers for BluetoothHeadset change notifications.
		final IntentFilter bluetoothHeadsetFilter = new IntentFilter();
		// Register receiver for change in connection state of the Headset profile.
		bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
		// Register receiver for change in audio connection state of the Headset profile.
		bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
		registerReceiver(bluetoothHeadsetReceiver, bluetoothHeadsetFilter);

		logger.debug("HEADSET profile state: "
				+ headsetStateToString(bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)));
		logger.debug("Bluetooth proxy for headset profile has started");
		this.bluetoothState = State.HEADSET_UNAVAILABLE;
		logger.debug("start done: BT state=" + bluetoothState);
	}

	/**
	 * Stops and closes all components related to Bluetooth audio.
	 */
	public void stop() {
		ThreadUtils.checkIsOnMainThread();
		unregisterReceiver(bluetoothHeadsetReceiver);
		logger.debug("stop: BT state=" + bluetoothState);
		if (bluetoothAdapter != null) {
			// Stop BT SCO connection with remote device if needed.
			stopScoAudio();
			// Close down remaining BT resources.
			if (bluetoothState != State.UNINITIALIZED) {
				cancelTimer();
				if (bluetoothHeadset != null) {
					bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
					bluetoothHeadset = null;
				}
				bluetoothAdapter = null;
				bluetoothDevice = null;
				bluetoothState = State.UNINITIALIZED;
			}
		}
		logger.debug("stop done: BT state=" + bluetoothState);
	}

	/**
	 * Starts Bluetooth SCO connection with remote device.
	 * Note that the phone application always has the priority on the usage of the SCO connection
	 * for telephony. If this method is called while the phone is in call it will be ignored.
	 * Similarly, if a call is received or sent while an application is using the SCO connection,
	 * the connection will be lost for the application and NOT returned automatically when the call
	 * ends. Also note that: up to and including API version JELLY_BEAN_MR1, this method initiates a
	 * virtual voice call to the Bluetooth headset. After API version JELLY_BEAN_MR2 only a raw SCO
	 * audio connection is established.
	 * TODO(henrika): should we add support for virtual voice call to BT headset also for JBMR2 and
	 * higher. It might be required to initiates a virtual voice call since many devices do not
	 * accept SCO audio without a "call".
	 */
	public boolean startScoAudio() {
		ThreadUtils.checkIsOnMainThread();
		logger.debug("startSco: BT state=" + bluetoothState + ", "
				+ "attempts: " + scoConnectionAttempts + ", "
				+ "SCO is on: " + isScoOn());
		if (scoConnectionAttempts >= MAX_SCO_CONNECTION_ATTEMPTS) {
			logger.error("BT SCO connection fails - no more attempts");
			return false;
		}
		if (bluetoothState != State.HEADSET_AVAILABLE) {
			logger.error("BT SCO connection fails - no headset available");
			return false;
		}
		// Start BT SCO channel and wait for ACTION_AUDIO_STATE_CHANGED.
		logger.debug("Starting Bluetooth SCO and waits for ACTION_AUDIO_STATE_CHANGED...");
		// The SCO connection establishment can take several seconds, hence we cannot rely on the
		// connection to be available when the method returns but instead register to receive the
		// intent ACTION_SCO_AUDIO_STATE_UPDATED and wait for the state to be SCO_AUDIO_STATE_CONNECTED.
		this.bluetoothState = State.SCO_CONNECTING;
		try {
			this.audioManager.startBluetoothSco();
		} catch (RuntimeException e) {
			logger.error("Could not start bluetooth SCO", e);
		}
		this.scoConnectionAttempts++;
		this.startTimer();
		logger.debug("startScoAudio done: BT state=" + bluetoothState);
		return true;
	}

	/**
	 * Stops Bluetooth SCO connection with remote device.
	 */
	public void stopScoAudio() {
		ThreadUtils.checkIsOnMainThread();
		logger.debug("stopScoAudio: BT state=" + bluetoothState + ", "
				+ "SCO is on: " + isScoOn());
		if (bluetoothState != State.SCO_CONNECTING && bluetoothState != State.SCO_CONNECTED) {
			return;
		}
		cancelTimer();
		audioManager.stopBluetoothSco();
		bluetoothState = State.SCO_DISCONNECTING;
		logger.debug("stopScoAudio done: BT state=" + bluetoothState);
	}

	/**
	 * Use the BluetoothHeadset proxy object (controls the Bluetooth Headset
	 * Service via IPC) to update the list of connected devices for the HEADSET
	 * profile. The internal state will change to HEADSET_UNAVAILABLE or to
	 * HEADSET_AVAILABLE and |bluetoothDevice| will be mapped to the connected
	 * device if available.
	 */
	public void updateDevice() {
		if (bluetoothState == State.UNINITIALIZED || bluetoothHeadset == null) {
			return;
		}
		logger.debug("updateDevice");
		// Get connected devices for the headset profile. Returns the set of
		// devices which are in state STATE_CONNECTED. The BluetoothDevice class
		// is just a thin wrapper for a Bluetooth hardware address.
		List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
		if (devices.isEmpty()) {
			bluetoothDevice = null;
			bluetoothState = State.HEADSET_UNAVAILABLE;
			logger.debug("No connected bluetooth headset");
		} else {
			// Always use first device is list. Android only supports one device.
			bluetoothDevice = devices.get(0);
			bluetoothState = State.HEADSET_AVAILABLE;
			logger.debug("Connected bluetooth headset: "
					+ "name=" + bluetoothDevice.getName() + ", "
					+ "state=" + headsetStateToString(bluetoothHeadset.getConnectionState(bluetoothDevice))
					+ ", SCO audio=" + bluetoothHeadset.isAudioConnected(bluetoothDevice));
		}
		logger.debug("updateDevice done: BT state=" + bluetoothState);
	}

	/**
	 * Stubs for test mocks.
	 */
	protected AudioManager getAudioManager(Context context) {
		return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
	}

	protected void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
		apprtcContext.registerReceiver(receiver, filter);
	}

	protected void unregisterReceiver(BroadcastReceiver receiver) {
		try {
			apprtcContext.unregisterReceiver(receiver);
		} catch (Exception e) {
			// receiver not registered
			logger.error("Could not unregister receiver", e);
		}
	}

	protected boolean getBluetoothProfileProxy(
			Context context, BluetoothProfile.ServiceListener listener, int profile) {
		return bluetoothAdapter.getProfileProxy(context, listener, profile);
	}

	protected boolean hasPermission(Context context, String permission) {
		return apprtcContext.checkPermission(permission, Process.myPid(), Process.myUid())
				== PackageManager.PERMISSION_GRANTED;
	}

	/**
	 * Logs the state of the local Bluetooth adapter.
	 */
	@SuppressLint("HardwareIds")
	protected void logBluetoothAdapterInfo(BluetoothAdapter localAdapter) {
		try {
			logger.debug("BluetoothAdapter: "
				+ "enabled=" + localAdapter.isEnabled() + ", "
				+ "state=" + adapterStateToString(localAdapter.getState()) + ", "
				+ "name=" + localAdapter.getName() + ", "
				+ "address=" + localAdapter.getAddress());
			// Log the set of BluetoothDevice objects that are bonded (paired) to the local adapter.
			Set<BluetoothDevice> pairedDevices = localAdapter.getBondedDevices();
			if (!pairedDevices.isEmpty()) {
				logger.debug("paired devices:");
				for (BluetoothDevice device : pairedDevices) {
					logger.debug(" name=" + device.getName() + ", address=" + device.getAddress());
				}
			}
		} catch (SecurityException e) {
			// some calls on localAdapter may cause SecurityExceptions on some devices
			logger.error("BT logging failed", e);
		}
	}

	/**
	 * Ensures that the audio manager updates its list of available audio devices.
	 */
	private void updateAudioDeviceState() {
		ThreadUtils.checkIsOnMainThread();
		logger.debug("updateAudioDeviceState");
		voipAudioManager.updateAudioDeviceState();
	}

	/**
	 * Starts timer which times out after BLUETOOTH_SCO_TIMEOUT_MS milliseconds.
	 */
	private void startTimer() {
		ThreadUtils.checkIsOnMainThread();
		logger.debug("startTimer");
		handler.postDelayed(bluetoothTimeoutRunnable, BLUETOOTH_SCO_TIMEOUT_MS);
	}

	/**
	 * Cancels any outstanding timer tasks.
	 */
	private void cancelTimer() {
		ThreadUtils.checkIsOnMainThread();
		logger.debug("cancelTimer");
		handler.removeCallbacks(bluetoothTimeoutRunnable);
	}

	/**
	 * Called when start of the BT SCO channel takes too long time. Usually
	 * happens when the BT device has been turned on during an ongoing call.
	 */
	private void bluetoothTimeout() {
		ThreadUtils.checkIsOnMainThread();
		if (bluetoothState == State.UNINITIALIZED || bluetoothHeadset == null) {
			return;
		}
		logger.debug("bluetoothTimeout: BT state=" + bluetoothState + ", "
				+ "attempts: " + scoConnectionAttempts + ", "
				+ "SCO is on: " + isScoOn());
		if (bluetoothState != State.SCO_CONNECTING) {
			return;
		}
		// Bluetooth SCO should be connecting; check the latest result.
		boolean scoConnected = false;
		final List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
		if (devices.size() > 0) {
			bluetoothDevice = devices.get(0);
			if (bluetoothHeadset.isAudioConnected(bluetoothDevice)) {
				logger.debug("SCO connected with " + bluetoothDevice.getName());
				scoConnected = true;
			} else {
				logger.debug("SCO is not connected with " + bluetoothDevice.getName());
			}
		}
		if (scoConnected) {
			// We thought BT had timed out, but it's actually on; updating state.
			bluetoothState = State.SCO_CONNECTED;
			scoConnectionAttempts = 0;
		} else {
			// Give up and "cancel" our request by calling stopBluetoothSco().
			logger.warn("BT failed to connect after timeout");
			stopScoAudio();
		}
		updateAudioDeviceState();
		logger.debug("bluetoothTimeout done: BT state=" + bluetoothState);
	}

	/**
	 * Checks whether audio uses Bluetooth SCO.
	 */
	private boolean isScoOn() {
		return audioManager.isBluetoothScoOn();
	}

	/**
	 * Converts BluetoothAdapter states into local string representations.
	 */
	private String adapterStateToString(int state) {
		switch (state) {
			case BluetoothAdapter.STATE_DISCONNECTED:
				return "DISCONNECTED";
			case BluetoothAdapter.STATE_CONNECTED:
				return "CONNECTED";
			case BluetoothAdapter.STATE_CONNECTING:
				return "CONNECTING";
			case BluetoothAdapter.STATE_DISCONNECTING:
				return "DISCONNECTING";
			case BluetoothAdapter.STATE_OFF:
				return "OFF";
			case BluetoothAdapter.STATE_ON:
				return "ON";
			case BluetoothAdapter.STATE_TURNING_OFF:
				// Indicates the local Bluetooth adapter is turning off. Local clients should immediately
				// attempt graceful disconnection of any remote links.
				return "TURNING_OFF";
			case BluetoothAdapter.STATE_TURNING_ON:
				// Indicates the local Bluetooth adapter is turning on. However local clients should wait
				// for STATE_ON before attempting to use the adapter.
				return "TURNING_ON";
			default:
				return "INVALID";
		}
	}

	/**
	 * Converts BluetoothHeadset states into local string representations.
	 */
	private String headsetStateToString(int state) {
		switch (state) {
			case BluetoothHeadset.STATE_CONNECTING:
				return "CONNECTING";
			case BluetoothHeadset.STATE_AUDIO_CONNECTING:
				return "A_CONNECTING";
			case BluetoothHeadset.STATE_CONNECTED:
				return "CONNECTED";
			case BluetoothHeadset.STATE_AUDIO_CONNECTED:
				return "A_CONNECTED";
			case BluetoothHeadset.STATE_DISCONNECTING:
				return "DISCONNECTING";
			case BluetoothHeadset.STATE_DISCONNECTED:
				return "DISCONNECTED";
			case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
				return "A_DISCONNECTED";
			default:
				return "INVALID_STATE";
		}
	}
}

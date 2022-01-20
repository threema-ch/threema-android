/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code (the original WebRTC parts) is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package ch.threema.app.voip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;

import org.slf4j.Logger;
import org.webrtc.ThreadUtils;

import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.notifications.BackgroundErrorNotification;
import ch.threema.app.voip.listeners.VoipAudioManagerListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.voip.util.AppRTCUtils;
import ch.threema.base.utils.LoggingUtil;
import java8.util.concurrent.CompletableFuture;

/**
 * VoipAudioManager manages all audio related parts of the Threema VoIP calls.
 */
public class VoipAudioManager {
	private static final Logger logger = LoggingUtil.getThreemaLogger("VoipAudioManager");
	private static final String TAG = "VoipAudioManager";

	/**
	 * AudioDevice is the names of possible audio devices that we currently
	 * support.
	 */
	public enum AudioDevice {
		SPEAKER_PHONE,
		WIRED_HEADSET,
		EARPIECE,
		BLUETOOTH,
		NONE,
	}

	/**
	 * AudioManager state.
	 */
	public enum AudioManagerState {
		UNINITIALIZED,
		PREINITIALIZED,
		RUNNING,
	}

	private final Context apprtcContext;
	private CompletableFuture<AudioManager> audioManagerFuture;
	private AudioManager audioManager;

	private AudioManagerState amState;
	private int savedAudioMode = AudioManager.MODE_INVALID;
	private boolean savedIsSpeakerPhoneOn = false;
	private boolean savedIsMicrophoneMute = false;
	private boolean hasWiredHeadset = false;
	private boolean micEnabled = true;

	// Default audio device; speaker phone for devices without telephony features
	// (e.g. tablets) or earpiece for devices with telephony features.
	private AudioDevice defaultAudioDevice;

	// Contains the currently selected audio device.
	// This device is changed automatically using a certain scheme where e.g.
	// a wired headset "wins" over speaker phone. It is also possible for a
	// user to explicitly select a device (and overrid any predefined scheme).
	// See |userSelectedAudioDevice| for details.
	@Nullable private AudioDevice selectedAudioDevice;

	// Contains the user-selected audio device which overrides the predefined
	// selection scheme.
	private AudioDevice userSelectedAudioDevice;

	// Handles all tasks related to Bluetooth headset devices.
	private final VoipBluetoothManager bluetoothManager;

	// Contains a list of available audio devices. A Set collection is used to
	// avoid duplicate elements.
	@NonNull private HashSet<AudioDevice> audioDevices = new HashSet<>();

	// Broadcast receiver for wired headset intent broadcasts.
	private BroadcastReceiver wiredHeadsetReceiver;

	// Callback method for changes in audio focus.
	private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

	/* Receiver which handles changes in wired headset availability. */
	private class WiredHeadsetReceiver extends BroadcastReceiver {
		private static final int STATE_UNPLUGGED = 0;
		private static final int STATE_PLUGGED = 1;
		private static final int HAS_NO_MIC = 0;
		private static final int HAS_MIC = 1;

		@Override
		public void onReceive(Context context, Intent intent) {
			int state = intent.getIntExtra("state", STATE_UNPLUGGED);
			int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
			String name = intent.getStringExtra("name");
			logger.debug("WiredHeadsetReceiver.onReceive" + AppRTCUtils.getThreadInfo() + ": "
					+ "a=" + intent.getAction() + ", s="
					+ (state == STATE_UNPLUGGED ? "unplugged" : "plugged") + ", m="
					+ (microphone == HAS_MIC ? "mic" : "no mic") + ", n=" + name + ", sb="
					+ isInitialStickyBroadcast());
			hasWiredHeadset = (state == STATE_PLUGGED);
			updateAudioDeviceState();
		}
	}

	public static VoipAudioManager create(Context context, CompletableFuture<Void> audioFocusAbandonedFuture) {
		return new VoipAudioManager(context, audioFocusAbandonedFuture);
	}

	private VoipAudioManager(Context context, CompletableFuture<Void> audioFocusAbandonedFuture) {
		logger.info("Initializing");
		ThreadUtils.checkIsOnMainThread();
		this.apprtcContext = context;
		this.audioManagerFuture = audioFocusAbandonedFuture
			.thenApply(x -> {
				return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			});
		this.bluetoothManager = VoipBluetoothManager.create(context, this);
		this.wiredHeadsetReceiver = new WiredHeadsetReceiver();
		this.amState = AudioManagerState.UNINITIALIZED;

		// Set default audio device
		if (this.hasEarpiece()) {
			this.defaultAudioDevice = AudioDevice.EARPIECE;
		} else {
			this.defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
		}

		logger.info("defaultAudioDevice: {}", defaultAudioDevice);
		AppRTCUtils.logDeviceInfo(TAG);
	}

	public void start() {
		logger.debug("start");
		ThreadUtils.checkIsOnMainThread();
		if (amState == AudioManagerState.RUNNING) {
			logger.error("AudioManager is already active");
			return;
		}

		logger.debug("AudioManager starts...");
		amState = AudioManagerState.RUNNING;

		// Store current audio state so we can restore it when stop() is called.
		try {
			audioManager = audioManagerFuture.get();
		} catch (InterruptedException e) {
			logger.error("AudioManager Future error", e);
			BackgroundErrorNotification.showNotification(ThreemaApplication.getAppContext(), "AudioManager initialization error", "AudioManager Future failed", TAG, true, e);
			// Restore interrupted state...
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			logger.error("AudioManager Future error", e);
			BackgroundErrorNotification.showNotification(ThreemaApplication.getAppContext(), "AudioManager initialization error", "AudioManager Future failed", TAG, true, e);
		}

		savedAudioMode = audioManager.getMode();
		savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
		savedIsMicrophoneMute = audioManager.isMicrophoneMute();
		hasWiredHeadset = hasWiredHeadset();

		// Create an AudioManager.OnAudioFocusChangeListener instance.
		audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
			// Called on the listener to notify if the audio focus for this listener has been changed.
			// The |focusChange| value indicates whether the focus was gained, whether the focus was lost,
			// and whether that loss is transient, or whether the new focus holder will hold it for an
			// unknown amount of time.
			@Override
			public void onAudioFocusChange(int focusChange) {
				String typeOfChange;
				switch (focusChange) {
					case AudioManager.AUDIOFOCUS_GAIN:
						logger.info("Audio Focus gain");

						typeOfChange = "AUDIOFOCUS_GAIN";
						VoipListenerManager.audioManagerListener.handle(new ListenerManager.HandleListener<VoipAudioManagerListener>() {
							@Override
							public void handle(VoipAudioManagerListener listener) {
								listener.onAudioFocusGained();
							}
						});
						break;
					case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
						logger.info("Audio Focus gain transient");

						typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT";
						VoipListenerManager.audioManagerListener.handle(new ListenerManager.HandleListener<VoipAudioManagerListener>() {
							@Override
							public void handle(VoipAudioManagerListener listener) {
								listener.onAudioFocusGained();
							}
						});
						break;
					case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
						logger.info("Audio Focus gain transient exclusive");

						typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE";
						VoipListenerManager.audioManagerListener.handle(new ListenerManager.HandleListener<VoipAudioManagerListener>() {
							@Override
							public void handle(VoipAudioManagerListener listener) {
								listener.onAudioFocusGained();
							}
						});
						break;
					case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
						typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK";
						break;
					case AudioManager.AUDIOFOCUS_LOSS:
						typeOfChange = "AUDIOFOCUS_LOSS";
						logger.info("Audio Focus loss");

						/* ignore complete loss as this will break the use case of watching videos during a call */
						/* TODO: Currently disabled because of side effects
						VoipListenerManager.audioManagerListener.handle(new ListenerManager.HandleListener<VoipAudioManagerListener>() {
							@Override
							public void handle(VoipAudioManagerListener listener) {
								listener.onAudioFocusLost(false);
							}
						});
						*/
						break;
					case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
						logger.info("Audio Focus loss transient");

						typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT";
						/* if we lose the audio focus temporarily (e.g. if a Signal call comes in), we mute the call */
						/* TODO: Currently disabled because of side effects
						VoipListenerManager.audioManagerListener.handle(new ListenerManager.HandleListener<VoipAudioManagerListener>() {
							@Override
							public void handle(VoipAudioManagerListener listener) {
								listener.onAudioFocusLost(true);
							}
						});
						 */
						break;
					case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
						logger.info("Audio Focus loss transient can duck");

						typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
						// we continue in case of ducking
						break;
					default:
						typeOfChange = "AUDIOFOCUS_INVALID";
						break;
				}
				logger.debug("onAudioFocusChange: " + typeOfChange);
			}
		};

		// Request audio playout focus (without ducking) and install listener for changes in focus.
		final int result = audioManager.requestAudioFocus(
				audioFocusChangeListener,
				AudioManager.STREAM_VOICE_CALL,
				AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
		);
		if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			logger.info("Audio focus request granted for VOICE_CALL streams");
		} else {
			logger.info("Audio focus request for VOICE_CALL failed");
		}

		// Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
		// required to be in this mode when playout and/or recording starts for
		// best possible VoIP performance.
		this.audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

		// Always disable microphone mute during a WebRTC call.
		this.setMicrophoneMute(false);

		// Set initial device states.
		this.userSelectedAudioDevice = AudioDevice.NONE;
		this.selectedAudioDevice = AudioDevice.NONE;
		this.audioDevices.clear();

		// Initialize and start Bluetooth if a BT device is available or initiate
		// detection of new (enabled) BT devices.
		this.bluetoothManager.start();

		// Do initial selection of audio device. This setting can later be changed
		// either by adding/removing a BT or wired headset or by covering/uncovering
		// the proximity sensor.
		this.updateAudioDeviceState();

		// Register receiver for broadcast intents related to adding/removing a wired headset.
		this.registerReceiver(this.wiredHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
		logger.debug("AudioManager started");
	}

	public void stop() {
		logger.debug("stop");
		ThreadUtils.checkIsOnMainThread();
		if (this.amState != AudioManagerState.RUNNING) {
			logger.error("Trying to stop AudioManager in incorrect state: " + amState);
			return;
		}
		this.amState = AudioManagerState.UNINITIALIZED;

		this.unregisterReceiver(this.wiredHeadsetReceiver);

		this.bluetoothManager.stop();

		// Restore previously stored audio states.
		this.setSpeakerphoneOn(this.savedIsSpeakerPhoneOn);
		this.setMicrophoneMute(this.savedIsMicrophoneMute);
		this.audioManager.setMode(this.savedAudioMode);

		// Abandon audio focus. Gives the previous focus owner, if any, focus.
		this.audioManager.abandonAudioFocus(this.audioFocusChangeListener);
		this.audioFocusChangeListener = null;
		logger.info("Abandoned audio focus for VOICE_CALL streams");
		logger.info("Stopped");
	}

	/**
	 * Changes selection of the currently active audio device.
	 */
	private void setAudioDeviceInternal(AudioDevice device) {
		logger.info("Changing audio device to {}", device);
		if (!audioDevices.contains(device)) {
			logger.error("Trying to call setAudioDeviceInternal with an invalid device:");
			logger.error("{} is not contained in {}", device, audioDevices);
			return;
		}

		switch (device) {
			case SPEAKER_PHONE:
				setSpeakerphoneOn(true);
				break;
			case EARPIECE:
				setSpeakerphoneOn(false);
				break;
			case WIRED_HEADSET:
				setSpeakerphoneOn(false);
				break;
			case BLUETOOTH:
				setSpeakerphoneOn(false);
				break;
			case NONE:
			default:
				logger.error("Invalid audio device selection");
				break;
		}
		selectedAudioDevice = device;
	}

	/**
	 * Changes selection of the currently active audio device.
	 */
	public void selectAudioDevice(AudioDevice device) {
		ThreadUtils.checkIsOnMainThread();
		if (!this.audioDevices.contains(device)) {
			logger.error("Can not select " + device + " from available " + this.audioDevices);
		}
		this.userSelectedAudioDevice = device;
		this.updateAudioDeviceState();
	}

	public void setMicEnabled(boolean micEnabled) {
		if (this.micEnabled != micEnabled) {
			this.micEnabled = micEnabled;
			// Notify listeners
			VoipListenerManager.audioManagerListener.handle(new ListenerManager.HandleListener<VoipAudioManagerListener>() {
				@Override
				public void handle(VoipAudioManagerListener listener) {
					listener.onMicEnabledChanged(micEnabled);
				}
			});
		}
	}

	/**
	 * Notify listeners of the VoipAudioManagerListener.
	 */
	public void requestAudioManagerNotify() {
		VoipListenerManager.audioManagerListener.handle(new ListenerManager.HandleListener<VoipAudioManagerListener>() {
			@Override
			public void handle(VoipAudioManagerListener listener) {
				listener.onAudioDeviceChanged(selectedAudioDevice, audioDevices);
			}
		});
	}

	public void requestMicEnabledNotify() {
		VoipListenerManager.audioManagerListener.handle(new ListenerManager.HandleListener<VoipAudioManagerListener>() {
			@Override
			public void handle(VoipAudioManagerListener listener) {
				listener.onMicEnabledChanged(micEnabled);
			}
		});
	}

	/**
	 * Return whether the specified device is available.
	 */
	public boolean hasAudioDevice(AudioDevice device) {
		return this.audioDevices.contains(device);
	}

	/**
	 * Helper method for receiver registration.
	 */
	private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
		apprtcContext.registerReceiver(receiver, filter);
	}

	/**
	 * Helper method for unregistration of an existing receiver.
	 */
	private void unregisterReceiver(BroadcastReceiver receiver) {
		apprtcContext.unregisterReceiver(receiver);
	}

	/**
	 * Sets the speaker phone mode.
	 */
	private void setSpeakerphoneOn(boolean on) {
		boolean wasOn = audioManager.isSpeakerphoneOn();
		if (wasOn == on) {
			return;
		}
		audioManager.setSpeakerphoneOn(on);
	}

	/**
	 * Sets the microphone mute state.
	 */
	private void setMicrophoneMute(boolean on) {
		boolean wasMuted = audioManager.isMicrophoneMute();
		if (wasMuted == on) {
			return;
		}
		logger.info("{} microphone", on ? "Mute" : "Unmute");
		audioManager.setMicrophoneMute(on);
	}

	/**
	 * Checks whether the device has an earpiece.
	 * This should be the case if the telephony feature is available.
	 */
	private boolean hasEarpiece() {
		return apprtcContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
	}

	/**
	 * Checks whether a wired headset is connected or not.
	 * This is not a valid indication that audio playback is actually over
	 * the wired headset as audio routing depends on other conditions. We
	 * only use it as an early indicator (during initialization) of an attached
	 * wired headset.
	 */
	@Deprecated
	private boolean hasWiredHeadset() {
		return audioManager.isWiredHeadsetOn();
	}

	/**
	 * Updates list of possible audio devices and make new device selection.
	 */
	public synchronized void updateAudioDeviceState() {
		ThreadUtils.checkIsOnMainThread();
		logger.debug(
			"Updating audio device state, initial state: wired_headset={}, bt_state={}, available={}, selected={}, user_selected={}",
			this.hasWiredHeadset,
			this.bluetoothManager.getState(),
			this.audioDevices,
			this.selectedAudioDevice,
			this.userSelectedAudioDevice
		);

		// Query for available audio devices
		final HashSet<AudioDevice> newAudioDevices = this.queryAvailableAudioDevices();

		// Store state which is set to true if the device list has changed.
		boolean audioDeviceSetUpdated = !this.audioDevices.equals(newAudioDevices);

		// Update the existing audio device set.
		this.audioDevices = newAudioDevices;

		// Correct user selected audio devices if needed
		this.validateUserSelection();

		// Start bluetooth stack if necessary
		audioDeviceSetUpdated = this.initBluetooth(audioDeviceSetUpdated);

		// Update selected audio device.
		AudioDevice newAudioDevice;
		if (this.bluetoothManager.getState() == VoipBluetoothManager.State.SCO_CONNECTED) {
			// If bluetooth connection is active, switch over to it
			newAudioDevice = AudioDevice.BLUETOOTH;
		} else if (this.hasWiredHeadset) {
			// If there's a wired headset, set this as default device
			newAudioDevice = AudioDevice.WIRED_HEADSET;
		} else {
			// Otherwise, use the default device, which should be either speakerphone or earpiece,
			// depending on device configuration.
			newAudioDevice = this.defaultAudioDevice;
		}
		switch (this.userSelectedAudioDevice) {
			case BLUETOOTH:
				if (bluetoothManager.getState() == VoipBluetoothManager.State.SCO_CONNECTED) {
					// If a Bluetooth device is connected, then it should be used as output audio
					// device. Note that it is not sufficient that a headset is available;
					// an active SCO channel must also be up and running.
					newAudioDevice = AudioDevice.BLUETOOTH;
				}
				break;
			case EARPIECE:
			case SPEAKER_PHONE:
			case WIRED_HEADSET:
				newAudioDevice = this.userSelectedAudioDevice;
				break;
			case NONE:
				break;
			default:
				logger.error(": Invalid user selected audio device: " + this.userSelectedAudioDevice);
		}

		// Switch to new device but only if there has been any changes.
		if (newAudioDevice != this.selectedAudioDevice || audioDeviceSetUpdated) {
			// Do the required device switch.
			this.setAudioDeviceInternal(newAudioDevice);
			logger.info("New device status: available={}, selected={}", this.audioDevices, newAudioDevice);

			// Notify listeners
			VoipListenerManager.audioManagerListener.handle(new ListenerManager.HandleListener<VoipAudioManagerListener>() {
				@Override
				public void handle(VoipAudioManagerListener listener) {
					listener.onAudioDeviceChanged(selectedAudioDevice, audioDevices);
				}
			});
		}
		logger.debug("Done updating audio device state");
	}

	private boolean initBluetooth(boolean audioDeviceSetUpdated) {
		// Need to start Bluetooth if it is available and user either selected it explicitly or
		// user did not select any output device.
		boolean needBluetoothAudioStart =
				this.bluetoothManager.getState() == VoipBluetoothManager.State.HEADSET_AVAILABLE
				&& (this.userSelectedAudioDevice == AudioDevice.NONE
					|| this.userSelectedAudioDevice == AudioDevice.BLUETOOTH);

		// Need to stop Bluetooth audio if user selected different device and
		// Bluetooth SCO connection is established or in the process.
		boolean needBluetoothAudioStop =
				(this.bluetoothManager.getState() == VoipBluetoothManager.State.SCO_CONNECTED
					|| this.bluetoothManager.getState() == VoipBluetoothManager.State.SCO_CONNECTING)
					&& (this.userSelectedAudioDevice != AudioDevice.NONE
						&& this.userSelectedAudioDevice != AudioDevice.BLUETOOTH);

		if (this.audioDevices.contains(AudioDevice.BLUETOOTH)) {
			logger.debug("Need BT audio: start=" + needBluetoothAudioStart + ", "
					 + "stop=" + needBluetoothAudioStop + ", "
					 + "BT state=" + this.bluetoothManager.getState());
		}

		// Start or stop Bluetooth SCO connection given states set earlier.
		if (needBluetoothAudioStop) {
			this.bluetoothManager.stopScoAudio();
			this.bluetoothManager.updateDevice();
		}
		if (needBluetoothAudioStart && !needBluetoothAudioStop) {
			// Attempt to start Bluetooth SCO audio (takes a few second to start).
			if (!this.bluetoothManager.startScoAudio()) {
				// Remove BLUETOOTH from list of available devices since SCO failed.
				this.audioDevices.remove(AudioDevice.BLUETOOTH);
				audioDeviceSetUpdated = true;
			}
		}
		return audioDeviceSetUpdated;
	}

	/**
	 * Return the set of available audio devices.
	 */
	@NonNull
	private HashSet<AudioDevice> queryAvailableAudioDevices() {
		final HashSet<AudioDevice> devices = new HashSet<>();

		// Check if any Bluetooth headset is connected. The internal BT state will
		// change accordingly.
		// TODO(henrika): perhaps wrap required state into BT manager.
		switch (bluetoothManager.getState()) {
			case HEADSET_AVAILABLE:
			case HEADSET_UNAVAILABLE:
			case SCO_DISCONNECTING:
				bluetoothManager.updateDevice();
				break;
			default:
				// Ignore
		}

		// Check for a bluetooth device
		switch (bluetoothManager.getState()) {
			case SCO_CONNECTED:
			case SCO_CONNECTING:
			case HEADSET_AVAILABLE:
				devices.add(AudioDevice.BLUETOOTH);
				break;
			default:
				// Ignore
		}

		if (this.hasWiredHeadset) {
			// If a wired headset is connected, then it is the only possible option.
			devices.add(AudioDevice.WIRED_HEADSET);
		} else {
			// No wired headset, hence the audio-device list can contain speaker
			// phone (on a tablet), or speaker phone and earpiece (on mobile phone).
			devices.add(AudioDevice.SPEAKER_PHONE);
			if (this.hasEarpiece()) {
				devices.add(AudioDevice.EARPIECE);
			}
		}

		return devices;
	}

	/**
	 * Validate and fix the user selected audio device.
	 * For example if the user selected bluetooth but there is no bluetooth device available,
	 * reset the selection to NONE.
	 */
	private void validateUserSelection() {
		if (this.bluetoothManager.getState() == VoipBluetoothManager.State.HEADSET_UNAVAILABLE
				&& this.userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
			// If BT is not available, it can't be the user selection.
			this.userSelectedAudioDevice = AudioDevice.NONE;
		}

		if (this.hasWiredHeadset && this.userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE) {
			// If user selected speaker phone, but then plugged wired headset, make
			// wired headset as user selected device.
			this.userSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
		}
		if (this.hasWiredHeadset && this.userSelectedAudioDevice == AudioDevice.EARPIECE) {
			// If user selected earpiece, but then plugged wired headset, make
			// wired headset as user selected device (since most devices cannot
			// output audio to both outputs at once).
			this.userSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
		}

		if (!this.hasWiredHeadset && this.userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
			// If user selected wired headset, but then unplugged wired headset,
			// unset the user selection so that the default device will be picked again.
			this.userSelectedAudioDevice = AudioDevice.NONE;
		}
	}
}

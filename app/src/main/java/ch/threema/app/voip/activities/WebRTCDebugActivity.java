/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.voip.activities;

import static ch.threema.app.preference.SettingsAdvancedOptionsFragment.THREEMA_SUPPORT_IDENTITY;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.slf4j.Logger;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.WebRTCUtil;
import ch.threema.app.voip.PeerConnectionClient;
import ch.threema.app.voip.util.SdpPatcher;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.protobuf.callsignaling.O2OCall;
import ch.threema.storage.models.ContactModel;

/**
 * An activity to debug problems with WebRTC (in the context of Threema Calls).
 */
public class WebRTCDebugActivity extends ThreemaToolbarActivity implements PeerConnectionClient.Events, TextEntryDialog.TextEntryDialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("WebRTCDebugActivity");
	private static final String DIALOG_TAG_SEND_WEBRTC_DEBUG = "swd";

	// Threema services
	@NonNull private MessageService messageService;
	@NonNull private ContactService contactService;

	// Views
	@NonNull private CircularProgressIndicator progressBar;
	@NonNull private TextView introText;
	@NonNull private TextView doneText;
	@Nullable private Button copyButton;
	@Nullable private Button sendButton;
	@Nullable private View footerButtons;

	@Nullable private PeerConnectionClient peerConnectionClient;
	@NonNull private final List<String> eventLog = new ArrayList<>();
	@NonNull private ArrayAdapter adapter;
	private boolean gatheringComplete = false;
	private String clipboardString;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		logger.trace("onCreate");
		super.onCreate(savedInstanceState);

		// Get services
		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			logger.error("Could not obtain service manager");
			finish();
			return;
		}
		try {
			this.messageService = serviceManager.getMessageService();
		} catch (Exception e) {
			logger.error("Could not obtain message service", e);
			finish();
			return;
		}
		try {
			this.contactService = serviceManager.getContactService();
		} catch (Exception e) {
			logger.error("Could not obtain contact service", e);
			finish();
			return;
		}

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.voip_webrtc_debug);
		}

		// Get view references
		this.progressBar = findViewById(R.id.webrtc_debug_loading);
		this.introText = findViewById(R.id.webrtc_debug_intro);
		this.doneText = findViewById(R.id.webrtc_debug_done);
		this.copyButton = findViewById(R.id.webrtc_debug_copy_button);
		this.sendButton = findViewById(R.id.webrtc_debug_send_button);
		this.footerButtons = findViewById(R.id.webrtc_debug_footer_buttons);

		// Wire up start button
		final Button startButton = findViewById(R.id.webrtc_debug_start);
		startButton.setOnClickListener(view -> {
			startButton.setVisibility(View.GONE);
			WebRTCDebugActivity.this.startGathering();
		});

		// Wire up copy button
		assert this.copyButton != null;
		this.copyButton.setOnClickListener(view -> {
			if (!TestUtil.empty(this.clipboardString)) {
				this.copyToClipboard(this.clipboardString);
			}
		});

		// Wire up send button
		assert this.sendButton != null;
		this.sendButton.setOnClickListener(view -> {
			if (!TestUtil.empty(this.clipboardString)) {
				this.prepareSendToSupport();
			}
		});

		// Initialize list of candidates
		final ListView candidatesList = findViewById(R.id.webrtc_debug_candidates);
		this.adapter = new ArrayAdapter<>(this,
			R.layout.item_webrtc_debug_list, this.eventLog);
		candidatesList.setAdapter(this.adapter);
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_webrtc_debug;
	}

	@Override
	protected void onStart() {
		logger.trace("onStart");
		super.onStart();
	}

	@Override
	protected void onStop() {
		logger.trace("onStop");

		logger.info("*** Finished WebRTC Debugging Test");
		this.cleanup();

		super.onStop();
	}

	@UiThread
	private void startGathering() {
		logger.info("*** Starting WebRTC Debugging Test ***");
		logger.info("Setting up peer connection");
		this.eventLog.clear();
		this.clipboardString = "";
		this.addToLog("Starting Call Diagnostics...");
		this.addToLog("----------------");
		this.addToLog("Device info: " + ConfigUtils.getDeviceInfo(this, false));
		this.addToLog("App version: " + ConfigUtils.getAppVersion(this));
		this.addToLog("App language: " + LocaleUtil.getAppLanguage());
		this.addToLog("----------------");

		// Show settings
		this.addToLog("Enabled: calls=" + preferenceService.isVoipEnabled() + " video=" + preferenceService.isVideoCallsEnabled());
		this.addToLog("Settings: aec=" + preferenceService.getAECMode() +
			" video_codec=" + preferenceService.getVideoCodec() +
			" video_profile=" + preferenceService.getVideoCallsProfile() +
			" force_turn=" + preferenceService.getForceTURN());
		this.addToLog("----------------");

		// Update UI visibility
		this.progressBar.setVisibility(View.VISIBLE);
		this.introText.setVisibility(View.GONE);
		this.doneText.setVisibility(View.GONE);
		this.footerButtons.setVisibility(View.GONE);

		// Initialize peer connection client
		final boolean useHardwareEC = true;
		final boolean useHardwareNC = true;
		final boolean videoCallEnabled = true;
		final boolean useVideoHwAcceleration = true;
		final boolean videoCodecEnableVP8 = true;
		final boolean videoCodecEnableH264HiP = true;
		final SdpPatcher.RtpHeaderExtensionConfig rtpHeaderExtensionConfig =
			SdpPatcher.RtpHeaderExtensionConfig.ENABLE_WITH_ONE_AND_TWO_BYTE_HEADER;
		final boolean forceTurn = false;
		final boolean gatherContinually = false;
		final boolean allowIpv6 = true;
		final PeerConnectionClient.PeerConnectionParameters peerConnectionParameters = new PeerConnectionClient.PeerConnectionParameters(
				false,
				useHardwareEC, useHardwareNC,
				videoCallEnabled, useVideoHwAcceleration, videoCodecEnableVP8, videoCodecEnableH264HiP,
				rtpHeaderExtensionConfig,
				forceTurn, gatherContinually, allowIpv6
		);
		this.peerConnectionClient = new PeerConnectionClient(
				getApplicationContext(),
				peerConnectionParameters,
				null,
				2
		);
		this.peerConnectionClient.setEventHandler(this);

		// Create peer connection factory
		boolean factoryCreated = false;
		Throwable factoryCreateException = null;
		try {
			factoryCreated = peerConnectionClient
				.createPeerConnectionFactory()
				.get(10, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			factoryCreateException = e;
			logger.error("Could not create peer connection factory", e);
		}
		if (!factoryCreated) {
			WebRTCDebugActivity.this.addToLog("Could not create peer connection factory");
			if (factoryCreateException != null) {
				WebRTCDebugActivity.this.addToLog(factoryCreateException.getMessage());
			}
			WebRTCDebugActivity.this.onComplete();
			return;
		}

		// Create peer connection
		peerConnectionClient.createPeerConnection();

		// Create offer to trigger ICE collection
		this.addToLog("ICE Candidates found:");
		peerConnectionClient.createOffer();

		// Schedule gathering timeout
		final Handler handler = new Handler();
		handler.postDelayed(() -> {
			if (!gatheringComplete) {
				logger.info("Timeout");
				WebRTCDebugActivity.this.addToLog("Timed out");
				WebRTCDebugActivity.this.onComplete();
			}
		}, 20000);
	}

	@AnyThread
	private void addToLog(final String value) {
		clipboardString += value + "\n";

		RuntimeUtil.runOnUiThread(() -> {
			synchronized (WebRTCDebugActivity.this.eventLog) {
				WebRTCDebugActivity.this.eventLog.add(value);
				WebRTCDebugActivity.this.adapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	@AnyThread
	public void onLocalDescription(long callId, SessionDescription sdp) {
		logger.info("onLocalDescription: {}", sdp);
	}

	@Override
	@AnyThread
	public void onRemoteDescriptionSet(long callId) {
		logger.info("onRemoteDescriptionSet");
	}

	@Override
	@AnyThread
	public void onIceCandidate(long callId, IceCandidate candidate) {
		logger.info("onIceCandidate: {}", candidate);
		this.addToLog(WebRTCUtil.iceCandidateToString(candidate));
	}

	@Override
	public void onTransportConnecting(long callId) {
		logger.info("onTransportConnecting");
		this.addToLog("Transport connecting");
	}

	@Override
	@AnyThread
	public void onTransportConnected(long callId) {
		logger.info("onTransportConnected");
		this.addToLog("Transport connected");
	}

	@Override
	@AnyThread
	public void onTransportDisconnected(long callId) {
		logger.info("onTransportDisconnected");
		this.addToLog("Transport disconnected");
	}

	@Override
	@AnyThread
	public void onTransportFailed(long callId) {
		logger.info("onTransportFailed");
		this.addToLog("Transport failed");
	}

	@Override
	public void onIceGatheringStateChange(long callId, PeerConnection.IceGatheringState newState) {
		logger.info("onIceGatheringStateChange: {}", newState);
		if (newState == PeerConnection.IceGatheringState.COMPLETE && !this.gatheringComplete) {
			// We're done.
			this.addToLog("----------------");
			this.addToLog("Done!");
			this.onComplete();
		}
	}

	@Override
	@AnyThread
	public void onPeerConnectionClosed(long callId) {
		logger.info("onPeerConnectionClosed");
		this.addToLog("PeerConnection closed");
	}

	@Override
	@AnyThread
	public void onError(long callId, final @NonNull String description, boolean abortCall) {
		final String msg = String.format("%s (abortCall: %s)", description, abortCall);
		logger.info("onError: " + msg);
		this.addToLog("Error: " + msg);
	}

	@Override
	public void onSignalingMessage(long callId, @NonNull O2OCall.Envelope envelope) {
		logger.info("onSignalingMessage: {}", envelope);
	}

	/**
	 * Test is complete.
	 */
	@AnyThread
	private void onComplete() {
		this.gatheringComplete = true;

		RuntimeUtil.runOnUiThread(() -> {
			progressBar.setVisibility(View.GONE);
			introText.setVisibility(View.GONE);
			doneText.setVisibility(View.VISIBLE);
			footerButtons.setVisibility(View.VISIBLE);
		});
	}

	/**
	 * Copy the specified string to the system clipboard.
	 */
	private void copyToClipboard(@NonNull String clipboardString) {
		final ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		final ClipData clip = ClipData.newPlainText(getString(R.string.voip_webrtc_debug), clipboardString);
		clipboard.setPrimaryClip(clip);
		Toast.makeText(getApplicationContext(), getString(R.string.voip_webrtc_debug_copied), Toast.LENGTH_LONG).show();
	}

	/**
	 * Show a dialog to confirm that the information should be sent to the support.
	 */
	private void prepareSendToSupport() {
		TextEntryDialog dialog = TextEntryDialog.newInstance(R.string.send_to_support,
			R.string.enter_description,
			R.string.send,
			R.string.cancel,
			5,
			3000,
			1);
		dialog.show(getSupportFragmentManager(), DIALOG_TAG_SEND_WEBRTC_DEBUG);
	}

	@Override
	public void onYes(String tag, String text) {
		if (DIALOG_TAG_SEND_WEBRTC_DEBUG.equals(tag)) {
			// User confirmed that log should be sent to support
			sendToSupport(text);
		}
	}

	@Override
	public void onNo(String tag) { /* Unused */ }

	@Override
	public void onNeutral(String tag) { /* Unused */ }

	@SuppressLint("StaticFieldLeak")
	private void sendToSupport(@NonNull String caption) {
		if (this.contactService == null || messageService == null) {
			logger.error("Cannot send to support, some services are null");
			return;
		}

		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... voids) {
				try {
					final ContactModel contactModel = contactService.getOrCreateByIdentity(THREEMA_SUPPORT_IDENTITY, true);
					final ContactMessageReceiver messageReceiver = contactService.createReceiver(contactModel);

					messageService.sendText(clipboardString +
						"\n---\n" +
						caption +
						"\n---\n" +
						ConfigUtils.getSupportDeviceInfo(WebRTCDebugActivity.this) + "\n" +
						"Threema " + ConfigUtils.getAppVersion(WebRTCDebugActivity.this) + "\n" +
						getMyIdentity(), messageReceiver);

					return true;
				} catch (Exception e) {
					logger.error("Exception while sending information to support", e);
					return false;
				}
			}

			@Override
			protected void onPostExecute(Boolean success) {
				Toast.makeText(
					getApplicationContext(),
					Boolean.TRUE.equals(success) ? R.string.message_sent : R.string.an_error_occurred,
					Toast.LENGTH_LONG
				).show();
				finish();
			}
		}.execute();
	}

	@AnyThread
	private synchronized void cleanup() {
		if (this.peerConnectionClient != null) {
			this.peerConnectionClient.close();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
}

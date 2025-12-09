/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.asynctasks.SendToSupportBackgroundTask;
import ch.threema.app.asynctasks.SendToSupportResult;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.WebRTCUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.app.voip.PeerConnectionClient;
import ch.threema.app.voip.util.SdpPatcher;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.data.models.ContactModel;
import ch.threema.protobuf.callsignaling.O2OCall;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

/**
 * An activity to debug problems with WebRTC (in the context of Threema Calls).
 */
public class WebRTCDebugActivity extends ThreemaToolbarActivity implements PeerConnectionClient.Events, TextEntryDialog.TextEntryDialogClickListener {
    private static final Logger logger = getThreemaLogger("WebRTCDebugActivity");
    private static final String DIALOG_TAG_SEND_WEBRTC_DEBUG = "swd";

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    @Nullable
    private BackgroundExecutor backgroundExecutor;

    // Views
    @NonNull
    private CircularProgressIndicator progressBar;
    @NonNull
    private TextView introText;
    @NonNull
    private TextView doneText;
    @Nullable
    private Button copyButton;
    @Nullable
    private Button sendButton;
    @Nullable
    private View footerButtons;

    @Nullable
    private PeerConnectionClient peerConnectionClient;
    @NonNull
    private final List<String> eventLog = new ArrayList<>();
    @NonNull
    private ArrayAdapter adapter;
    private boolean gatheringComplete = false;
    private String clipboardString;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        logger.trace("onCreate");
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!isSessionScopeReady()) {
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
            if (!TestUtil.isEmptyOrNull(this.clipboardString)) {
                this.copyToClipboard(this.clipboardString);
            }
        });

        // Wire up send button
        assert this.sendButton != null;
        this.sendButton.setOnClickListener(view -> {
            if (!TestUtil.isEmptyOrNull(this.clipboardString)) {
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
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.content_container),
            InsetSides.lbr()
        );
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
        this.addToLog("Device info: " + ConfigUtils.getDeviceInfo(false));
        this.addToLog("App version: " + ConfigUtils.getAppVersion());
        this.addToLog("App language: " + LocaleUtil.getAppLanguage());
        this.addToLog("----------------");

        // Show settings
        var preferenceService = dependencies.getPreferenceService();
        this.addToLog("Enabled: calls=" + preferenceService.isVoipEnabled() + " video=" + preferenceService.areVideoCallsEnabled());
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
    public void onYes(@NonNull String tag, @NonNull String text) {
        if (DIALOG_TAG_SEND_WEBRTC_DEBUG.equals(tag)) {
            // User confirmed that log should be sent to support
            sendToSupport(text);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void sendToSupport(@NonNull String caption) {
        SendToSupportBackgroundTask sendToSupportTask = new SendToSupportBackgroundTask(
            dependencies.getUserService().getIdentity(),
            dependencies.getApiConnector(),
            dependencies.getContactModelRepository(),
            this
        ) {
            @NonNull
            @Override
            public SendToSupportResult onSupportAvailable(@NonNull ContactModel contactModel) {
                try {
                    final ContactMessageReceiver messageReceiver = dependencies.getContactService().createReceiver(
                        contactModel
                    );

                    dependencies.getMessageService().sendText(clipboardString +
                        "\n---\n" +
                        caption +
                        "\n---\n" +
                        ConfigUtils.getSupportDeviceInfo() + "\n" +
                        "Threema " + ConfigUtils.getAppVersion() + "\n" +
                        dependencies.getUserService().getIdentity(), messageReceiver);

                    return SendToSupportResult.SUCCESS;
                } catch (Exception e) {
                    logger.error("Exception while sending information to support", e);
                    return SendToSupportResult.FAILED;
                }
            }

            @Override
            public void onFinished(SendToSupportResult result) {
                Toast.makeText(
                    getApplicationContext(),
                    result == SendToSupportResult.SUCCESS ? R.string.message_sent : R.string.an_error_occurred,
                    Toast.LENGTH_LONG
                ).show();
                finish();
            }
        };

        if (backgroundExecutor == null) {
            backgroundExecutor = new BackgroundExecutor();
        }
        backgroundExecutor.execute(sendToSupportTask);
    }

    @AnyThread
    private synchronized void cleanup() {
        if (this.peerConnectionClient != null) {
            this.peerConnectionClient.close();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

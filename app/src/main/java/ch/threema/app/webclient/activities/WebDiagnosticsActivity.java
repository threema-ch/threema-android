/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.app.webclient.activities;

import static ch.threema.app.preference.SettingsAdvancedOptionsFragment.THREEMA_SUPPORT_IDENTITY;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
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
import com.neovisionaries.ws.client.DualStackMode;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;

import org.saltyrtc.client.helpers.UnsignedHelper;
import org.saltyrtc.client.keystore.Box;
import org.saltyrtc.client.nonce.SignalingChannelNonce;
import org.saltyrtc.client.signaling.CloseCode;
import org.slf4j.Logger;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.WebRTCUtil;
import ch.threema.app.webclient.utils.DefaultNoopPeerConnectionObserver;
import ch.threema.app.webclient.utils.DefaultNoopWebSocketListener;
import ch.threema.app.webclient.webrtc.PeerConnectionWrapper;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;

@SuppressWarnings("FieldCanBeLocal")
@UiThread
public class WebDiagnosticsActivity extends ThreemaToolbarActivity implements TextEntryDialog.TextEntryDialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("WebDiagnosticsActivity");
	private static final String DIALOG_TAG_SEND_VOIP_DEBUG = "svd";

	// Config
	private static final String WS_HOST = "saltyrtc-ee.threema.ch";
	private static final String WS_BASE_URL = "wss://" + WS_HOST;
	private static final String WS_PATH = "ffffffffffffffff000000000000eeeeeeee000000000000ffffffffffffffff";
	private static final String WS_PROTOCOL = "v1.saltyrtc.org";
	private static final int WS_CONNECT_TIMEOUT_MS = 10000;
	private static final int WS_TEST_TIMEOUT_MS = WS_CONNECT_TIMEOUT_MS + 3000;
	private static final int RTC_TEST_TIMEOUT_MS = 12000;

	// Threema services
	@Nullable private ContactService contactService;

	// Views
	@Nullable private CircularProgressIndicator progressBar;
	@Nullable private TextView introText;
	@Nullable private TextView doneText;
	@Nullable private Button copyButton;
	@Nullable private Button sendButton;
	@Nullable private View footerButtons;

	// String that will be copied to clipboard
	@Nullable private String clipboardString;

	// Event logging
	@NonNull private final List<String> eventLog = new ArrayList<>();
	@Nullable private ArrayAdapter<String> adapter;
	private long startTime = 0;

	// Websocket
	@Nullable private WebSocket ws;
	private boolean wsDone = false;

	// WebRTC
	@Nullable private PeerConnection pc;
	@Nullable private PeerConnectionFactory pcFactory;
	private final AtomicInteger candidateCount = new AtomicInteger(0);
	private boolean rtcDone = false;

	// Executor service that should be used for running creation / destruction
	// of the peer connection and related objects.
	@Nullable private ScheduledExecutorService webrtcExecutor;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		logger.trace("onCreate");
		super.onCreate(savedInstanceState);

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.webclient_diagnostics);
		}
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		logger.trace("initActivity");
		if (!super.initActivity(savedInstanceState)) {
			return false;
		}

		// Initialize services
		try {
			this.contactService = this.serviceManager.getContactService();
		} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
			logger.error("Could not initialize services", e);
		}

		// Get view references
		this.progressBar = findViewById(R.id.webclient_diagnostics_loading);
		this.introText = findViewById(R.id.webclient_diagnostics_intro);
		this.doneText = findViewById(R.id.webclient_diagnostics_done);
		this.copyButton = findViewById(R.id.webclient_diagnostics_copy_button);
		this.sendButton = findViewById(R.id.webclient_diagnostics_send_button);
		this.footerButtons = findViewById(R.id.webclient_diagnostics_footer_buttons);

		// Wire up start button
		final Button startButton = findViewById(R.id.webclient_diagnostics_start);
		startButton.setOnClickListener(view -> {
			startButton.setVisibility(View.GONE);
			WebDiagnosticsActivity.this.startTests();
		});

		// Wire up copy button
		assert this.copyButton != null;
		this.copyButton.setOnClickListener(view -> {
			if (!TestUtil.isEmptyOrNull(this.clipboardString)) {
				WebDiagnosticsActivity.this.copyToClipboard(this.clipboardString);
			}
		});

		// Wire up send button
		assert this.sendButton != null;
		this.sendButton.setOnClickListener(view -> {
			if (!TestUtil.isEmptyOrNull(this.clipboardString)) {
				WebDiagnosticsActivity.this.prepareSendToSupport();
			}
		});

		// Initialize event log
		final ListView eventLog = findViewById(R.id.webclient_diagnostics_event_log);
		this.adapter = new ArrayAdapter<>(this, R.layout.item_webrtc_debug_list, this.eventLog);
		eventLog.setAdapter(this.adapter);

		return true;
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_webclient_debug;
	}

	@Override
	protected void onStart() {
		logger.trace("onStart");
		this.webrtcExecutor = Executors.newSingleThreadScheduledExecutor();
		super.onStart();
	}

	@Override
	protected void onStop() {
		logger.trace("onStop");
		this.cleanup();
		super.onStop();
	}

	@AnyThread
	private void resetStartTime() {
		this.startTime = System.nanoTime();
	}

	@AnyThread
	private void addLogSeparator() {
		this.addToLog("----------------", false);
	}

	@AnyThread
	private void addToLog(final String value, boolean timestamp) {
		final long elapsedNs = System.nanoTime() - this.startTime;
		final String logLine = timestamp
			? String.format("+%sms %s", elapsedNs / 1000 / 1000, value)
			: value;
		this.clipboardString += logLine + "\n";

		RuntimeUtil.runOnUiThread(() -> {
			synchronized (WebDiagnosticsActivity.this.eventLog) {
				logger.info(logLine);
				WebDiagnosticsActivity.this.eventLog.add(logLine);
				if (WebDiagnosticsActivity.this.adapter != null) {
					WebDiagnosticsActivity.this.adapter.notifyDataSetChanged();
				}
			}
		});
	}

	@AnyThread
	private void addToLog(final String value) {
		this.addToLog(value, true);
	}

	@UiThread
	private void copyToClipboard(@NonNull String text) {
		final ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		if (clipboard != null) {
			final String label = getString(R.string.webclient_diagnostics);
			final ClipData clip = ClipData.newPlainText(label, text);
			clipboard.setPrimaryClip(clip);
			Toast.makeText(getApplicationContext(), getString(R.string.voip_webrtc_debug_copied), Toast.LENGTH_LONG).show();
		}
	}

	private void prepareSendToSupport() {
		TextEntryDialog dialog = TextEntryDialog.newInstance(R.string.send_to_support,
			R.string.enter_description,
			R.string.send,
			R.string.cancel,
			5,
			3000,
			1);
		dialog.show(getSupportFragmentManager(), DIALOG_TAG_SEND_VOIP_DEBUG);
	}

	@SuppressLint("StaticFieldLeak")
	private void sendToSupport(@NonNull String caption) {
		final MessageService messageService;
		try {
			messageService = serviceManager.getMessageService();
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			return;
		}

		if (this.contactService == null || messageService == null) {
			return;
		}

		new AsyncTask<Void, Void, ContactMessageReceiver>() {
			@Override
			protected ContactMessageReceiver doInBackground(Void... voids) {
				try {
					final ContactModel contactModel = contactService.getOrCreateByIdentity(THREEMA_SUPPORT_IDENTITY, true);
					return contactService.createReceiver(contactModel);
				} catch (Exception e) {
					return null;
				}
			}

			@Override
			protected void onPostExecute(ContactMessageReceiver messageReceiver) {
				try {
					messageService.sendText(clipboardString +
						"\n---\n" +
						caption +
						"\n---\n" +
						ConfigUtils.getSupportDeviceInfo() + "\n" +
						"Threema " + ConfigUtils.getAppVersion() + "\n" +
						getMyIdentity(), messageReceiver);
					Toast.makeText(getApplicationContext(), R.string.message_sent, Toast.LENGTH_LONG).show();
					finish();
					return;
				} catch (Exception e1) {
					logger.error("Exception", e1);
				}
				Toast.makeText(getApplicationContext(), R.string.an_error_occurred, Toast.LENGTH_LONG).show();
			}
		}.execute();
	}

	@UiThread
	private void startTests() {
		logger.info("*** Starting Threema Web Diagnostics Test");
		this.eventLog.clear();
		this.clipboardString = "";
		this.resetStartTime();
		this.addToLog("Starting Threema Web Diagnostics...", false);

		// Update UI visibility
		assert this.progressBar != null;
		this.progressBar.setVisibility(View.VISIBLE);
		assert this.introText != null;
		this.introText.setVisibility(View.GONE);
		assert this.doneText != null;
		this.doneText.setVisibility(View.GONE);
		assert this.footerButtons != null;
		this.footerButtons.setVisibility(View.GONE);

		// Print connectivity info
		this.queryConnectivityInfo();

		// Start with WebSocket test
		this.startWsTest();
	}

	@UiThread
	private void queryConnectivityInfo() {
		final Context appContext = getApplicationContext();

		final ConnectivityManager connectivityManager =
			(ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);

		this.addLogSeparator();

		if (Build.VERSION.SDK_INT >= 21) { // Ignore Android 4
			// Add available networks
			final Network[] networks = connectivityManager.getAllNetworks();
			this.addToLog("Networks (" + networks.length + "):", false);
			for (Network network : networks) {
				final NetworkInfo info = connectivityManager.getNetworkInfo(network);
				final String typeName = info.getTypeName();
				final String fullType = info.getSubtypeName().isEmpty() ? typeName : typeName + "/" + info.getSubtypeName();
				final String detailedState = info.getDetailedState().toString();
				final String failover = "failover=" + info.isFailover();
				final String available = "available=" + info.isAvailable();
				final String roaming = "roaming=" + info.isRoaming();
				this.addToLog("- " + fullType + ", " + detailedState + ", " + failover + ", " + available + ", " + roaming, false);
			}
		} else {
			this.addToLog("API level " + Build.VERSION.SDK_INT + ", ignoring network info");
		}

		this.addLogSeparator();

		try {
			final List<String> addresses = new ArrayList<>();
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
				final NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
					final InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						final String addr = inetAddress.getHostAddress();
						if (inetAddress.isLinkLocalAddress()) {
							addresses.add(addr + " [link-local]");
						} else {
							addresses.add(addr);
						}
					}
				}
			}
			Collections.sort(addresses);
			this.addToLog("Non-loopback interfaces (" + addresses.size() + "):", false);
			for (String addr : addresses) {
				this.addToLog("- " + addr, false);
			}
		} catch (SocketException e) {
			this.addToLog("Socket exception when enumerating network interfaces: " + e.toString());
		}
	}

	/**
	 * Start the WebSocket test.
	 */
	@UiThread
	private synchronized void startWsTest() {
		this.wsDone = false;
		final Handler handler = new Handler();
		handler.postDelayed(() -> {
			if (!wsDone) {
				WebDiagnosticsActivity.this.failWs("WS test timed out");
			}
		}, WS_TEST_TIMEOUT_MS);
		RuntimeUtil.runInAsyncTask(() -> {
			final boolean success = WebDiagnosticsActivity.this.testWebsocket();
			if (!success) {
				addToLog("Initializing WebSocket test failed.");
			}
		});
	}

	/**
	 * Start the WebRTC test.
	 */
	@UiThread
	private synchronized void startRtcTest() {
		this.rtcDone = false;
		this.candidateCount.set(0);
		final Handler handler = new Handler();
		handler.postDelayed(() -> {
			if (!rtcDone) {
				WebDiagnosticsActivity.this.addToLog("WebRTC test timed out");
				WebDiagnosticsActivity.this.onRtcComplete(this.candidateCount.get() > 0);
			}
		}, RTC_TEST_TIMEOUT_MS);
		RuntimeUtil.runInAsyncTask(() -> {
			final boolean success = WebDiagnosticsActivity.this.testWebRTC();
			if (!success) {
				addToLog("Initializing WebRTC test failed.");
			}
		});
	}

	/**
	 * Initialize the WebSocket tests.
	 *
	 * If something during the initialization fails, return false.
	 */
	@AnyThread
	private boolean testWebsocket() {
		this.addLogSeparator();
		this.resetStartTime();
		this.addToLog("Starting WS tests");

		// Get configuration
		// Note: Below needs to be kept in sync with how dual stack mode is applied to the
		//       SaltyRTC WebSocket code.
		assert this.preferenceService != null;
		DualStackMode dualStackMode = DualStackMode.BOTH;
		if (!this.preferenceService.allowWebrtcIpv6()) {
			dualStackMode = DualStackMode.IPV4_ONLY;
		}
		this.addToLog("Setting: dualStackMode=" + dualStackMode.name());

		// Create WebSocket
		final String url = WS_BASE_URL + "/" + WS_PATH;
		logger.info("Connecting to " + url);
		try {
			this.ws = new WebSocketFactory()
				.setConnectionTimeout(WS_CONNECT_TIMEOUT_MS)
				.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(WS_HOST))
				.setVerifyHostname(true)
				.setDualStackMode(dualStackMode)
				.createSocket(url)
				.addProtocol(WS_PROTOCOL)
				.addListener(this.wsListener);
		} catch (IOException e) {
			this.failWs("IOException when creating WebSocket: " + e.getMessage(), e);
			return false;
		}

		// Connect
		try {
			this.addToLog("Connecting to WebSocket");
			assert this.ws != null;
			this.ws.connect();
		} catch (WebSocketException e) {
			this.failWs("WebSocketException when connecting: " + e.getMessage(), e);
			return false;
		}

		return true;
	}

	/**
	 * Initialize the WebRTC tests.
	 *
	 * If something during the initialization fails, return false.
	 */
	@AnyThread
	private boolean testWebRTC() {
		this.addLogSeparator();
		this.resetStartTime();
		this.addToLog("Starting WebRTC tests");

		// Get configuration
		assert this.preferenceService != null;
		final boolean allowIpv6 = this.preferenceService.allowWebrtcIpv6();
		this.addToLog("Setting: allowWebrtcIpv6=" + allowIpv6);

		// Set up peer connection
		assert this.webrtcExecutor != null;
		this.webrtcExecutor.execute(() -> {
			WebRTCUtil.initializePeerConnectionFactory(
				this.getApplicationContext(), WebRTCUtil.Scope.DIAGNOSTIC);

			final PeerConnection.RTCConfiguration rtcConfig;
			try {
				rtcConfig = PeerConnectionWrapper.getRTCConfiguration(logger);
			} catch (Exception e) {
				this.addToLog("Could not get RTC configuration: " + e.getMessage());
				return;
			}
			rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
			this.addToLog("Using " + rtcConfig.iceServers.size() + " ICE servers:");
			for (PeerConnection.IceServer server : rtcConfig.iceServers) {
				this.addToLog("- " + server.urls.toString());
			}

			// Instantiate peer connection
			this.pcFactory = PeerConnectionWrapper.getPeerConnectionFactory();
			this.pc = this.pcFactory.createPeerConnection(rtcConfig, this.pcObserver);
			if (this.pc == null) {
				this.addToLog("Could not create peer connection");
				return;
			}

			// Create a data channel and a offer to kick off ICE gathering
			this.pc.createDataChannel("trigger-ice-gathering", new DataChannel.Init());
			this.pc.createOffer(this.sdpObserver, new MediaConstraints());
		});
		return true;
	}

	private final WebSocketListener wsListener = new DefaultNoopWebSocketListener() {

		// State changes

		@Override
		public void onStateChanged(WebSocket websocket, WebSocketState newState) {
			addToLog("WS state changed to " + newState.name());
		}

		@Override
		public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
			final Socket socket;
			try {
				socket = websocket.getConnectedSocket();
			} catch (WebSocketException e) {
				addToLog("Unable to retrieve connected socket: " + e.toString());
				return;
			}
			final String local = socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort();
			final String remote = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
			addToLog("WS connected (" + local + " -> " + remote + ")");
		}

		@Override
		public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame,
		                           WebSocketFrame clientCloseFrame, boolean closedByServer) {
			if (closedByServer) {
				int code = serverCloseFrame.getCloseCode();
				addToLog("WS closed by server with code " + code + " (" + CloseCode.explain(code) + ")");
			} else {
				int code = clientCloseFrame.getCloseCode();
				addToLog("WS closed by us with code " + code + " (" + CloseCode.explain(code) + ")");
			}
			final boolean success = !closedByServer && clientCloseFrame.getCloseCode() == 1000;
			WebDiagnosticsActivity.this.onWsComplete(success);
		}

		// Data

		@Override
		public void onTextMessage(WebSocket websocket, String text) {
			addToLog("WS received text message, aborting");
			websocket.disconnect();
		}

		@Override
		public void onTextMessage(WebSocket websocket, byte[] data) {
			addToLog("WS received text message, aborting");
			websocket.disconnect();
		}

		@Override
		public void onBinaryMessage(WebSocket websocket, byte[] binary) {
			addToLog("WS received " + binary.length + " bytes");

			// This should be the server-hello message
			// Validate length
			if (binary.length < 81) {
				addToLog("Invalid message length: " + binary.length);
				websocket.disconnect(1000);
				return;
			}

			// Wrap message
			final Box box = new Box(
					ByteBuffer.wrap(binary),
					SignalingChannelNonce.TOTAL_LENGTH
			);

			// Validate nonce
			final SignalingChannelNonce nonce =
					new SignalingChannelNonce(ByteBuffer.wrap(box.getNonce()));
			if (nonce.getSource() != 0) {
				addToLog("Invalid nonce source: " + nonce.getSource());
				websocket.disconnect(1000);
				return;
			}
			if (nonce.getDestination() != 0) {
				addToLog("Invalid nonce destination: " + nonce.getDestination());
				websocket.disconnect(1000);
				return;
			}
			if (nonce.getOverflow() != 0) {
				addToLog("Invalid nonce overflow: " + nonce.getOverflow());
				websocket.disconnect(1000);
				return;
			}

			// Validate data
			// Data should start with 0x82 (fixmap with 2 entries) followed by a string
			// with either the value "type" or "key".
			final byte[] data = box.getData();
			short byte1 = UnsignedHelper.readUnsignedByte(data[0]);
			short byte2 = UnsignedHelper.readUnsignedByte(data[1]);
			short byte3 = UnsignedHelper.readUnsignedByte(data[2]);
			short byte4 = UnsignedHelper.readUnsignedByte(data[3]);
			short byte5 = UnsignedHelper.readUnsignedByte(data[4]);
			short byte6 = UnsignedHelper.readUnsignedByte(data[5]);
			if (byte1 != 0x82) {
				addToLog("Invalid data (does not start with 0x82)");
				websocket.disconnect(1000);
				return;
			}
			if (byte2 == 0xa3 && byte3 == 'k' && byte4 == 'e' && byte5 == 'y') {
				addToLog("Received server-hello message!");
			} else if (byte2 == 0xa4 && byte3 == 't' && byte4 == 'y' && byte5 == 'p' && byte6 == 'e') {
				addToLog("Received server-hello message!");
			} else {
				addToLog("Received invalid message (bad data)");
			}
			websocket.disconnect(1000);
		}

		// Errors

		@Override
		public void onConnectError(WebSocket websocket, WebSocketException cause) {
			WebDiagnosticsActivity.this.failWs("WS connect error: " + cause.toString());
		}

		@Override
		public void onError(WebSocket websocket, WebSocketException cause) {
			WebDiagnosticsActivity.this.failWs("WS error: " + cause.toString());
		}

		@Override
		public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) {
			WebDiagnosticsActivity.this.failWs("WS frame error: " + cause.toString());
		}

		@Override
		public void onMessageError(WebSocket websocket, WebSocketException cause, List<WebSocketFrame> frames) {
			WebDiagnosticsActivity.this.failWs("WS message error: " + cause.toString());
		}

		@Override
		public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) {
			WebDiagnosticsActivity.this.failWs("WS send error: " + cause.toString());
		}

	};

	private final PeerConnection.Observer pcObserver = new DefaultNoopPeerConnectionObserver() {
		@Override
		public void onSignalingChange(PeerConnection.SignalingState signalingState) {
			if (WebDiagnosticsActivity.this.pc == null) { return; }
			WebDiagnosticsActivity.this.addToLog("PC signaling state change to " + signalingState.name());
		}

		@Override
		public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
			if (WebDiagnosticsActivity.this.pc == null) { return; }
			WebDiagnosticsActivity.this.addToLog("ICE connection state change to " + iceConnectionState.name());
			switch (iceConnectionState) {
				case NEW:
				case CHECKING:
				case CONNECTED:
				case COMPLETED:
				case DISCONNECTED:
				case CLOSED:
					break;
				case FAILED:
					WebDiagnosticsActivity.this.failRtc("ICE failed");
					break;
			}
		}

		@Override
		public void onIceConnectionReceivingChange(boolean b) {
			if (WebDiagnosticsActivity.this.pc == null) { return; }
			WebDiagnosticsActivity.this.addToLog("ICE connection receiving: " + b);
		}

		@Override
		public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
			if (WebDiagnosticsActivity.this.pc == null) { return; }
			WebDiagnosticsActivity.this.addToLog("ICE gathering state change to " + iceGatheringState.name());
			switch (iceGatheringState) {
				case NEW:
				case GATHERING:
					break;
				case COMPLETE:
					WebDiagnosticsActivity.this.onRtcComplete(true);
					break;
			}
		}

		@Override
		public void onIceCandidate(IceCandidate candidate) {
			if (WebDiagnosticsActivity.this.pc == null) { return; }
			WebDiagnosticsActivity.this.addToLog(WebRTCUtil.iceCandidateToString(candidate));
			if (candidate == null) {
				WebDiagnosticsActivity.this.onRtcComplete(true);
			} else {
				WebDiagnosticsActivity.this.candidateCount.incrementAndGet();
				WebDiagnosticsActivity.this.addToLog(WebRTCUtil.iceCandidateToString(candidate));
			}
		}

		@Override
		public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
			if (WebDiagnosticsActivity.this.pc == null) { return; }
			for (IceCandidate candidate : iceCandidates) {
				WebDiagnosticsActivity.this.addToLog("Removed: " + WebRTCUtil.iceCandidateToString(candidate));
			}
		}

		@Override
		public void onRenegotiationNeeded() {
			if (WebDiagnosticsActivity.this.pc == null) { return; }
			WebDiagnosticsActivity.this.addToLog("ICE renegotiation needed");
		}
	};

	private final SdpObserver sdpObserver = new SdpObserver() {
		@Override
		public void onCreateSuccess(SessionDescription sessionDescription) {
			WebDiagnosticsActivity.this.addToLog("SDP create success");
			assert WebDiagnosticsActivity.this.webrtcExecutor != null;
			WebDiagnosticsActivity.this.webrtcExecutor.execute(() -> {
				if (WebDiagnosticsActivity.this.pc != null) {
					WebDiagnosticsActivity.this.pc.setLocalDescription(this, sessionDescription);
				} else {
					WebDiagnosticsActivity.this.failRtc("Could not set local description: Peer connection is null");
				}
			});
		}

		@Override
		public void onSetSuccess() {
			WebDiagnosticsActivity.this.addToLog("SDP set success");
		}

		@Override
		public void onCreateFailure(String s) {
			WebDiagnosticsActivity.this.addToLog("SDP create failure");
			WebDiagnosticsActivity.this.failRtc("Could not create SDP: " + s);
		}

		@Override
		public void onSetFailure(String s) {
			WebDiagnosticsActivity.this.addToLog("SDP set failure");
			WebDiagnosticsActivity.this.failRtc("Could not set SDP: " + s);
		}
	};

	@AnyThread
	private void failWs(@NonNull String message) {
		this.failWs(message, null);
	}

	@AnyThread
	private void failWs(@NonNull String message, @Nullable Exception e) {
		if (e != null) {
			logger.error("WS Exception", e);
		}
		this.addToLog(message);
		this.onWsComplete(false);
	}

	@AnyThread
	private void failRtc(@NonNull String message) {
		this.addToLog(message);
		this.onRtcComplete(false);
	}

	/**
	 * Test is complete.
	 */
	@AnyThread
	private void onWsComplete(boolean success) {
		this.addToLog("WS tests complete (success=" + success + ")");
		this.cleanupWs();
		this.wsDone = true;
		if (success) {
			this.runOnUiThread(this::startRtcTest);
		} else {
			RuntimeUtil.runOnUiThread(this::onComplete);
		}
	}

	/**
	 * Test is complete.
	 */
	@AnyThread
	private void onRtcComplete(boolean success) {
		this.addToLog("WebRTC tests complete (success=" + success + ")");
		this.cleanupRtc();
		this.rtcDone = true;
		RuntimeUtil.runOnUiThread(this::onComplete);
	}

	@UiThread
	private void onComplete() {
		final Handler handler = new Handler();
		handler.postDelayed(() -> {
			logger.info("*** Finished Threema Web Diagnostics Test");
			this.addLogSeparator();
			this.addToLog("Done.", false);
			RuntimeUtil.runOnUiThread(() -> {
				assert progressBar != null;
				progressBar.setVisibility(View.GONE);
				assert introText != null;
				introText.setVisibility(View.GONE);
				assert doneText != null;
				doneText.setVisibility(View.VISIBLE);
				assert footerButtons != null;
				footerButtons.setVisibility(View.VISIBLE);
			});
		}, 200);
	}

	@AnyThread
	private synchronized void cleanupWs() {
		logger.trace("cleanupWs");
		if (this.ws != null) {
			this.ws.clearListeners();
			this.ws.disconnect();
			this.ws = null;
		}
	}

	@AnyThread
	private synchronized void cleanupRtc() {
		logger.trace("cleanupRtc");
		if (this.pc != null) {
			assert this.webrtcExecutor != null;
			this.webrtcExecutor.execute(this.pc::dispose);
			this.pc = null;
		}
		if (this.pcFactory != null) {
			assert this.webrtcExecutor != null;
			this.webrtcExecutor.execute(this.pcFactory::dispose);
			this.pcFactory = null;
		}
	}

	@AnyThread
	private synchronized void cleanup() {
		logger.info("Cleaning up resources");
		this.cleanupWs();
		this.cleanupRtc();
		if (this.webrtcExecutor != null) {
			this.webrtcExecutor.shutdown();
			try {
				if (!this.webrtcExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
					this.webrtcExecutor.shutdownNow();
				}
			} catch (InterruptedException e) {
				this.webrtcExecutor.shutdownNow();
			}
			this.webrtcExecutor = null;
		}
	}

	@Override
	public void onYes(@NonNull String tag, @NonNull String text) {
		if (DIALOG_TAG_SEND_VOIP_DEBUG.equals(tag)) {
			sendToSupport(text);
		}
	}

	@Override
	public void onNo(String tag) {
	}

	@Override
	public void onNeutral(String tag) {
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		//noinspection SwitchStatementWithTooFewBranches
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
}

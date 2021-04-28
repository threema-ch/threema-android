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
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package ch.threema.app.voip;

import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import com.google.protobuf.InvalidProtocolBufferException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CryptoOptions;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.R;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.WebRTCUtil;
import ch.threema.app.voip.signaling.CaptureState;
import ch.threema.app.voip.signaling.ToSignalingMessage;
import ch.threema.app.voip.util.SdpPatcher;
import ch.threema.app.voip.util.SdpUtil;
import ch.threema.app.voip.util.VideoCapturerUtil;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.app.voip.util.VoipVideoParams;
import ch.threema.app.webrtc.DataChannelObserver;
import ch.threema.app.webrtc.UnboundedFlowControlledDataChannel;
import ch.threema.client.APIConnector;
import ch.threema.protobuf.callsignaling.CallSignaling;
import java8.util.concurrent.CompletableFuture;
import java8.util.stream.StreamSupport;

/**
 * Peer connection client implementation.
 *
 * All public methods are routed to local looper thread.
 * All PeerConnectionEvents callbacks are invoked from the same looper thread.
 */
public class PeerConnectionClient {
	// Note: Not static, because we want to set a prefix
	private final Logger logger = LoggerFactory.getLogger(PeerConnectionClient.class);

	private static final String AUDIO_TRACK_ID = "3MACALLa0";
	private static final String AUDIO_CODEC_OPUS = "opus";
	private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
	private static final String AUDIO_LEVEL_CONTROL_CONSTRAINT = "levelControl";

	private static final String VIDEO_TRACK_ID = "3MACALLv0";
	private static final String VIDEO_TRACK_TYPE = "video";

	// Capturing settings. What's being sent may be lower.
	private static final int VIDEO_WIDTH = 1920;
	private static final int VIDEO_HEIGHT = 1080;
	private static final int VIDEO_FPS = 25;

	private static final String SIGNALING_CHANNEL_ID = "3MACALLdc0";

	// Stats semaphore (wait for all pending stats to be complete)
	private final Semaphore statsLock = new Semaphore(1);
	private int statsCounter = 0;

	// General
	private final @NonNull Context appContext;
	private final long callId;

	// Peer connection
	private @Nullable PeerConnectionFactory factory = null;
	private final @NonNull Semaphore factoryInitializing = new Semaphore(1);
	private PeerConnection peerConnection;
	private final @NonNull PeerConnectionParameters peerConnectionParameters;
	private final @NonNull SdpPatcher sdpPatcher;

	// Observers and events
	private @Nullable Events events;
	private final PCObserver pcObserver = new PCObserver();
	private final SDPObserver sdpObserver = new SDPObserver();
	private final DCObserver dcObserver = new DCObserver();

	// Signaling
	private @Nullable UnboundedFlowControlledDataChannel signalingDataChannel;

	// Executor service for everything that has to do with libwebrtc
	private final ScheduledExecutorService executor;

	// Queued remote ICE candidates are consumed only after both local and
	// remote descriptions are set. Similarly local ICE candidates are sent to
	// remote peer after both local and remote description are set.
	private LinkedList<IceCandidate> queuedRemoteCandidates = null;

	// Video
	private final boolean renderVideo = true; // Always render remote video, to avoid masking bugs
	private final @Nullable EglBase.Context eglBaseContext;
	private @Nullable VideoSink localVideoSink;
	private @Nullable VideoSink remoteVideoSink;
	private @Nullable VideoTrack localVideoTrack;
	private @Nullable VideoTrack remoteVideoTrack;
	private @Nullable RtpSender localVideoSender;
	private @Nullable SurfaceTextureHelper surfaceTextureHelper;
	private @Nullable VideoSource videoSource;

	// Video capturer. Always lock the `capturingLock` when modifying the capturer in any way!
	private @Nullable VideoCapturer videoCapturer;
	private final Object capturingLock = new Object();

	// Audio
	private boolean enableAudio = true;
	private @Nullable AudioTrack localAudioTrack;
	private @Nullable AudioSource audioSource;

	// Outgoing audio
	private boolean enableLocalAudioTrack = true;

	// Media constraints
	private MediaConstraints audioConstraints;
	private MediaConstraints sdpMediaConstraints;

	// State
	private boolean isInitiator;
	private boolean isError = false;

	// Stats
	private RTCStatsCollectorCallback afterClosingStatsCallback = null;
	private final Map<RTCStatsCollectorCallback, Timer> periodicStatsTimers = new HashMap<>();

	// Local session description
	private SessionDescription localSdp = null; // either offer or answer SDP

	// Workaround for ANDR-1079 / CRBUG 935905
	private @Nullable Long setRemoteDescriptionNanotime = null;
	private @Nullable ScheduledFuture<?> iceFailedFuture = null;

	// Workaround for ANDR-1119
	private @Nullable List<RtpTransceiver> cachedRtpTransceivers = null;

	// Flag for disabling the use of ICE servers (TURN) for testing purposes
	private boolean enableIceServers = true;

	/**
	 * Peer connection parameters.
	 */
	public static class PeerConnectionParameters {
		// Trace logging
		final boolean tracing;

		// Audio
		final boolean useOpenSLES;
		final boolean disableBuiltInAEC;
		final boolean disableBuiltInAGC;
		final boolean disableBuiltInNS;
		final boolean enableLevelControl;

		// Video
		final boolean videoCallEnabled;
		final boolean videoCodecHwAcceleration;

		final boolean videoCodecEnableVP8;
		final boolean videoCodecEnableH264HiP;

		// RTP
		@NonNull final SdpPatcher.RtpHeaderExtensionConfig rtpHeaderExtensionConfig;

		// Networking
		final boolean forceTurn;
		final boolean gatherContinually;
		final boolean allowIpv6;

		/**
		 * Initialize the peer connection client.
		 *
		 * @param tracing Enable WebRTC trace logging. Should only be used for internal debugging.
		 * @param useOpenSLES
		 * @param disableBuiltInAEC Disable acoustic echo cancelation
		 * @param disableBuiltInAGC Disable automatic gain control
		 * @param disableBuiltInNS Disable noise suppression
		 * @param enableLevelControl
		 * @param videoCallEnabled
		 * @param videoCodecHwAcceleration
		 * @param rtpHeaderExtensionConfig See {@link SdpPatcher}
		 * @param forceTurn Whether TURN servers should be forced (relay only).
		 * @param gatherContinually Whether ICE candidates should be gathered continually.
		 * @param allowIpv6 Whether IPv6 should be allowed
		 */
		public PeerConnectionParameters(
			boolean tracing,
			boolean useOpenSLES,
			boolean disableBuiltInAEC,
			boolean disableBuiltInAGC,
			boolean disableBuiltInNS,
			boolean enableLevelControl,
			boolean videoCallEnabled,
			boolean videoCodecHwAcceleration,
			boolean videoCodecEnableVP8,
			boolean videoCodecEnableH264HiP,
			@NonNull SdpPatcher.RtpHeaderExtensionConfig rtpHeaderExtensionConfig,
			boolean forceTurn,
			boolean gatherContinually,
			boolean allowIpv6
		) {
			// Logging
			this.tracing = tracing;

			// Audio
			this.useOpenSLES = useOpenSLES;
			this.disableBuiltInAEC = disableBuiltInAEC;
			this.disableBuiltInAGC = disableBuiltInAGC;
			this.disableBuiltInNS = disableBuiltInNS;
			this.enableLevelControl = enableLevelControl;

			// Video
			this.videoCallEnabled = videoCallEnabled;
			this.videoCodecHwAcceleration = videoCodecHwAcceleration;
			this.videoCodecEnableVP8 = videoCodecEnableVP8;
			this.videoCodecEnableH264HiP = videoCodecEnableH264HiP;

			// RTP
			this.rtpHeaderExtensionConfig = rtpHeaderExtensionConfig;

			// Networking
			this.forceTurn = forceTurn;
			this.gatherContinually = gatherContinually;
			this.allowIpv6 = allowIpv6;
		}
	}

	/**
	 * Subscribe to this event handler to be notified about events
	 * happening in the PeerConnectionClient.
	 */
	public interface Events {
		/**
		 * Callback fired once local SDP is created and set.
		 */
		void onLocalDescription(long callId, final SessionDescription sdp);

		/**
		 * Callback fired once remote SDP is set.
		 */
		void onRemoteDescriptionSet(long callId);

		/**
		 * Callback fired once local Ice candidate is generated.
		 */
		void onIceCandidate(long callId, final IceCandidate candidate);

		/**
		 * Callback fired once connection is starting to check candidate pairs
		 * (IceConnectionState is CHECKING).
		 */
		void onIceChecking(long callId);

		/**
		 * Callback fired once connection is established (IceConnectionState is
		 * CONNECTED).
		 */
		void onIceConnected(long callId);

		/**
		 * Callback fired once connection is closed (IceConnectionState is
		 * DISCONNECTED).
		 */
		void onIceDisconnected(long callId);

		/**
		 * Callback fired if connection fails (IceConnectionState is
		 * FAILED).
		 *
		 * NOTE: Due to ANDR-1079 (CRBUG 935905), this will not be called
		 *       earlier than 15 seconds after the connection attempt was started.
		 */
		@AnyThread
		void onIceFailed(long callId);

		/**
		 * Callback fired if the ICE gathering state changes.
		 */
		void onIceGatheringStateChange(long callId, IceGatheringState newState);

		/**
		 * Callback fired once peer connection is closed.
		 */
		void onPeerConnectionClosed(long callId);

		/**
		 * Callback fired when an error occurred.
		 *
		 * If the `abortCall` flag is set, the error is critical
		 * and the call should be aborted.
		 */
		void onError(long callId, final @NonNull String description, boolean abortCall);

		/**
		 * Called when a new signaling message from the peer arrives.
		 *
		 * @param envelope The protobuf envelope.
		 */
		@WorkerThread
		default void onSignalingMessage(long callId, final @NonNull CallSignaling.Envelope envelope) { }

		/**
		 * This is triggered whenever a capturing camera reports the first available frame.
		 */
		default void onCameraFirstFrameAvailable() { }
	}

	/**
	 * Create a PeerConnectionClient with the specified parameters.
	 */
	public PeerConnectionClient(
		final @NonNull Context appContext,
		final @NonNull PeerConnectionParameters peerConnectionParameters,
		final @Nullable EglBase.Context eglBaseContext,
		final long callId
	) {
		// Set logging prefix
		VoipUtil.setLoggerPrefix(logger, callId);

		// Create logger for SdpPatcher
		final Logger sdpPatcherLogger = LoggerFactory.getLogger(PeerConnectionClient.class + ":" + "SdpPatcher");
		VoipUtil.setLoggerPrefix(sdpPatcherLogger, callId);

		// Initialize instance variables
		this.appContext = appContext;
		this.peerConnectionParameters = peerConnectionParameters;
		this.sdpPatcher = new SdpPatcher()
			.withLogger(sdpPatcherLogger)
			.withRtpHeaderExtensions(this.peerConnectionParameters.rtpHeaderExtensionConfig);
		this.eglBaseContext = eglBaseContext;
		this.callId = callId;

		// Executor thread is started once in private ctor and is used for all
		// peer connection API calls to ensure new peer connection factory is
		// created on the same thread as previously destroyed factory.
		this.executor = Executors.newSingleThreadScheduledExecutor();
	}

	/**
	 * Set the `PeerConnectionEvents` handler.
	 */
	public void setEventHandler(final @Nullable Events events) {
		this.events = events;
	}

	/**
	 * Enable or disable the use of ICE servers (defaults to enabled).
	 *
	 * @param enableIceServers
	 */
	public void setEnableIceServers(boolean enableIceServers) {
		this.enableIceServers = enableIceServers;
	}

	/**
	 * Create a peer connection factory.
	 *
	 * Return a future that resolves to true if the factory could be created,
	 * or to false otherwise.
	 */
	@AnyThread
	public CompletableFuture<Boolean> createPeerConnectionFactory() {
		final CompletableFuture<Boolean> future = new CompletableFuture<>();
		this.executor.execute(() -> createPeerConnectionFactoryInternal(future));
		return future;
	}

	@AnyThread
	public void createPeerConnection() {
		this.createPeerConnection(null, null);
	}

	@AnyThread
	public void createPeerConnection(@Nullable VideoSink localVideoSink, @Nullable VideoSink remoteVideoSink) {
		try {
			// Ensure that the factory is not currently being initialized.
			this.factoryInitializing.acquire();
			this.factoryInitializing.release();
		} catch (InterruptedException e) {
			logger.error("Exception", e);
			Thread.currentThread().interrupt();
		}
		if (this.factory == null) {
			logger.error("Cannot create peer connection without initializing factory first");
			return;
		}
		this.localVideoSink = localVideoSink;
		this.remoteVideoSink = remoteVideoSink;
		this.executor.execute(() -> {
			try {
				this.createMediaConstraintsInternal();
				this.createPeerConnectionInternal();
			} catch (Exception e) {
				this.reportError("Failed to create peer connection: " + e.getMessage(), e, true);
			}
		});
	}

	@AnyThread
	public void close() {
		executor.execute(this::closeInternal);
	}

	private boolean isVideoCallEnabled() {
		return this.peerConnectionParameters.videoCallEnabled;
	}

	/**
	 * Create the peer connection factory.
	 * @return true if the factory was created, false otherwise.
	 */
	@WorkerThread
	private boolean createPeerConnectionFactoryInternal(@NonNull CompletableFuture<Boolean> future) {
		logger.info("Create peer connection factory");
		if (this.factory != null) {
			logger.error("Peer connetion factory already initialized");
			future.complete(false);
			return false;
		}
		try {
			this.factoryInitializing.acquire();
		} catch (InterruptedException e) {
			logger.error("Interrupted while acquiring semaphore", e);
			future.complete(false);
			return false;
		}

		this.isError = false;

		// Initialize peer connection factory
		WebRTCUtil.initializeAndroidGlobals(this.appContext);

		// Enable/disable tracing
		//
		// NOTE: For this to work, the "enableVerboseInternalTracing" option needs to be uncommented
		//       in `WebRTCUtil#initializeAndroidGlobals`.
		if (this.peerConnectionParameters.tracing) {
			final String tracingFilePath = this.appContext.getCacheDir().getAbsolutePath()
				+ File.separator + "webrtc-trace.log";
			logger.info("Writing WebRTC trace to {}", tracingFilePath);
			PeerConnectionFactory.startInternalTracingCapture(tracingFilePath);
		}

		// Enable/disable OpenSL ES playback
		if (!peerConnectionParameters.useOpenSLES) {
			logger.info("Disable OpenSL ES audio even if device supports it");
			WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true /* enable */);
		} else {
			logger.info("Allow OpenSL ES audio if device supports it");
			WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(false);
		}

		// Enable/disable acoustic echo cancelation
		if (peerConnectionParameters.disableBuiltInAEC) {
			logger.info("Disable built-in AEC even if device supports it");
			WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
		} else {
			logger.info("Enable built-in AEC if device supports it");
			WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(false);
		}

		// Enable/disable automatic gain control
		if (peerConnectionParameters.disableBuiltInAGC) {
			logger.info("Disable built-in AGC even if device supports it");
			WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true);
		} else {
			logger.info("Enable built-in AGC if device supports it");
			WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(false);
		}

		// Enable/disable built-in noise suppressor
		if (peerConnectionParameters.disableBuiltInNS) {
			logger.info("Disable built-in NS even if device supports it");
			WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);
		} else {
			logger.info("Enable built-in NS if device supports it");
			WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(false);
		}

		// Determine video encoder/decoder factory
		final VideoEncoderFactory encoderFactory;
		final VideoDecoderFactory decoderFactory;
		boolean useHardwareVideoCodec = peerConnectionParameters.videoCodecHwAcceleration;
		if (!Config.allowHardwareVideoCodec()) {
			this.logger.info("Video codec: Device {} is on hardware codec exclusion list", Build.MODEL);
			useHardwareVideoCodec = false;
		}
		if (useHardwareVideoCodec && this.eglBaseContext != null) {
			logger.info("Video codec: HW acceleration (VP8={}, H264HiP={})",
				peerConnectionParameters.videoCodecEnableVP8,
				peerConnectionParameters.videoCodecEnableH264HiP);
			final boolean enableIntelVp8Encoder = peerConnectionParameters.videoCodecEnableVP8;
			final boolean enableH264HighProfile = peerConnectionParameters.videoCodecEnableH264HiP;
			encoderFactory = new DefaultVideoEncoderFactory(
					this.eglBaseContext,
					enableIntelVp8Encoder,
					enableH264HighProfile
			);
			decoderFactory = new DefaultVideoDecoderFactory(this.eglBaseContext);
		} else {
			logger.info("Video codec: SW acceleration");
			encoderFactory = new SoftwareVideoEncoderFactory();
			decoderFactory = new SoftwareVideoDecoderFactory();
		}

		// Create peer connection factor
		logger.debug("Creating peer connection factory");
		final PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
		this.factory = PeerConnectionFactory.builder()
				.setOptions(options)
				.setVideoDecoderFactory(decoderFactory)
				.setVideoEncoderFactory(encoderFactory)
				.createPeerConnectionFactory();
		if (this.factory == null) {
			logger.error("Could not create peer connection factory");
			throw new RuntimeException("createPeerConnectionFactoryInternal: createPeerConnectionFactory returned null");
		}
		logger.info("Peer connection factory created");

		this.factoryInitializing.release();
		future.complete(true);
		return true;
	}

	@WorkerThread
	private void createMediaConstraintsInternal() {
		// Create audio constraints.
		this.audioConstraints = new MediaConstraints();

		// Added for audio performance measurements
		if (this.peerConnectionParameters.enableLevelControl) {
			logger.info("Enabling level control");
			this.audioConstraints.mandatory.add(
					new MediaConstraints.KeyValuePair(AUDIO_LEVEL_CONTROL_CONSTRAINT, "true"));
		}

		// Create SDP constraints.
		this.sdpMediaConstraints = new MediaConstraints();
		this.sdpMediaConstraints.mandatory.add(
			new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
		this.sdpMediaConstraints.mandatory.add(
			new MediaConstraints.KeyValuePair("OfferToReceiveVideo", Boolean.toString(this.isVideoCallEnabled())));
	}

	@WorkerThread
	private void createPeerConnectionInternal() throws Exception {
		logger.info("Create peer connection");
		if (this.factory == null) {
			logger.error("createPeerConnectionInternal: Peer connection factory is null");
			throw new IllegalStateException("Peer connection factory is null");
		}
		if (this.isError) {
			logger.error("createPeerConnectionInternal: isError = true");
			throw new IllegalStateException("isError=true when creating peer connection");
		}

		// Determine ICE servers
		this.queuedRemoteCandidates = new LinkedList<>();

		if (this.peerConnectionParameters.allowIpv6) {
			logger.info("Using dual-stack mode");
		} else {
			logger.info("Using v4 only mode");
		}

		final PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(getIceServers());

		// We don't want TCP candidates at the moment since TCP retransmissions can become
		// a problem with a bad connection.
		rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
		rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
		rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
		if (this.peerConnectionParameters.gatherContinually) {
			rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
		} else {
			rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
		}

		// If we want to force the use of a TURN server (to hide the local IP),
		// set the ICE transport type to RELAY only.
		if (this.peerConnectionParameters.forceTurn) {
			rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
		} else {
			rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
		}

		// Use ECDSA encryption.
		rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

		// Opt-in to unified plan SDP
		rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

		// Crypto options
		final CryptoOptions.Builder cryptoOptions = CryptoOptions.builder();

		// Our patches already ensure that the SRTP cipher AES 128 SHA1 32 is disabled and the
		// AES 128 SHA1 80 SRTP cipher is moved to the bottom.
		// However, just to be safe here we'll explicitly enable AES GCM SRTP suites,
		// disable AES 128 SHA1 32 but keep AES 128 SHA1 80 for backwards compatibility.
		cryptoOptions
			.setEnableGcmCryptoSuites(true)
			.setEnableAes128Sha1_80CryptoCipher(true)
			.setEnableAes128Sha1_32CryptoCipher(false);

		// Enable RTP header encryption extension (RFC 6904) and one/two-byte RTP
		// header mixed mode.
		//
		// Note: Be aware that this is not backwards compatible with older apps because
		//       bugs bugs bugs! But it doesn't matter since audio does not need any RTP header
		//       extensions and the `SdpPatcher` ensures the `a=extmap-allow-mixed` attribute is
		//       being stripped when setting `twoByteRtpHeaderSupport` to `false`
		cryptoOptions.setEnableEncryptedRtpHeaderExtensions(true);
		rtcConfig.offerExtmapAllowMixed = true;  // NEVER disable this or you will see crashes!

		// Apply crypto options
		rtcConfig.cryptoOptions = cryptoOptions.createCryptoOptions();

		// Create peer connection
		this.peerConnection = factory.createPeerConnection(rtcConfig, pcObserver);
		if (this.peerConnection == null) {
			logger.error("Could not create peer connection (factory.createPeerConnection returned null");
			throw new RuntimeException("createPeerConnectionInternal: createPeerConnection returned null");
		}

		this.isInitiator = false;

		// Determine media stream label
		final List<String> mediaStreamLabels = Collections.singletonList("3MACALL");

		// Add an audio track
		final AudioTrack audioTrack = this.createAudioTrack();
		this.peerConnection.addTrack(audioTrack, mediaStreamLabels);

		// Add a video track
		if (this.isVideoCallEnabled()) {
			logger.debug("Adding video track");
			final VideoTrack videoTrack = this.createVideoTrack();
			if (videoTrack != null) {
				this.localVideoSender = this.peerConnection.addTrack(videoTrack, mediaStreamLabels);
			} else {
				logger.error("Could not create local video track");
			}

			// We can add the renderers right away because we don't need to wait for an
			// answer to get the remote track.
			this.remoteVideoTrack = this.getRemoteVideoTrack();
			if (this.remoteVideoTrack != null) {
				this.remoteVideoTrack.setEnabled(this.renderVideo);
				if (this.remoteVideoSink != null) {
					this.remoteVideoTrack.addSink(this.remoteVideoSink);
				} else {
					logger.error("Could not add sink to remote video track");
				}
			} else {
				logger.error("Could not get remote video track");
			}
		}
		logger.info("Peer connection created");

		// Add a negotiated data channel with ID 0.
		// Note: Since this is a negotiated data channel, no in-band signaling will take place.
		//       Therefore we must take care to not send a message until the other peer has created
		//       a data channel to receive it. Since at this point in time we are still constructing
		//       the peer connection and have not yet created an offer or answer, this should not
		//       be an issue.
		// Note: By passing in the 'open' future which resolves once the state is open, we can
		//       immediately start queueing messages.
		final DataChannel.Init init = new DataChannel.Init();
		init.id = 0;
		init.negotiated = true;
		init.ordered = true;
		final DataChannel dc = peerConnection.createDataChannel(SIGNALING_CHANNEL_ID, init);
		this.dcObserver.register(dc);
		this.signalingDataChannel = new UnboundedFlowControlledDataChannel(
			"SignalingDataChannel",
			dc,
			this.dcObserver.openFuture
		);
		logger.info("Data channel created");
	}

	@WorkerThread
	private List<PeerConnection.IceServer> getIceServers() throws Exception {
		if (!enableIceServers) {
			logger.debug("ICE servers disabled");
			return new ArrayList<>();
		}

		final List<org.webrtc.PeerConnection.IceServer> iceServers = new ArrayList<>();

		// forceTurn determines whether to use dual stack enabled TURN servers.
		// In normal mode, the device is either:
		// a) IPv4 only or dual stack. It can then be reached directly or via relaying over IPv4 TURN servers.
		// b) IPv6 only and then **must** be reachable via a peer-to-peer connection.
		//
		// When enforcing relayed mode, the device may have an IPv6 only configuration, so we need to be able
		// to reach our TURN servers via IPv6 or no connection can be established at all.
		final APIConnector.TurnServerInfo turnServerInfo = Config.getTurnServerCache().getTurnServers();
		final List<String> turnServers = Arrays.asList(this.peerConnectionParameters.forceTurn ? turnServerInfo.turnUrlsDualStack: turnServerInfo.turnUrls);
		StreamSupport.stream(turnServers)
			.map(server -> PeerConnection.IceServer.builder(server)
				.setUsername(turnServerInfo.turnUsername)
				.setPassword(turnServerInfo.turnPassword)
				.createIceServer())
			.forEach(iceServers::add);
		logger.debug("Using ICE servers: {}", turnServers);
		return iceServers;
	}

	/**
	 * Set the outgoing video encoder limits.
	 *
	 * @param maxBitrate The max bitrate in bits per second. If set to null,
	 *                   any limit will be removed.
	 * @param maxFps Max frame rate (e.g. 25 or 20)
	 */
	@WorkerThread
	private void setOutgoingVideoEncoderLimits(
		@Nullable Integer maxBitrate,
		int maxFps
	) {
		if (!this.isVideoCallEnabled()) {
			// Video calls not enabled, ignoring
			return;
		}

		logger.info("setOutgoingVideoBandwidthLimit: " + maxBitrate);
		final RtpSender sender = this.localVideoSender;
		if (sender == null) {
			logger.error("setOutgoingVideoBandwidthLimit: Could not find local video sender");
			return;
		}

		// Get current parameters
		final RtpParameters parameters = sender.getParameters();
		if (parameters == null) {
			logger.error("setOutgoingVideoBandwidthLimit: Video sender has no parameters");
			return;
		}

		// Configure parameters
		parameters.degradationPreference = RtpParameters.DegradationPreference.BALANCED;
		for (RtpParameters.Encoding encoding : parameters.encodings) {
			this.logRtpEncoding("before", encoding);
			encoding.maxBitrateBps = maxBitrate;
			encoding.maxFramerate = maxFps;
			this.logRtpEncoding("after", encoding);
		}
		boolean success = sender.setParameters(parameters);
		if (success) {
			logger.debug("Updated RtpParameters");
		} else {
			logger.error("Failed to update RtpParameters");
		}
	}

	@AnyThread
	private void logRtpEncoding(@NonNull String tag, @NonNull RtpParameters.Encoding encoding) {
		logger.debug(
			"RtpParameters[{}]: Encoding: ssrc={} maxBitrate={} maxFramerate={} scale={} active={}",
			tag,
			encoding.ssrc,
			encoding.maxBitrateBps,
			encoding.maxFramerate,
			encoding.scaleResolutionDownBy,
			encoding.active
		);
	}

	@WorkerThread
	private void closeInternal() {
		// Cancel ICE failed future and reset time variables
		if (this.iceFailedFuture != null) {
			this.iceFailedFuture.cancel(true);
			this.iceFailedFuture = null;
			logger.info("iceFailedFuture: Cancelled (closeInternal)");
		}
		this.setRemoteDescriptionNanotime = null;

		// Stop creating further stats requests
		logger.debug("Clearing periodic stats timers");
		for (Timer timer : this.periodicStatsTimers.values()) {
			timer.cancel();
		}
		this.periodicStatsTimers.clear();

		// Requests stats after having closed the peer connection (if requested)
		if (this.afterClosingStatsCallback != null) {
			this.getStats(this.afterClosingStatsCallback);
		}

		// Wait for asynchronous stats to finish
		logger.debug("Waiting for {} pending stats to finish", this.statsCounter);
		boolean acquired = false;
		try {
			acquired = this.statsLock.tryAcquire(5, TimeUnit.SECONDS);
		} catch (InterruptedException ignored) {
			logger.error("Spurious wakeup!");
		}

		try {
			logger.info("Closing signaling data channel");
			if (signalingDataChannel != null) {
				signalingDataChannel.dc.close();
				signalingDataChannel.dc.unregisterObserver();
				signalingDataChannel.dc.dispose();
				signalingDataChannel = null;
			}
			logger.info("Closing peer connection");
			if (peerConnection != null) {
				peerConnection.close();
			}
			logger.info("Disposing peer connection");
			if (peerConnection != null) {
				peerConnection.dispose();
				peerConnection = null;
			}
			logger.info("Disposing audio source");
			if (audioSource != null) {
				audioSource.dispose();
				audioSource = null;
			}
			logger.info("Stopping and disposing capturer");
			synchronized (this.capturingLock) {
				if (this.videoCapturer != null) {
					try {
						this.videoCapturer.stopCapture();
					} catch (InterruptedException e) {
						logger.error("Spurious wakeup!");
					}
					this.videoCapturer.dispose();
					this.videoCapturer = null;
				}
			}
			logger.info("Disposing video source");
			if (this.videoSource != null) {
				this.videoSource.dispose();
				this.videoSource = null;
			}
			if (this.surfaceTextureHelper != null) {
				this.surfaceTextureHelper.dispose();
				this.surfaceTextureHelper = null;
			}
			this.localVideoSink = null;
			this.remoteVideoSink =  null;
			logger.info("Disposing peer connection factory");
			if (factory != null) {
				factory.dispose();
				factory = null;
			}
			if (this.events != null) {
				this.events.onPeerConnectionClosed(this.callId);
			}
			if (this.peerConnectionParameters.tracing) {
				PeerConnectionFactory.stopInternalTracingCapture();
				PeerConnectionFactory.shutdownInternalTracer();
			}
			this.events = null;
		} finally {
			// Release
			if (acquired) {
				this.statsLock.release();
			}
		}
	}

	//region Stats

	public void getStats(@NonNull RTCStatsCollectorCallback callback) {
		if (this.peerConnection == null || this.isError) {
			return;
		}

		// Lock until all pending stats have been retrieved
		synchronized (this.statsLock) {
			if (this.statsCounter == 0) {
				try {
					this.statsLock.acquire();
				} catch (InterruptedException ignored) {
					logger.warn("Spurious wakeup!");
					return;
				}
			}
			++this.statsCounter;
		}
		this.peerConnection.getStats(report -> {
			try {
				callback.onStatsDelivered(report);
			} finally {
				synchronized (this.statsLock) {
					--this.statsCounter;
					if (this.statsCounter == 0) {
						this.statsLock.release();
					}
				}
			}
		});
	}

	public void setAfterClosingStatsCallback(@NonNull RTCStatsCollectorCallback callback) {
		this.afterClosingStatsCallback = callback;
	}

	public boolean isPeriodicStatsRegistered(@Nullable RTCStatsCollectorCallback callback) {
		if (callback == null) {
			return false;
		}
		return this.periodicStatsTimers.containsKey(callback);
	}

	@AnyThread
	public void registerPeriodicStats(@NonNull RTCStatsCollectorCallback callback, long periodMs) {
		logger.debug("Registering stats every " + periodMs + "ms for callback " + callback);
		Timer timer;
		try {
			timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					executor.execute(() -> PeerConnectionClient.this.getStats(callback));
				}
			}, periodMs, periodMs);
		} catch (Exception e) {
			logger.error("Cannot schedule statistics timer", e);
			return;
		}
		this.periodicStatsTimers.put(callback, timer);

		// Run immediately (once)
		this.getStats(callback);
	}

	public void unregisterPeriodicStats(@Nullable RTCStatsCollectorCallback callback) {
		if (callback == null) {
			return;
		}
		final Timer timer = this.periodicStatsTimers.remove(callback);
		if (timer != null) {
			timer.cancel();
			logger.debug("Unregistered stats for callback " + callback);
		}
	}

	//endregion

	//region Muting / Unmuting

	/**
	 * Mute or unmute outgoing audio.
	 */
	public void setLocalAudioTrackEnabled(final boolean enable) {
		executor.execute(() -> {
			enableLocalAudioTrack = enable;
			if (localAudioTrack != null) {
				if (localAudioTrack.enabled() != enableLocalAudioTrack) {
					localAudioTrack.setEnabled(enableLocalAudioTrack);
					this.sendSignalingMessage(CaptureState.microphone(enableLocalAudioTrack));
				}
			}
		});
	}

	/**
	 * Mute or unmute incoming audio.
	 */
	public void setRemoteAudioTrackEnabled(final boolean enable) {
		executor.execute(() -> {
			if (this.peerConnection != null) {
				for (RtpTransceiver transceiver : this.getTransceivers()) {
					final MediaStreamTrack track = transceiver.getReceiver().track();
					if (track instanceof AudioTrack) {
						if (track.enabled() != enable) {
							if (enable) {
								logger.debug("Unmuting remote audio track");
							} else {
								logger.debug("Muting remote audio track");
							}
							track.setEnabled(enable);
						}
					}
				}
			}
		});
	}

	//endregion

	//region Offer / Answer / Candidates

	@AnyThread
	public void createOffer() {
		executor.execute(() -> {
			if (peerConnection != null && !isError) {
				logger.debug("createOffer()");
				isInitiator = true;
				peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
			} else {
				logger.debug("skipping createOffer()");
			}
		});
	}

	@AnyThread
	public void createAnswer() {
		executor.execute(() -> {
			if (peerConnection != null && !isError) {
				logger.debug("createAnswer()");
				isInitiator = false;
				peerConnection.createAnswer(sdpObserver, sdpMediaConstraints);
			} else {
				logger.debug("skipping createAnswer()");
			}
		});
	}

	@AnyThread
	public void addRemoteIceCandidate(final IceCandidate candidate) {
		executor.execute(() -> {
			if (peerConnection != null && !isError) {
				if (queuedRemoteCandidates != null) {
					logger.debug("Queueing remote candidate");
					queuedRemoteCandidates.add(candidate);
				} else {
					logger.debug("addRemoteIceCandidate()");
					peerConnection.addIceCandidate(candidate);
				}
			} else {
				logger.debug("skipping addRemoteIceCandidate()");
			}
		});
	}

	@AnyThread
	public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
		executor.execute(() -> {
			if (peerConnection == null || isError) {
				logger.debug("skipping removeRemoteIceCandidates()");
				return;
			}
			// Drain the queued remote candidates if there is any so that
			// they are processed in the proper order.
			drainCandidates();
			logger.debug("removeRemoteIceCandidates()");
			peerConnection.removeIceCandidates(candidates);
		});
	}

	@AnyThread
	public void setRemoteDescription(final SessionDescription sdp) {
		executor.execute(() -> {
			if (peerConnection == null || isError) {
				logger.debug("skipping setRemoteDescription()");
				return;
			}

			String sdpDescription = sdp.description;

			// Set codec preferences
			// TODO(ANDR-1109): Move this into SDPUtil!
			sdpDescription = preferCodec(this.logger, sdpDescription, AUDIO_CODEC_OPUS, true);
			try {
				sdpDescription = PeerConnectionClient.this.sdpPatcher
					.patch(SdpPatcher.Type.LOCAL_ANSWER_OR_REMOTE_SDP, sdpDescription);
			} catch (SdpPatcher.InvalidSdpException e) {
				this.reportError("Invalid remote SDP: " + e.getMessage(), e, true);
				return;
			} catch (IOException e) {
				this.reportError("Unable to patch remote SDP", e, true);
				return;
			}

			SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
			logger.debug("Set remote SDP from {}", sdpRemote.type.canonicalForm());
			logger.debug("SDP:\n{}", sdpRemote.description);
			peerConnection.setRemoteDescription(sdpObserver, sdpRemote);
		});
	}

	//endregion

	//region Error handling / reporting

	@AnyThread
	private void reportError(
		@NonNull final String errorMessage,
		boolean abortCall
	) {
		this.reportError(errorMessage, null, abortCall);
	}

	@AnyThread
	private void reportError(
		@NonNull final String errorMessage,
		@Nullable final Throwable t,
		boolean abortCall
	) {
		if (t != null) {
			logger.error("Error: " + errorMessage, t);
		} else {
			logger.error("Error: " + errorMessage);
		}
		executor.execute(() -> {
			if (events != null) {
				events.onError(callId, errorMessage, abortCall);
			}
			isError = true;
		});
	}

	//endregion

	//region Audio / video tracks

	/**
	 * Create and return a local audio track.
	 *
	 * The track will be stored in the `localAudioTrack` instance variable.
	 */
	private AudioTrack createAudioTrack() {
		logger.trace("createAudioTrack");
		this.audioSource = this.factory.createAudioSource(this.audioConstraints);
		this.localAudioTrack = this.factory.createAudioTrack(AUDIO_TRACK_ID, this.audioSource);
		this.localAudioTrack.setEnabled(this.enableLocalAudioTrack);
		return this.localAudioTrack;
	}

	/**
	 * Create and return a local video track.
	 *
	 * The track will be stored in the `localVideoTrack` instance variable.
	 *
	 * Note that the created video track does not yet require or start a camera capturer.
	 * It's an "empty" track initially.
	 */
	@Nullable
	private VideoTrack createVideoTrack() {
		// Check preconditions
		if (!this.isVideoCallEnabled()) {
			logger.error("Cannot create video track, isVideoCallEnabled() returns false");
			return null;
		}
		if (this.eglBaseContext == null) {
			logger.error("Cannot create video track, eglBaseContext is null");
			return null;
		}
		if (this.factory == null) {
			logger.error("Cannot create video track, factory is null");
			return null;
		}
		if (this.localVideoSink == null) {
			logger.error("Cannot create video track, local video sink is null");
			return null;
		}

		// Configuration
		boolean isScreencast = false; // Not yet supported

		// Create helpers and a video source
		this.surfaceTextureHelper = SurfaceTextureHelper.create(
			"VideoCaptureThread",
			this.eglBaseContext
		);
		this.videoSource = this.factory.createVideoSource(isScreencast);
		if (this.videoSource == null) {
			logger.error("Could not create video source");
			return null;
		}

		// Create local video track
		logger.trace("Creating local video track");
		this.localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, this.videoSource);
		if (this.localVideoTrack == null) {
			logger.error("Could not create local video track");
			return null;
		}
		this.localVideoTrack.setEnabled(this.renderVideo);
		logger.trace("Adding sink to local video track: {}", this.localVideoSink);
		this.localVideoTrack.addSink(this.localVideoSink);
		return this.localVideoTrack;
	}

	/**
	 * Return the remote VideoTrack, assuming there is only one.
	 */
	@Nullable
	private VideoTrack getRemoteVideoTrack() {
		for (RtpTransceiver transceiver : this.getTransceivers()) {
			final MediaStreamTrack track = transceiver.getReceiver().track();
			if (track instanceof VideoTrack) {
				return (VideoTrack) track;
			}
		}
		return null;
	}

	/**
	 * Get list of transceivers.
	 *
	 * WARNING: ALWAYS use this method to access transceivers! Otherwise,
	 *          audio/video will be lost! DO NOT use getSenders/getReceivers,
	 *          either! See ANDR-1119 for an explanation.
	 */
	public @NonNull List<RtpTransceiver> getTransceivers() {
		if (this.peerConnection == null) {
			return Collections.emptyList();
		}

		// Permanent workaround for ANDR-1119
		if (this.cachedRtpTransceivers == null) {
			this.cachedRtpTransceivers = this.peerConnection.getTransceivers();
		}
		return this.cachedRtpTransceivers;
	}

	//endregion

	/**
	 * Returns the line number containing "m=audio|video", or -1 if no such line exists.
	 */
	private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
		final String mediaDescription = isAudio ? "m=audio " : "m=video ";
		for (int i = 0; i < sdpLines.length; ++i) {
			if (sdpLines[i].startsWith(mediaDescription)) {
				return i;
			}
		}
		return -1;
	}

	private static String joinString(
			Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
		Iterator<? extends CharSequence> iter = s.iterator();
		if (!iter.hasNext()) {
			return "";
		}
		StringBuilder buffer = new StringBuilder(iter.next());
		while (iter.hasNext()) {
			buffer.append(delimiter).append(iter.next());
		}
		if (delimiterAtEnd) {
			buffer.append(delimiter);
		}
		return buffer.toString();
	}

	private static String movePayloadTypesToFront(
		@NonNull Logger logger,
		List<String> preferredPayloadTypes,
		String mLine
	) {
		// The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
		final List<String> origLineParts = Arrays.asList(mLine.split(" "));
		if (origLineParts.size() <= 3) {
			logger.error("Wrong SDP media description format: {}", mLine);
			return null;
		}
		final List<String> header = origLineParts.subList(0, 3);
		final List<String> unpreferredPayloadTypes = new ArrayList<>(origLineParts.subList(3, origLineParts.size()));
		unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
		// Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
		// types.
		final List<String> newLineParts = new ArrayList<>();
		newLineParts.addAll(header);
		newLineParts.addAll(preferredPayloadTypes);
		newLineParts.addAll(unpreferredPayloadTypes);
		return joinString(newLineParts, " ", false /* delimiterAtEnd */);
	}

	private static String preferCodec(
		@NonNull Logger logger,
		String sdpDescription,
		String codec,
		boolean isAudio
	) {
		final String[] lines = sdpDescription.split("\r\n");
		final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
		if (mLineIndex == -1) {
			logger.warn("Warning: No mediaDescription line, so can't prefer {}", codec);
			return sdpDescription;
		}
		// A list with all the payload types with name |codec|. The payload types are integers in the
		// range 96-127, but they are stored as strings here.
		final List<String> codecPayloadTypes = new ArrayList<>();
		// a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
		final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
		for (String line : lines) {
			Matcher codecMatcher = codecPattern.matcher(line);
			if (codecMatcher.matches()) {
				codecPayloadTypes.add(codecMatcher.group(1));
			}
		}
		if (codecPayloadTypes.isEmpty()) {
			logger.warn("Warning: No payload types with name {}", codec);
			return sdpDescription;
		}

		final String newMLine = movePayloadTypesToFront(logger, codecPayloadTypes, lines[mLineIndex]);
		if (newMLine == null) {
			return sdpDescription;
		}
		logger.warn("Change media description from {} to {}", lines[mLineIndex], newMLine);
		lines[mLineIndex] = newMLine;
		return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
	}

	private void drainCandidates() {
		logger.trace("drainCandidates()");
		if (queuedRemoteCandidates != null) {
			logger.debug("Add {} remote candidates", queuedRemoteCandidates.size());
			for (IceCandidate candidate : queuedRemoteCandidates) {
				peerConnection.addIceCandidate(candidate);
			}
			queuedRemoteCandidates = null;
		}
	}

	// Implementation detail: observe ICE & stream changes and react accordingly.
	private class PCObserver implements PeerConnection.Observer {
		@NonNull private Set<String> relatedAddresses = new HashSet<>();

		@Override
		public void onIceCandidate(final IceCandidate candidate) {
			logger.info("New local ICE candidate: {}", candidate.sdp);

			// Discard loopback candidates
			if (SdpUtil.isLoopbackCandidate(candidate.sdp)) {
				logger.info("Ignoring local ICE candidate (loopback): {}", candidate.sdp);
				return;
			}

			// Discard IPv6 candidates if disabled
			if (!PeerConnectionClient.this.peerConnectionParameters.allowIpv6 && SdpUtil.isIpv6Candidate(candidate.sdp)) {
				logger.info("Ignoring local ICE candidate (ipv6_disabled): {}", candidate.sdp);
				return;
			}

			// Discard relay candidates with the same related address.
			// Note: It's hard to do reduce these further since gathering may at any time
			//       yield additional relay candidates due to an interface change.
			// Important: This only works as long as we don't do ICE restarts and don't add further transports!
			final String relatedAddress = SdpUtil.getRelatedAddress(candidate.sdp);
			if (relatedAddress != null && !relatedAddress.equals("0.0.0.0")) {
				if (this.relatedAddresses.contains(relatedAddress)) {
					logger.info("Ignoring local ICE candidate (duplicate_related_addr {}): {}", relatedAddress, candidate.sdp);
					return;
				} else {
					this.relatedAddresses.add(relatedAddress);
				}
			}

			// Dispatch event
			executor.execute(() -> events.onIceCandidate(callId, candidate));
		}

		@Override
		public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
			if (logger.isInfoEnabled()) {
				logger.info("Ignoring removed candidates: {}", Arrays.toString(candidates));
			}
		}

		@Override
		public void onSignalingChange(PeerConnection.SignalingState newState) {
			logger.info("Signaling state change to {}", newState);
		}

		@Override
		public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
			executor.execute(() -> {
				logger.info("ICE connection state change to {}", newState);
				if (newState == IceConnectionState.CHECKING) {
					events.onIceChecking(callId);
				} else if (newState == IceConnectionState.CONNECTED) {
					if (iceFailedFuture != null) {
						// Note: Because the iceFailedFuture is also scheduled on the same executor
						// as this code, it should not be possible that the scheduled task is
						// already running.
						iceFailedFuture.cancel(false);
						logger.info("iceFailedFuture: Cancelled (connected)");
						iceFailedFuture = null;
					}
					events.onIceConnected(callId);
				} else if (newState == IceConnectionState.DISCONNECTED) {
					events.onIceDisconnected(callId);
				} else if (newState == IceConnectionState.FAILED) {
					logger.warn("IceConnectionState changed to FAILED");
					// Note: LibWebRTC has a bug where FAILED is not a terminal state. Sometimes
					// the IceConnectionState changes to FAILED before all candidates have been
					// received, and then changes to CONNECTED once the connection has been
					// established a few ms later. See ANDR-1079 and CRBUG 935905 for more details.
					// As a workaround, we only fire `onIceFailed` if the state does not switch
					// to CONNECTED within 15s after setting the remote description.
					long minimalWaitingTimeSeconds = 15;
					if (setRemoteDescriptionNanotime == null) {
						// This should not happen
						logger.error("createOfferAnswerNanotime is null in onIceConnectionState");
						events.onIceFailed(callId);
					} else {
						// Elapsed nanoseconds since the remote description was set
						final long elapsedNs = System.nanoTime() - setRemoteDescriptionNanotime;
						// Max waiting time in nanoseconds
						final long waitingTimeNs = minimalWaitingTimeSeconds * 1000000000L;

						if (elapsedNs > waitingTimeNs) {
							// Minimal waiting time already exceeded, trigger event immediately
							events.onIceFailed(callId);
						} else {
							// Less than 15s since remote description was set. Schedule the call to
							// events.onIceFailed unless it's already scheduled.
							if (iceFailedFuture == null) {
								final long remainingNs = waitingTimeNs - elapsedNs;
								logger.info("iceFailedFuture: Delaying onIceFailed call, {} ms remaining", remainingNs / 1000000);
								//noinspection Convert2Lambda
								iceFailedFuture = executor.schedule(new Runnable() {
									@Override
									@AnyThread
									public void run() {
										logger.info("iceFailedFuture: Time's up, calling onIceFailed");
										events.onIceFailed(callId);
									}
								}, remainingNs, TimeUnit.NANOSECONDS);
							}
						}
					}
				}
			});
		}

		@Override
		public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
			logger.info("ICE gathering state change to {}", newState);
			events.onIceGatheringStateChange(callId, newState);
		}

		@Override
		public void onIceConnectionReceivingChange(boolean receiving) {
			logger.info("ICe connection receiving state change to {}", receiving);
		}

		@Override
		public void onAddStream(final MediaStream stream) {
			logger.warn("Warning: onAddStream (even though we use unified plan)");
		}

		@Override
		public void onRemoveStream(final MediaStream stream) {
			logger.warn("Warning: onRemoveStream (even though we use unified plan)");
		}

		@Override
		public void onDataChannel(final DataChannel dc) {
			try {
				logger.warn("New unexpected data channel: {} (id={})", dc.label(), dc.id());
			} catch (IllegalStateException e) {
				logger.error("New unexpected data channel (could not fetch information)", e);
			}
		}

		@Override
		public void onRenegotiationNeeded() {
			logger.info("Renegotiation needed");
		}

		@Override
		public void onAddTrack(final RtpReceiver receiver, final MediaStream[] mediaStreams) {
			logger.debug("onAddTrack");
		}

		@Override
		public void onTrack(RtpTransceiver transceiver) {
			logger.debug("onTrack");
		}
	}

	// Implementation detail: handle offer creation/signaling and answer setting,
	// as well as adding remote ICE candidates once the answer SDP is set.
	private class SDPObserver implements SdpObserver {
		@Override
		public void onCreateSuccess(final SessionDescription origSdp) {
			if (localSdp != null) {
				// Note: Will probably get resolved by SE-49.
				// Once SE-49 is implemented, this can be converted to an "abortCall" call
				// (with showErrorNotification=true).
				logger.error("onCreateSuccess while localSdp is not null");
				return;
			}
			final String sdpDescription;
			final SdpPatcher.Type sdpPatcherType =
				isInitiator ?
					SdpPatcher.Type.LOCAL_OFFER :
					SdpPatcher.Type.LOCAL_ANSWER_OR_REMOTE_SDP;
			try {
				sdpDescription = PeerConnectionClient.this.sdpPatcher
					.patch(sdpPatcherType, origSdp.description);
			} catch (SdpPatcher.InvalidSdpException e) {
				reportError("Invalid remote SDP: " + e.getMessage(), e, true);
				return;
			} catch (IOException e) {
				reportError("Unable to patch remote SDP", e, true);
				return;
			}
			final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
			localSdp = sdp;
			executor.execute(() -> {
				if (peerConnection != null && !isError) {
					logger.debug("Set local SDP from {}", sdp.type.canonicalForm());
					logger.debug("SDP:\n{}", sdp.description);
					peerConnection.setLocalDescription(sdpObserver, sdp);
				}
			});
		}

		@Override
		public void onSetSuccess() {
			executor.execute(() -> {
				if (peerConnection == null || isError) {
					return;
				}
				if (isInitiator) {
					// For offering peer connection we first create offer and set
					// local SDP, then after receiving answer set remote SDP.
					if (peerConnection.getRemoteDescription() == null) {
						// We've just set our local SDP so time to send it.
						logger.info("Local SDP set succesfully");
						if (events != null) {
							events.onLocalDescription(callId, localSdp);
						}
					} else {
						// We've just set remote description, so drain remote
						// and send local ICE candidates.
						logger.info("Remote SDP set succesfully");
						setRemoteDescriptionNanotime = System.nanoTime();
						if (events != null) {
							events.onRemoteDescriptionSet(callId);
						}
						drainCandidates();
					}
				} else {
					// For answering peer connection we set remote SDP and then
					// create answer and set local SDP.
					if (peerConnection.getLocalDescription() != null) {
						// We've just set our local SDP so time to send it, drain
						// remote and send local ICE candidates.
						logger.info("Local SDP set succesfully");
						if (events != null) {
							events.onLocalDescription(callId, localSdp);
						}
						drainCandidates();
					} else {
						// We've just set remote SDP - do nothing for now -
						// answer will be created soon.
						logger.info("Remote SDP set succesfully");
						setRemoteDescriptionNanotime = System.nanoTime();
						if (events != null) {
							events.onRemoteDescriptionSet(callId);
						}
					}
				}
			});
		}

		@Override
		public void onCreateFailure(final String error) {
			reportError("SDP onCreateFailure: " + error, true);
		}

		@Override
		public void onSetFailure(final String error) {
			// Note: I assume the "called in wrong state" error happens if an offer/answer is set
			// while a call is already established. This should get resolved by SE-49.
			logger.warn("onSetFailure: " + error);
			if (error != null && error.contains("Called in wrong state: kStable")) {
				reportError("SDP onSetFailure: " + error, false);
			}
		}
	}

	private class DCObserver extends DataChannelObserver {
		private final @NonNull Logger logger = LoggerFactory.getLogger("SignalingDataChannel");
		final @NonNull CompletableFuture<?> openFuture = new CompletableFuture<>();

		@Override
		public void onBufferedAmountChange(long l) {
			logger.debug("onBufferedAmountChange: {}", l);
			final UnboundedFlowControlledDataChannel ufcdc = PeerConnectionClient.this.signalingDataChannel;
			if (ufcdc == null) {
				logger.warn("onBufferedAmountChange, but signalingDataChannel is null");
				return;
			}

			// Forward buffered amount to flow control
			// Important: ALWAYS dispatch this event to another thread because webrtc.org!
			RuntimeUtil.runInAsyncTask(ufcdc::bufferedAmountChange);
		}

		@Override
		public synchronized void onStateChange(@NonNull DataChannel.State state) {
			logger.debug("onStateChange: {}", state);
			switch (state) {
				case CONNECTING:
					// Ignore
					break;
				case OPEN:
					logger.info("Data channel is open");
					if (signalingDataChannel != null) {
						this.openFuture.complete(null);
					} else {
						logger.error("onStateChange: data channel is null!");
					}
					break;
				case CLOSING:
					logger.info("Data channel is closing");
					break;
				case CLOSED:
					logger.info("Data channel is closed");
					break;
			}
		}

		@Override
		public synchronized void onMessage(DataChannel.Buffer buffer) {
			logger.debug("Received message ({} bytes)", buffer.data.remaining());
			if (!buffer.binary) {
				logger.warn("Received non-binary data channel message, discarding");
				return;
			}

			// Copy the message since the ByteBuffer will be reused immediately
			final ByteBuffer copy = ByteBuffer.allocate(buffer.data.remaining());
			copy.put(buffer.data);
			copy.flip();

			// Notify event listener asychronously
			RuntimeUtil.runInAsyncTask(() -> {
				try {
					final @NonNull CallSignaling.Envelope envelope = CallSignaling.Envelope.parseFrom(copy);
					if (events != null) {
						events.onSignalingMessage(callId, envelope);
					}
				} catch (InvalidProtocolBufferException e) {
					logger.warn("Could not parse incoming signaling message", e);
				}
			});
		}
	}

	//region Video capturing

	/**
	 * Create a video capturer. Return whether the action succeeded.
	 *
	 * @locks {@link #capturingLock}
	 */
	@AnyThread
	private boolean setupCapturer() {
		logger.info("Set up capturer");

		synchronized (this.capturingLock) {
			// Create video capturer
			this.videoCapturer = VideoCapturerUtil.createVideoCapturer(
				this.appContext,
				new CameraEventsHandler()
			);
			if (this.videoCapturer == null) {
				logger.error("Could not create camera video capturer");
				return false;
			}
			logger.info("Video capturer created");

			// Initialize capturer
			if (this.videoSource == null) {
				logger.error("Could not start capturing, video source is null");
				return false;
			}
			this.videoCapturer.initialize(
				this.surfaceTextureHelper,
				this.appContext,
				this.videoSource.getCapturerObserver()
			);
			return true;
		}
	}

	/**
	 * Create a video capturer (if necessary) and start capturing.
	 *
	 * Return VideoCapturer on success, null otherwise.
	 *
	 * Note: WebRTC's CameraCapturer may block so it's better to call this method from a worker thread
	 *
	 * @locks {@link #capturingLock}
	 */
	@WorkerThread
	public @Nullable VideoCapturer startCapturing(@Nullable VoipVideoParams params) {
		logger.info("Start capturing");

		synchronized (this.capturingLock) {
			// Initialize capturer
			if (this.videoCapturer == null) {
				if (!setupCapturer()) {
					return null;
				}
			}

			// Start capturing
			try {
				if (params != null) {
					this.videoCapturer.startCapture(params.getMaxWidth(), params.getMaxHeight(), params.getMaxFps());
				} else {
					this.videoCapturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS);
				}
			} catch (RuntimeException e) {
				this.videoCapturer = null;
				if (!setupCapturer()) {
					return null;
				}
				// try again after setting up capturer again
				try {
					if (params != null) {
						this.videoCapturer.startCapture(params.getMaxWidth(), params.getMaxHeight(), params.getMaxFps());
					} else {
						this.videoCapturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS);
					}
				} catch (RuntimeException ignored) {
					this.videoCapturer = null;
					return null;
				}
			}
		}

		// Notify peer
		if (this.videoCapturer != null) {
			this.sendSignalingMessage(CaptureState.camera(true));
		}

		// Return capturer
		return this.videoCapturer;
	}

	/**
	 * Change the capture format on the fly.
	 * This will do nothing if no video capturer is set up, or if it wasn't started.
	 *
	 * Note: WebRTC's CameraCapturer may block so it's better to call this method from a worker thread
	 *
	 * @locks {@link #capturingLock}
	 */
	@WorkerThread
	private void changeCapturingFormat(int width, int height, int fps) {
		logger.debug("Change capturing format");
		synchronized (this.capturingLock) {
			if (this.videoCapturer != null && this.videoCapturer.isCapturing()) {
				// WARNING: If the capturer is not started, this will implicitly start it!
				this.videoCapturer.changeCaptureFormat(width, height, fps);
			} else {
				logger.debug("Ignoring capturing format change, not currently capturing");
			}
		}
	}

	/**
	 * Stop capturing asynchronously.
	 *
	 * Note: WebRTC's CameraCapturer may block so it's better to call this method from a worker thread
	 *
	 * @locks {@link #capturingLock}
	 */
	@WorkerThread
	public void stopCapturing() {
		logger.info("Stop capturing");
		synchronized (this.capturingLock) {
			if (this.videoCapturer != null) {
				try {
					this.videoCapturer.stopCapture();
					logger.info("Stopped capturing");
				} catch (InterruptedException e) {
					logger.error("Interrupted while stopping video capturer", e);
					Thread.currentThread().interrupt();
					return;
				}
			} else {
				logger.warn("stopCapturing: Video capturer is null");
			}
		}

		// Notify peer
		this.sendSignalingMessage(CaptureState.camera(false));
	}

	/**
	 * Change the outgoing video parameters by setting the appropriate RTP parameters.
	 */
	@AnyThread
	public void changeOutgoingVideoParams(@NonNull VoipVideoParams params) {
		logger.info("Changing outgoing video params to {}.", params);
		executor.execute(
			() -> {
				// Adjust capturer
				this.changeCapturingFormat(params.getMaxWidth(), params.getMaxHeight(), params.getMaxFps());
				// Adjust encoder
				this.setOutgoingVideoEncoderLimits(params.getMaxBitrateKbps() * 1000, params.getMaxFps());
			}
		);
	}

	class CameraEventsHandler implements CameraVideoCapturer.CameraEventsHandler {
		@Override
		public void onCameraError(String s) {
			logger.error("Camera error: {}", s);
			final String msg = appContext.getString(R.string.msg_camera_framework_bug);
			RuntimeUtil.runOnUiThread(() -> SingleToast.getInstance().showBottom(msg, Toast.LENGTH_LONG));
		}

		@Override
		public void onCameraDisconnected() {
			logger.debug("Camera disconnected");
		}

		@Override
		public void onCameraFreezed(String s) {
			logger.error("Camera frozen: {}", s);
		}

		@Override
		public void onCameraOpening(String s) {
			logger.info("Camera opening: {}", s);
		}

		@Override
		public void onFirstFrameAvailable() {
			logger.debug("Camera first frame available");
			events.onCameraFirstFrameAvailable();
		}

		@Override
		public void onCameraClosed() {
			logger.debug("Camera closed");
		}
	}

	//endregion

	//region Signaling data channel

	/**
	 * Enqueue a signaling message for sending it through the signaling data channel
	 * once it's open and ready to send.
	 */
	@AnyThread
	public void sendSignalingMessage(@NonNull ToSignalingMessage message) {
		if (this.signalingDataChannel == null) {
			logger.warn("queueSignalingMessage: Data channel is null");
			return;
		}
		final ByteBuffer buffer = message.toSignalingMessageByteBuffer();
		logger.debug("Enqueuing signaling message: ({}, {} bytes)", message, buffer.remaining());
		this.signalingDataChannel.write(new DataChannel.Buffer(buffer, true));
	}

	//endregion
}

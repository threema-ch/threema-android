/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.app.webclient.webrtc;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.saltyrtc.client.exceptions.ConnectionException;
import org.saltyrtc.tasks.webrtc.WebRTCTask;
import org.saltyrtc.tasks.webrtc.messages.Answer;
import org.saltyrtc.tasks.webrtc.messages.Candidate;
import org.saltyrtc.tasks.webrtc.messages.Offer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import ch.threema.annotation.SameThread;
import ch.threema.app.voip.util.SdpUtil;
import ch.threema.app.utils.WebRTCUtil;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Config;
import ch.threema.app.webclient.listeners.PeerConnectionListener;
import ch.threema.app.webclient.state.PeerConnectionState;
import ch.threema.client.APIConnector;
import ch.threema.logging.ThreemaLogger;
import java8.util.concurrent.CompletableFuture;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

/**
 * Wrapper around the WebRTC PeerConnection.
 *
 * This handles everything from creating the peer connection
 * to destroying it afterwards.
 */
@SameThread
public class PeerConnectionWrapper {
	private static final String THREEMA_DC_LABEL = "THREEMA";

	// Logger
	private final Logger logger = LoggerFactory.getLogger(PeerConnectionWrapper.class);

	// Worker thread handler
	@NonNull private final HandlerExecutor handler;

	// WebRTC / SaltyRTC
	@NonNull private final PeerConnectionFactory factory;
	@NonNull private final org.webrtc.PeerConnection pc;
	@NonNull private final WebRTCTask task;
	private final boolean allowIpv6;

	// State
	@NonNull private CompletableFuture<Void> readyToSetRemoteDescription = new CompletableFuture<>();
	@NonNull private CompletableFuture<Void> readyToAddRemoteCandidates = new CompletableFuture<>();
	@NonNull private CompletableFuture<Void> readyToSendLocalCandidates = new CompletableFuture<>();
	@NonNull private PeerConnectionState state = PeerConnectionState.NEW;
	private boolean disposed = false;

	// Listener
	@NonNull private final PeerConnectionListener listener;

	/**
	 * Return a PeerConnectionFactory instance used for Threema Web.
	 */
	public static PeerConnectionFactory getPeerConnectionFactory() {
		return PeerConnectionFactory
			.builder()
			.createPeerConnectionFactory();
	}

	/**
	 * Return the RTCConfiguration used for Threema Web.
	 */
	public static PeerConnection.RTCConfiguration getRTCConfiguration(@NonNull final Logger logger) throws Exception {
		// Set ICE servers
		final List<org.webrtc.PeerConnection.IceServer> iceServers = new ArrayList<>();
		final APIConnector.TurnServerInfo turnServerInfo = Config.getTurnServerCache().getTurnServers();
		final List<String> turnServers = Arrays.asList(turnServerInfo.turnUrls);
		StreamSupport.stream(turnServers)
			.map(server -> PeerConnection.IceServer.builder(server)
				.setUsername(turnServerInfo.turnUsername)
				.setPassword(turnServerInfo.turnPassword)
				.createIceServer())
			.forEach(iceServers::add);
		logger.debug("Using ICE servers: {}", turnServers);

		// Set up RTC configuration
		final PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
		rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
		rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

		return rtcConfig;
	}

	/**
	 * Initialize a peer connection.
	 */
	public PeerConnectionWrapper(
		@NonNull final String logPrefix,
		@NonNull final Context appContext,
		@NonNull final HandlerExecutor handler,
		@NonNull final WebRTCTask task,
		@NonNull final TemporaryTaskEventHandler temporaryTaskEventHandler,
		final boolean allowIpv6,
		@NonNull final PeerConnectionListener listener
	) throws Exception {
		// Set logger prefix
		if (logger instanceof ThreemaLogger) {
			((ThreemaLogger) logger).setPrefix(logPrefix);
		}
		logger.info("Initialize WebRTC PeerConnection");

		// Initialise WebRTC for Android
		WebRTCUtil.initializeAndroidGlobals(appContext);
		this.factory = getPeerConnectionFactory();

		// Store handler, listener, task and set message handler
		this.handler = handler;
		this.listener = listener;
		this.task = task;
		temporaryTaskEventHandler.replace(this.task, new TaskMessageHandler());
		this.allowIpv6 = allowIpv6;

		// Create peer connection
		final PeerConnection peerConnection = factory.createPeerConnection(
			getRTCConfiguration(logger),
			new PeerConnectionObserver()
		);
        if (peerConnection == null) {
            throw new RuntimeException("Could not create peer connection: createPeerConnection returned null");
        }
        this.pc = peerConnection;
	}

	/**
	 * If the instance is disposed, throw an exception.
	 */
	private void ensureNotDisposed() {
		if (this.disposed) {
			throw new IllegalStateException("PeerConnection is disposed");
		}
	}

	/**
	 * Handler for incoming task messages.
	 */
	@AnyThread
	private class TaskMessageHandler implements org.saltyrtc.tasks.webrtc.events.MessageHandler {
		@Override
		public void onOffer(@NonNull final Offer offer) {
			PeerConnectionWrapper.this.readyToSetRemoteDescription.thenRunAsync(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					if (PeerConnectionWrapper.this.disposed) {
						logger.warn("Ignoring offer, peer connection already disposed");
					} else {
						PeerConnectionWrapper.this.onOfferReceived(offer);
					}
				}
			}, PeerConnectionWrapper.this.handler.getExecutor());
		}

		@Override
		public void onAnswer(@NonNull final Answer answer) {
			logger.warn("Ignoring answer");
		}

		@Override
		public void onCandidates(@NonNull final Candidate[] candidates) {
			PeerConnectionWrapper.this.readyToAddRemoteCandidates.thenRunAsync(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					if (PeerConnectionWrapper.this.disposed) {
						logger.warn("Ignoring candidates, peer connection already disposed");
					} else {
						PeerConnectionWrapper.this.onIceCandidatesReceived(candidates);
					}
				}
			}, PeerConnectionWrapper.this.handler.getExecutor());
		}
	}

	/**
	 * A WebRTC offer was received. Set the remote description.
	 */
	@AnyThread
	private void onOfferReceived(@NonNull final Offer offer) {
		logger.info("Offer received, applying as remote description");
		this.pc.setRemoteDescription(new SdpObserver() {
			@Override
			@AnyThread
			public void onCreateSuccess(@NonNull final SessionDescription description) {
				// Unreachable
			}

			@Override
			@AnyThread
			public void onCreateFailure(@NonNull final String error) {
				// Unreachable
			}

			@Override
			@AnyThread
			public void onSetSuccess() {
				PeerConnectionWrapper.this.onRemoteDescriptionSet();
			}

			@Override
			@AnyThread
			public void onSetFailure(@NonNull final String error) {
				logger.error("Could not apply remote description: {}", error);
			}
		}, new SessionDescription(SessionDescription.Type.OFFER, offer.getSdp()));
	}

	/**
	 * The remote description was set. Create and send an answer.
	 */
	@AnyThread
	private void onRemoteDescriptionSet() {
		logger.info("Remote description applied successfully, creating answer");
		this.pc.createAnswer(new SdpObserver() {
			@Nullable private SessionDescription description;

			@Override
			@AnyThread
			public synchronized void onCreateSuccess(@NonNull final SessionDescription description) {
				logger.info("Created answer");
				this.description = description;
				PeerConnectionWrapper.this.pc.setLocalDescription(this, description);
			}

			@Override
			@AnyThread
			public void onCreateFailure(@NonNull final String error) {
				logger.error("Could not create answer: {}", error);
			}

			@Override
			@AnyThread
			public synchronized void onSetSuccess() {
				logger.info("Local description applied successfully, sending answer");
				final Answer answer = new Answer(Objects.requireNonNull(this.description).description);
				PeerConnectionWrapper.this.handler.post(new Runnable() {
					@Override
					@WorkerThread
					public void run() {
						logger.debug("Sending answer");
						try {
							// Send the answer
							PeerConnectionWrapper.this.task.sendAnswer(answer);

							// Signal that local ICE candidates may be sent now
							PeerConnectionWrapper.this.readyToSendLocalCandidates.complete(null);
						} catch (ConnectionException error) {
							logger.error("Could not send answer", error);
						}
					}
				});
			}

			@Override
			@AnyThread
			public void onSetFailure(@NonNull final String error) {
				logger.error("Could not set local description: {}", error);
			}
		}, new MediaConstraints());

		// Signal that remote ICE candidates may be added now (delayed to rule
		// out weird state bugs in libwebrtc)
		PeerConnectionWrapper.this.handler.post(new Runnable() {
			@Override
			@WorkerThread
			public void run() {
				PeerConnectionWrapper.this.readyToAddRemoteCandidates.complete(null);
			}
		});
	}

	/**
	 * One or more ICE candidates were received. Add them.
	 */
	@AnyThread
	private void onIceCandidatesReceived(@NonNull final Candidate[] candidates) {
		int added = 0;
		for (Candidate candidate : candidates) {
			// Ignore without m-line
			if (candidate.getSdpMLineIndex() == null) {
				logger.warn(
					"Received candidate without SdpMLineIndex, ignoring: {}",
					candidate.getSdp()
				);
				continue;
			}

			// Ignore candidates with empty SDP
			if (candidate.getSdp() == null || candidate.getSdp().trim().equals("")) {
				logger.warn("Received candidate with empty SDP, ignoring");
				continue;
			}

			// Ignore IPv6 (if requested)
			if (!this.allowIpv6 && SdpUtil.isIpv6Candidate(candidate.getSdp())) {
				logger.info("Ignoring IPv6 candidate due to settings: {}", candidate.getSdp());
				continue;
			}

			// Add candidate
			logger.info("Adding peer ICE candidate: {}", candidate.getSdp());
			this.pc.addIceCandidate(new IceCandidate(
				candidate.getSdpMid(), candidate.getSdpMLineIndex(), candidate.getSdp()
			));
			added++;
		}
		logger.info("Added {} ICE candidate(s) from peer", added);
		if (added < candidates.length) {
			logger.info("Ignored {} remote candidate(s) from peer", candidates.length - added);
		}
	}

	/**
	 * Return the wrapped PeerConnection.
	 */
	public org.webrtc.PeerConnection getPeerConnection() {
		this.ensureNotDisposed();
		return this.pc;
	}

	/**
	 * Return the peer connection state.
	 */
	@NonNull public synchronized PeerConnectionState getState() {
		return this.state;
	}

	/**
	 * Set the peer connection state and notify listeners.
	 */
	@AnyThread
	private synchronized void setState(@NonNull final PeerConnectionState state) {
		final PeerConnectionState current = this.state;
		if (this.disposed) {
			logger.warn("PeerConnection is disposed, ignoring state change from {} to {}", current, state);
			return;
		}

		// Update state
		this.state = state;
		logger.info("PeerConnectionState changed to {}", state);

		// Fire state event
		this.listener.onStateChanged(current, state);
	}

	/**
	 * Close the peer connection and dispose allocated resources.
	 *
	 * This results in a terminal state. After calling this method,
	 * the instance MUST not be used anymore.
	 */
	public void dispose() {
		logger.info("dispose()");
		if (this.disposed) {
			logger.warn("Not disposing: Already disposed");
			return;
		}

		synchronized(this) {
			// Mark this instance as disposed
			this.disposed = true;
		}

		// Close and dispose peer connection.
		// (The `dispose()` method implicitly calls `close()`)
		logger.trace("Closing peer connection");
		pc.close();
		logger.trace("Disposing peer connection");
		pc.dispose();
		logger.trace("Disposed peer connection");

		// Dispose the peer connection factory.
		logger.trace("Disposing factory");
		factory.dispose();
		logger.trace("Disposed factory");

		logger.info("All native resources disposed");

		synchronized(this) {
			// Set state to CLOSED
			this.state = PeerConnectionState.CLOSED;

			// Fire state event
			this.listener.onStateChanged(this.state, PeerConnectionState.CLOSED);
		}
	}

	private class PeerConnectionObserver implements org.webrtc.PeerConnection.Observer {
		@Override
		@AnyThread
		public void onSignalingChange(@NonNull final org.webrtc.PeerConnection.SignalingState state) {
			logger.info("Signaling state change to {}", state.name());
		}

		@Override
		@AnyThread
		public void onIceConnectionChange(@NonNull final IceConnectionState state) {
			logger.info("ICE connection state change to {}", state.name());
			switch (state) {
				case NEW:
					PeerConnectionWrapper.this.setState(PeerConnectionState.NEW);
					break;
				case CHECKING:
				case DISCONNECTED:
					PeerConnectionWrapper.this.setState(PeerConnectionState.CONNECTING);
					break;
				case CONNECTED:
				case COMPLETED:
					PeerConnectionWrapper.this.setState(PeerConnectionState.CONNECTED);
					break;
				case FAILED:
					PeerConnectionWrapper.this.setState(PeerConnectionState.FAILED);
					PeerConnectionWrapper.this.logStatus();
					break;
				case CLOSED:
					PeerConnectionWrapper.this.setState(PeerConnectionState.CLOSED);
					break;
				default:
					logger.error("Unknown ICE connection state: {}", state);
			}
		}

		@Override
		@AnyThread
		public void onIceConnectionReceivingChange(final boolean noIdeaWhatThisIs) {}

		@Override
		@AnyThread
		public void onIceGatheringChange(@NonNull final IceGatheringState state) {
			logger.info("ICE gathering state change to {}", state.name());
		}

		/**
		 * A new ICE candidate was generated. Send it to the peer.
		 */
		@Override
		@AnyThread
		public void onIceCandidate(@NonNull final IceCandidate candidate) {
			logger.info("New local ICE candidate: {}", candidate.sdp);

			// Check if loopback
			if (SdpUtil.isLoopbackCandidate(candidate.sdp)) {
				logger.info("Ignored local loopback candidate");
				return;
			}

			// Check if IPv6
			if (!allowIpv6 && SdpUtil.isIpv6Candidate(candidate.sdp)) {
				logger.info("Ignored local IPv6 candidate (disabled via preferences)");
				return;
			}

			// Send candidate when ready
			PeerConnectionWrapper.this.readyToSendLocalCandidates.thenRunAsync(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					logger.debug("Sending ICE candidate");
					try {
						final Candidate[] candidates = new Candidate[] {
							new Candidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex),
						};
						PeerConnectionWrapper.this.task.sendCandidates(candidates);
					} catch (ConnectionException error) {
						logger.error("Could not send ICE candidate", error);
					}
				}
			}, PeerConnectionWrapper.this.handler.getExecutor());
		}

		@Override
		@AnyThread
		public void onIceCandidatesRemoved(@NonNull final IceCandidate[] iceCandidates) {
			// Legacy nonsense
			if (logger.isInfoEnabled()) {
				logger.info("Ignoring removed candidates: {}", Arrays.toString(iceCandidates));
			}
		}

		@Override
		@AnyThread
		public void onRenegotiationNeeded() {
			logger.info("Negotiation needed");
			PeerConnectionWrapper.this.setState(PeerConnectionState.CONNECTING);

			// Signal that a remote description may now be safely set (delayed to rule
			// out weird state bugs in libwebrtc)
			PeerConnectionWrapper.this.handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					PeerConnectionWrapper.this.readyToSetRemoteDescription.complete(null);
				}
			});
		}

		@Override
		@AnyThread
		public void onAddTrack(@NonNull final RtpReceiver rtpReceiver, @NonNull final MediaStream[] mediaStreams) {
			logger.error("onAddTrack (in web client)");
		}

		@Override
		@AnyThread
		public void onAddStream(@NonNull final MediaStream mediaStream) {
			logger.error("onAddStream (in web client)");
		}

		@Override
		@AnyThread
		public void onRemoveStream(@NonNull final MediaStream mediaStream) {
			logger.error("onRemoveStream (in web client)");
		}

		@Override
		@AnyThread
		public void onDataChannel(@NonNull final DataChannel dc) {
			final String label = dc.label();
			logger.info("New data channel: {}", label);

			if (!THREEMA_DC_LABEL.equals(label)) {
				logger.warn("Ignoring new data channel (wrong label).");
				return;
			}

			// Fire data channel event
			PeerConnectionWrapper.this.listener.onDataChannel(dc);
		}
	}

	@AnyThread
	public long getMaxMessageSize() {
		// Sigh... still not supported by libwebrtc, so fallback to a
		// well-known (and, frankly, terribly small) value.
		return 64 * 1024;
	}

	/**
	 * Log connection status info to the Android log.
	 */
	@AnyThread
	private synchronized void logStatus() {
		logger.debug("*** CONNECTION STATUS");
		logger.debug("Aggregated state: {}", this.state);
		logger.debug("ICE connection state: {}", this.pc.iceConnectionState());
		logger.debug("ICE gathering state: {}", this.pc.iceGatheringState());
		logger.debug("Signaling state: {}", this.pc.signalingState());
		logger.debug("*** END CONNECTION STATUS");
	}
}

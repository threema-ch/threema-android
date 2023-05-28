/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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

package ch.threema.app.webclient.services.instance.state;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.saltyrtc.chunkedDc.Unchunker;
import org.saltyrtc.client.SaltyRTC;
import org.saltyrtc.client.SaltyRTCBuilder;
import org.saltyrtc.client.events.ApplicationDataEvent;
import org.saltyrtc.client.events.EventHandler;
import org.saltyrtc.client.events.SignalingStateChangedEvent;
import org.saltyrtc.client.exceptions.ConnectionException;
import org.saltyrtc.client.exceptions.InvalidKeyException;
import org.saltyrtc.client.signaling.CloseCode;
import org.saltyrtc.client.signaling.state.SignalingState;
import org.saltyrtc.client.tasks.Task;
import org.saltyrtc.tasks.webrtc.WebRTCTask;
import org.saltyrtc.tasks.webrtc.WebRTCTaskBuilder;
import org.saltyrtc.tasks.webrtc.WebRTCTaskVersion;
import org.saltyrtc.tasks.webrtc.exceptions.UntiedException;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransportHandler;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransportLink;
import org.slf4j.Logger;
import org.webrtc.DataChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocketFactory;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.BuildConfig;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.SendMode;
import ch.threema.app.webclient.converter.ConnectionDisconnect;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.listeners.PeerConnectionListener;
import ch.threema.app.webclient.listeners.WebClientMessageListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.PeerConnectionState;
import ch.threema.app.webclient.webrtc.DataChannelContext;
import ch.threema.app.webclient.webrtc.PeerConnectionWrapper;
import ch.threema.app.webclient.webrtc.TemporaryDataChannelObserver;
import ch.threema.app.webclient.webrtc.TemporaryTaskEventHandler;
import ch.threema.app.webrtc.DataChannelObserver;
import ch.threema.app.webrtc.UnboundedFlowControlledDataChannel;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.logging.ThreemaLogger;

/**
 * Context passed around by the session state classes that have an active connection open.
 *
 * This contains everything that needs to be initialized on connect and cleaned up on disconnect.
 *
 * It also holds the authoritative SessionState instance.
 */
@WorkerThread
class SessionConnectionContext {
	// WebSocket connect timeout
	static final int WS_CONNECT_TIMEOUT_MS = 5000;
	// WebSocket ping interval
	static final int WS_PING_INTERVAL_S = 60;
	// SaltyRTC client-to-client connection timeout
	static final int C2C_CONNECT_TIMEOUT_MS = 42000;

	// Logger
	private final Logger logger = LoggingUtil.getThreemaLogger("SessionConnectionContext");

	// Session context
	@NonNull final SessionContext ctx;

	// SaltyRTC
	@NonNull final SaltyRTC salty;
	@Nullable DataChannel sdc = null;

	// WebRTC
	@Nullable PeerConnectionWrapper pc = null;
	@Nullable DataChannelContext dcc = null;

	// If set to true, ignore all further events!
	@NonNull AtomicBoolean closed = new AtomicBoolean(false);

	SessionConnectionContext(
		@NonNull final SessionContext ctx,
		@NonNull final SaltyRTCBuilder builder
	) throws NoSuchAlgorithmException, InvalidKeyException, IllegalArgumentException {
		// Create SSL socket factory
		final SSLSocketFactory sslSocketFactory = ConfigUtils.getSSLSocketFactory(ctx.model.getSaltyRtcHost());

		// Create SaltyRTC tasks
		final Task[] tasks = new Task[] {
			new WebRTCTaskBuilder()
				.withVersion(WebRTCTaskVersion.V1)
				.withHandover(true)
				.build(),
			new WebRTCTaskBuilder()
				.withVersion(WebRTCTaskVersion.V0)
				.withHandover(true)
				.build()
		};

		// Set connection information
		builder.connectTo(ctx.model.getSaltyRtcHost(), ctx.model.getSaltyRtcPort(), sslSocketFactory);

		// Set a server key if available
		if (ctx.model.getServerKey() != null) {
			builder.withServerKey(ctx.model.getServerKey());
		}

		// Create SaltyRTC instance
		SaltyRTC salty = builder
			.usingTasks(tasks)
			.withPingInterval(WS_PING_INTERVAL_S)
			.withWebsocketConnectTimeout(WS_CONNECT_TIMEOUT_MS)
			.asResponder();

		// Enable debugging in a debug build
		salty.setDebug(BuildConfig.DEBUG);

		// Store instances
		this.ctx = ctx;
		this.salty = salty;

		// Set logger prefix
		if (logger instanceof ThreemaLogger) {
			((ThreemaLogger) logger).setPrefix(ctx.sessionId + "." + ctx.affiliationId);
		}

		// Create temporary task event handler
		final TemporaryTaskEventHandler temporaryTaskEventHandler = new TemporaryTaskEventHandler();

		// Handle signaling state
		this.salty.events.signalingStateChanged.register(new EventHandler<SignalingStateChangedEvent>() {
			@Override
			@AnyThread
			public boolean handle(SignalingStateChangedEvent event) {
				// Unregister event handler when already closed
				if (SessionConnectionContext.this.closed.get()) {
					return true;
				}
				final SignalingState state = event.getState();

				// Register the temporary task event handler, so no events are being lost
				if (state == SignalingState.TASK) {
					final WebRTCTask task = (WebRTCTask) SessionConnectionContext.this.salty.getTask();
					task.setMessageHandler(temporaryTaskEventHandler);
				}

				// Dispatch event to worker thread
				SessionConnectionContext.this.ctx.handler.post(new Runnable() {
					@Override
					@WorkerThread
					public void run() {
						logger.info("Signaling state changed to {}", state.name());
						switch (state) {
							case NEW:
								break;
							case WS_CONNECTING:
							case SERVER_HANDSHAKE:
							case PEER_HANDSHAKE:
								if (!(SessionConnectionContext.this.ctx.manager.getInternalState() instanceof SessionStateConnecting)) {
									SessionConnectionContext.this.ctx.manager.setError(
										"Signaling state changed to " + state.name() + " in session state " +
											SessionConnectionContext.this.ctx.manager.getInternalState().state.name());
								}
								break;
							case TASK:
								// Create WebRTC peer connection
								try {
									SessionConnectionContext.this.createPeerConnection(temporaryTaskEventHandler);
								} catch (Exception error) {
									logger.error(error.toString());
									SessionConnectionContext.this.ctx.manager.setError(error.toString());
								}
								break;
							case CLOSING:
								SessionConnectionContext.this.salty.disconnect();
								SessionConnectionContext.this.ctx.manager.setDisconnected(DisconnectContext.unknown());
								break;
							case CLOSED:
								SessionConnectionContext.this.ctx.manager.setDisconnected(DisconnectContext.unknown());
								break;
							case ERROR:
								SessionConnectionContext.this.ctx.manager.setError("Signaling state changed to ERROR");
								break;
						}
					}
				});

				// Don't unregister event handler
				return false;
			}
		});

		// Handle application data
		salty.events.applicationData.register(new EventHandler<ApplicationDataEvent>() {
			@Override
			@AnyThread
			public boolean handle(ApplicationDataEvent event) {
				// Unregister event handler when already closed
				if (SessionConnectionContext.this.closed.get()) {
					return true;
				}

				// Note: No dispatching required as this is only being logged
				logger.error("Unexpected incoming application message");

				// Don't unregister event handler
				return false;
			}
		});
	}

	/**
	 * Create a WebRTC peer-to-peer connection.
	 */
	private void createPeerConnection(TemporaryTaskEventHandler temporaryTaskEventHandler) throws Exception {
		// Since the data channel will eventually move us into the connected state,
		// we must be in the connecting state at this point.
		if (!(this.ctx.manager.getInternalState() instanceof SessionStateConnecting)) {
			throw new IllegalStateException("Expected 'connecting' state");
		}

		// Sanity-check: Ensure a WebRTC task has been negotiated
		if (!(this.salty.getTask() instanceof WebRTCTask)) {
			throw new ConnectionException("Expected a WebRTC task to be negotiated");
		}
		final WebRTCTask task = (WebRTCTask) this.salty.getTask();

		// Make sure that we're starting from a clean state
		if (this.pc != null) {
			throw new IllegalStateException("Peer connection wrapper is not null");
		}
		if (this.dcc != null) {
			throw new IllegalStateException("Data channel is not null");
		}

		// Create WebRTC peer connection
		final String logPrefix = this.ctx.sessionId + "." + this.ctx.affiliationId;
		this.pc = new PeerConnectionWrapper(
			logPrefix, this.ctx.services.appContext, this.ctx.handler, task, temporaryTaskEventHandler, this.ctx.allowIpv6(),
			new PeerConnectionListener() {
				@Override
				@AnyThread
				public synchronized void onStateChanged(PeerConnectionState oldState, PeerConnectionState newState) {
					// Note: Synchronized because webrtc.org does call what the fuck it wants on
					//       whatever thread it wants, so we need this to guarantee state ordering

					// Dispatch event to worker thread
					SessionConnectionContext.this.ctx.handler.post(new Runnable() {
						@Override
						@WorkerThread
						public void run() {
							// Ignore events when already closed
							if (SessionConnectionContext.this.closed.get()) {
								return;
							}

							logger.info("Peer connection state changed from {} to {} and signaling state = {}",
								oldState, newState, salty.getSignalingState());
							switch (newState) {
								case CONNECTING:
								case CONNECTED:
									break;
								case FAILED:
								case CLOSED:
									SessionConnectionContext.this.ctx.manager.setDisconnected(DisconnectContext.unknown());
									break;
							}
						}
					});
				}

				@Override
				@AnyThread
				public void onDataChannel(@NonNull final DataChannel dc) {
					// Register the temporary data channel observer, so no events are being lost
					final TemporaryDataChannelObserver temporaryDataChannelObserver = new TemporaryDataChannelObserver();
					temporaryDataChannelObserver.register(dc);

					// Dispatch event to worker thread
					SessionConnectionContext.this.ctx.handler.post(new Runnable() {
						@Override
						@WorkerThread
						public void run() {
							// Ignore events when already closed
							if (SessionConnectionContext.this.closed.get()) {
								return;
							}

							// Ensure the channel is connecting or open
							final DataChannel.State state = dc.state();
							if (state != DataChannel.State.CONNECTING && state != DataChannel.State.OPEN) {
								final String label = dc.label();
								logger.error("Received data channel {} is in the state {}", label, state);
								return;
							}

							// Bind or discard data channel
							SessionConnectionContext.this.handleDataChannel(dc, temporaryDataChannelObserver);
						}
					});
				}
			});

		// Attempt to hand over the signalling data channel
		this.createSignalingChannelForHandover(task, logPrefix);
	}

	/**
	 * Create a signalling data channel and attempt to hand the signalling
	 * channel over to it.
	 */
	private void createSignalingChannelForHandover(@NonNull final WebRTCTask task, @NonNull final String logPrefix) {
		// Ensure this is only called once per connection
		if (this.sdc != null) {
			logger.error("Attempted to create another signalling data channel");
			return;
		}

		// Create signalling data channel
		final SignalingTransportLink link = task.getTransportLink();
		final DataChannel.Init parameters = new DataChannel.Init();
		parameters.id = link.getId();
		parameters.negotiated = true;
		parameters.ordered = true;
		parameters.protocol = link.getProtocol();
		this.sdc = Objects.requireNonNull(this.pc).getPeerConnection()
			.createDataChannel(link.getLabel(), parameters);
		Objects.requireNonNull(this.sdc);

		// Wrap as unbounded, flow-controlled data channel
		final UnboundedFlowControlledDataChannel ufcdc = new UnboundedFlowControlledDataChannel(logPrefix, this.sdc);

		// Create signalling data channel logger
		final Logger sdcLogger = LoggingUtil.getThreemaLogger("SignalingDataChannel");
		if (sdcLogger instanceof ThreemaLogger) {
			((ThreemaLogger) sdcLogger).setPrefix(logPrefix + "." + this.sdc.label() + "/" + this.sdc.id());
		}

		// Create transport handler
		final SignalingTransportHandler handler = new SignalingTransportHandler() {
			@Override
			@AnyThread
			public long getMaxMessageSize() {
				return SessionConnectionContext.this.pc.getMaxMessageSize();
			}

			@Override
			@AnyThread
			public void close() {
				// Ignore events when already closed
				if (SessionConnectionContext.this.closed.get()) {
					return;
				}

				// Sanity-check
				if (SessionConnectionContext.this.sdc == null) {
					logger.error("SignalingTransportHandler.close event but data channel has already been disposed!");
					return;
				}

				// Close data channel
				sdcLogger.info("Data channel {} close request", SessionConnectionContext.this.sdc.label());
				SessionConnectionContext.this.sdc.close();
			}

			@Override
			@AnyThread
			public void send(@NonNull final ByteBuffer message) {
				// Ignore events when already closed
				if (SessionConnectionContext.this.closed.get()) {
					return;
				}

				// Sanity-check
				if (SessionConnectionContext.this.sdc == null) {
					logger.error("SignalingTransportHandler.send event but data channel has already been disposed!");
					return;
				}

				// Copy the message since the ByteBuffer will be reused immediately
				final ByteBuffer copy = ByteBuffer.allocate(message.remaining());
				copy.put(message);
				copy.flip();

				// Send message via data channel
				sdcLogger.debug("Data channel {} outgoing signaling message of length {}",
					SessionConnectionContext.this.sdc.label(), copy.remaining());
				ufcdc.write(new DataChannel.Buffer(copy, true));
			}
		};

		// Bind events
		DataChannelObserver.register(this.sdc, new DataChannelObserver() {
			@Override
			@AnyThread
			public void onBufferedAmountChange(final long bufferedAmount) {
				// Sanity-check
				if (SessionConnectionContext.this.sdc == null) {
					logger.error("SignalingTransportHandler.onBufferedAmountChange event but data channel has already been disposed!");
					return;
				}

				// Forward buffered amount to flow control
				// Important: ALWAYS dispatch this event to another thread because webrtc.org!
				RuntimeUtil.runInAsyncTask(ufcdc::bufferedAmountChange);
			}

			@Override
			@AnyThread
			public synchronized void onStateChange(@NonNull DataChannel.State state) {
				// Note: Synchronized because webrtc.org does call what the fuck it wants on
				//       whatever thread it wants, so we need this to guarantee state ordering

				// Dispatch event to worker thread
				SessionConnectionContext.this.ctx.handler.post(new Runnable() {
					@Override
					@WorkerThread
					public void run() {
						// Ignore events when already closed
						if (SessionConnectionContext.this.closed.get()) {
							return;
						}

						// Sanity-check
						if (SessionConnectionContext.this.sdc == null) {
							logger.error("SignalingTransportHandler.onStateChange event but data channel has already been disposed!");
							return;
						}

						// Handle state change
						switch (state) {
							case CONNECTING:
								sdcLogger.debug("Connecting");
								break;
							case OPEN:
								sdcLogger.info("Open");
								task.handover(handler);
								break;
							case CLOSING:
								sdcLogger.debug("Closing");
								try {
									link.closing();
								} catch (UntiedException e) {
									sdcLogger.warn("Could not move into closing state", e);
								}
								break;
							case CLOSED:
								sdcLogger.info("Closed");
								try {
									link.closed();
								} catch (UntiedException e) {
									// Note: We can safely ignore this because, in
									//       our case, the signalling instance may
									//       be closed before the channel has been
									//       through the closing sequence.
								}

								// Note: The data channel MUST NOT be used after this point!
								final DataChannel dc = SessionConnectionContext.this.sdc;
								SessionConnectionContext.this.sdc = null;
								RuntimeUtil.runInAsyncTask(dc::dispose);
								break;
						}
					}
				});
			}

			@Override
			@AnyThread
			public synchronized void onMessage(@NonNull final DataChannel.Buffer buffer) {
				// Note: Synchronized because webrtc.org does call what the fuck it wants on
				//       whatever thread it wants, so we need this to guarantee message ordering

				// Copy the message since the ByteBuffer will be reused immediately
				final boolean isBinary = buffer.binary;
				final ByteBuffer copy = ByteBuffer.allocate(buffer.data.remaining());
				copy.put(buffer.data);
				copy.flip();

				// Dispatch event to worker thread
				SessionConnectionContext.this.ctx.handler.post(new Runnable() {
					@Override
					@WorkerThread
					public void run() {
						if (!isBinary) {
							sdcLogger.error("Received non-binary message");
							task.close(CloseCode.PROTOCOL_ERROR);
						} else {
							try {
								link.receive(copy);
							} catch (UntiedException error) {
								sdcLogger.warn("Could not feed incoming data to the transport link", error);
							}
						}
					}
				});
			}
		});
	}

	/**
	 * Handle an incoming data channel as the ARP channel.
	 */
	private void handleDataChannel(@NonNull final DataChannel dc, @NonNull final TemporaryDataChannelObserver temporaryDataChannelObserver) {
		// A new data channel is registered.
		// Note: This can not be the data channel intended for the signalling channel since
		//       that is being created by us (negotiated).
		if (this.dcc != null) {
			this.ctx.manager.setError("A DataChannel instance is already registered");
			return;
		}

		// Sanity-check: Ensure there is a peer connection
		if (this.pc == null) {
			this.ctx.manager.setError("PeerConnection instance is null");
			return;
		}

		// Sanity-check: Ensure a WebRTC task has been negotiated
		if (!(this.salty.getTask() instanceof WebRTCTask)) {
			this.ctx.manager.setError("Expected a WebRTC task to be negotiated");
			return;
		}
		final WebRTCTask task = (WebRTCTask) this.salty.getTask();

		// Create data channel context
		this.dcc = new DataChannelContext(
			this.ctx.sessionId + "." + this.ctx.affiliationId,
			dc, task, this.pc.getMaxMessageSize(), new Unchunker.MessageListener() {
			/**
			 * Handle a reassembled message.
			 *
			 * Note: Since we call .receive on the worker thread, the message event
			 *       will also fire on the worker thread.
			 */
			@Override
			@WorkerThread
			public void onMessage(@NonNull final ByteBuffer message) {
				// Ignore events when already closed
				if (SessionConnectionContext.this.closed.get()) {
					return;
				}

				// Sanity-check
				if (SessionConnectionContext.this.dcc == null) {
					logger.error("onMessage (full message) event but data channel has already been disposed!");
					return;
				}

				// Decode msgpack bytes
				final Value val;
				try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(message)) {
					val = unpacker.unpackValue();
				} catch (IOException e) {
					ctx.manager.setError("IOException while decoding incoming data channel message");
					return;
				} catch (OutOfMemoryError e) {
					SessionConnectionContext.this.sendBestEffortConnectionDisconnect(DisconnectContext.REASON_OUT_OF_MEMORY);
					ctx.manager.setError("Out of memory while decoding incoming data channel message");
					return;
				}
				if (val.isMapValue()) {
					// Notify listeners about new message
					WebClientListenerManager.messageListener.handle(new ListenerManager.HandleListener<WebClientMessageListener>() {
						@Override
						@WorkerThread
						public void handle(WebClientMessageListener listener) {
							if (listener.handle(ctx.model)) {
								listener.onMessage(val.asMapValue());
							}
						}
					});
				} else {
					logger.warn("Received invalid msgpack packet, not a MapValue");
				}
			}
		});

		// Get label
		final String label = dc.label();

		// Bind events
		temporaryDataChannelObserver.replace(dc, new DataChannelObserver() {
			@Override
			@AnyThread
			public void onBufferedAmountChange(final long bufferedAmount) {
				// Sanity-check
				if (SessionConnectionContext.this.dcc == null) {
					logger.error("onBufferedAmountChange event but data channel has already been disposed!");
					return;
				}

				// Forward buffered amount to flow control
				// Important: ALWAYS dispatch this event to another thread because webrtc.org!
				RuntimeUtil.runInAsyncTask(SessionConnectionContext.this.dcc.fcdc::bufferedAmountChange);
			}

			@Override
			@AnyThread
			public synchronized void onStateChange(@NonNull final DataChannel.State state) {
				// Note: Synchronized because webrtc.org does call what the fuck it wants on
				//       whatever thread it wants, so we need this to guarantee state ordering

				// Dispatch event to worker thread
				SessionConnectionContext.this.ctx.handler.post(new Runnable() {
					@Override
					@WorkerThread
					public void run() {
						// Ignore events when already closed
						if (SessionConnectionContext.this.closed.get()) {
							return;
						}

						// Sanity-check
						if (SessionConnectionContext.this.dcc == null) {
							logger.error("onStateChange event but data channel has already been disposed!");
							return;
						}

						// Handle state change
						switch (state) {
							case CONNECTING:
								logger.debug("Data channel {} connecting", label);
								break;
							case OPEN:
								logger.info("Data channel {} open", label);

								// Ready to exchange data
								ctx.manager.setConnected();
								break;
							case CLOSING:
								logger.debug("Data channel {} closing", label);

								// Cannot exchange any further data
								SessionConnectionContext.this.salty.disconnect();
								ctx.manager.setDisconnected(DisconnectContext.unknown());
								break;
							case CLOSED:
								logger.info("Data channel {} closed", label);

								// Note: The data channel MUST NOT be used after this point!
								SessionConnectionContext.this.dcc = null;
								RuntimeUtil.runInAsyncTask(dc::dispose);
								break;
						}
					}
				});
			}

			@Override
			@AnyThread
			public synchronized void onMessage(@NonNull final DataChannel.Buffer buffer) {
				// Note: Synchronized because webrtc.org does call what the fuck it wants on
				//       whatever thread it wants, so we need this to guarantee message ordering

				// Copy the message since the ByteBuffer will be reused immediately
				final boolean isBinary = buffer.binary;
				final ByteBuffer copy = ByteBuffer.allocate(buffer.data.remaining());
				copy.put(buffer.data);
				copy.flip();

				// Dispatch to handler thread
				SessionConnectionContext.this.ctx.handler.post(new Runnable() {
					@Override
					@WorkerThread
					public void run() {
						// Ignore events when already closed
						if (SessionConnectionContext.this.closed.get()) {
							return;
						}

						// Sanity-check
						if (SessionConnectionContext.this.dcc == null) {
							logger.error("onMessage (chunk) event but data channel has already been disposed!");
							return;
						}

						// Ensure binary
						if (!isBinary) {
							ctx.manager.setError(
								"Error: Received non-binary message through signaling data channel.");
							dc.close();
							return;
						}

						try {
							// Reassemble chunks to message
							SessionConnectionContext.this.dcc.receive(copy);
						} catch (OutOfMemoryError error) {
							SessionConnectionContext.this.sendBestEffortConnectionDisconnect(DisconnectContext.REASON_OUT_OF_MEMORY);
							ctx.manager.setError("Out of memory while reassembling incoming data channel message");
						} catch (Exception error) {
							logger.error("Unhandled exception", error);
							ctx.manager.setError("Exception encountered");
						}
					}
				});
			}
		});
	}

	/**
	 * Send a connection disconnect message.
	 *
	 * Important: This is meant as a "last resort" mechanism before a session is
	 *            being torn down. Do not use this for regular disconnects!
	 */
	private void sendBestEffortConnectionDisconnect(@DisconnectContext.DisconnectReason int reason) {
		// Ensure connected
		final SessionState state = this.ctx.manager.getInternalState();
		if (!(state instanceof SessionStateConnected)) {
			logger.info("Could not send alert, not connected");
			return;
		}

		// Send alert synchronously
		final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(Protocol.FIELD_TYPE, Protocol.TYPE_UPDATE);
		builder.put(Protocol.FIELD_SUB_TYPE, Protocol.SUB_TYPE_CONNECTION_DISCONNECT);
		try {
			builder.put(Protocol.FIELD_DATA, ConnectionDisconnect.convert(reason));
		} catch (ConversionException e) {
			logger.warn("ConversionException in sendBestEffortConnectionDisconnect", e);
			return;
		}
		logger.debug("Sending alert");
		state.send(builder.consume(), SendMode.UNSAFE_SYNC);
	}

}

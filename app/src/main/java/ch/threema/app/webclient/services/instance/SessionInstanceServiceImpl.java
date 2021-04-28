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

package ch.threema.app.webclient.services.instance;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.MapValue;
import org.msgpack.value.Value;
import org.saltyrtc.client.SaltyRTCBuilder;
import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.crypto.CryptoProvider;
import org.saltyrtc.client.keystore.KeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.managers.ListenerManager.HandleListener;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.SendMode;
import ch.threema.app.webclient.converter.ConnectionDisconnect;
import ch.threema.app.webclient.exceptions.DispatchException;
import ch.threema.app.webclient.listeners.WebClientMessageListener;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.ServicesContainer;
import ch.threema.app.webclient.services.instance.message.receiver.AcknowledgeRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ActiveConversationHandler;
import ch.threema.app.webclient.services.instance.message.receiver.AvatarRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.BlobRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.CleanReceiverConversationRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ClientInfoRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ConnectionInfoUpdateHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ContactDetailRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ConversationRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.CreateContactHandler;
import ch.threema.app.webclient.services.instance.message.receiver.CreateDistributionListHandler;
import ch.threema.app.webclient.services.instance.message.receiver.CreateGroupHandler;
import ch.threema.app.webclient.services.instance.message.receiver.DeleteDistributionListHandler;
import ch.threema.app.webclient.services.instance.message.receiver.DeleteGroupHandler;
import ch.threema.app.webclient.services.instance.message.receiver.DeleteMessageHandler;
import ch.threema.app.webclient.services.instance.message.receiver.FileMessageCreateHandler;
import ch.threema.app.webclient.services.instance.message.receiver.IgnoreRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.IsTypingHandler;
import ch.threema.app.webclient.services.instance.message.receiver.KeyPersistedRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.MessageReadRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.MessageRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ModifyContactHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ModifyConversationHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ModifyDistributionListHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ModifyGroupHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ModifyProfileHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ProfileRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ReceiversRequestHandler;
import ch.threema.app.webclient.services.instance.message.receiver.SyncGroupHandler;
import ch.threema.app.webclient.services.instance.message.receiver.TextMessageCreateHandler;
import ch.threema.app.webclient.services.instance.message.receiver.ThumbnailRequestHandler;
import ch.threema.app.webclient.services.instance.message.updater.AlertHandler;
import ch.threema.app.webclient.services.instance.message.updater.AvatarUpdateHandler;
import ch.threema.app.webclient.services.instance.message.updater.BatteryStatusUpdateHandler;
import ch.threema.app.webclient.services.instance.message.updater.ConversationUpdateHandler;
import ch.threema.app.webclient.services.instance.message.updater.MessageUpdateHandler;
import ch.threema.app.webclient.services.instance.message.updater.ProfileUpdateHandler;
import ch.threema.app.webclient.services.instance.message.updater.ReceiverUpdateHandler;
import ch.threema.app.webclient.services.instance.message.updater.ReceiversUpdateHandler;
import ch.threema.app.webclient.services.instance.message.updater.TypingUpdateHandler;
import ch.threema.app.webclient.services.instance.message.updater.VoipStatusUpdateHandler;
import ch.threema.app.webclient.services.instance.state.SessionStateManager;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.logging.ThreemaLogger;
import ch.threema.storage.models.WebClientSessionModel;

/**
 * Service class that handles everything related to the ARP session process.
 */
@WorkerThread
public class SessionInstanceServiceImpl implements SessionInstanceService {
	@NonNull final Logger logger = LoggerFactory.getLogger(SessionInstanceServiceImpl.class);

	// Session id registry
	@NonNull private static AtomicInteger staticSessionId = new AtomicInteger(0);

	// Services
	@NonNull private final ServicesContainer services;

	// NaCl crypto provider
	@NonNull private final CryptoProvider cryptoProvider;

	// Model
	@NonNull private final WebClientSessionModel model;

	// Session id
	private final int sessionId;

	// Session state manager
	@NonNull private final SessionStateManager stateManager;

	// Message updaters
	@NonNull private final MessageUpdater[] updaters;

	// Message dispatchers
	@NonNull private final MessageDispatcher[] dispatchers;

	// Listeners
	@NonNull private final WebClientMessageListener messageListener;

	// Current affiliation id
	@Nullable private String affiliationId;

	// Performance testing
	private long startTimeNs = -1;

	@AnyThread
	public SessionInstanceServiceImpl(
		@NonNull final ServicesContainer services,
		@NonNull final CryptoProvider cryptoProvider,
		@NonNull final WebClientSessionModel model,
		@NonNull final HandlerExecutor handler
	) {
		this.services = services;
		this.cryptoProvider = cryptoProvider;
		this.model = model;

		// Determine session id
		this.sessionId = SessionInstanceServiceImpl.staticSessionId.getAndIncrement();

		// Set logger prefix
		if (logger instanceof ThreemaLogger) {
			((ThreemaLogger) logger).setPrefix(String.valueOf(this.sessionId));
		}
		logger.info("Initialize SessionInstanceServiceImpl");

		// Initialize state manager
		this.stateManager = new SessionStateManager(sessionId, model, handler, services, new SessionStateManager.StopHandler() {
			@Override
			@WorkerThread
			public void onStopped(@NonNull DisconnectContext reason) {
				SessionInstanceServiceImpl.this.unregister();
			}
		});

		// Create dispatchers
		final MessageDispatcher responseDispatcher = new MessageDispatcher(Protocol.TYPE_RESPONSE,
			this, services.lifetime, services.messageQueue);
		final MessageDispatcher updateDispatcher = new MessageDispatcher(Protocol.TYPE_UPDATE,
			this, services.lifetime, services.messageQueue);
		final MessageDispatcher deleteDispatcher = new MessageDispatcher(Protocol.TYPE_DELETE,
			this, services.lifetime, services.messageQueue);

		// Create update handlers
		final ReceiverUpdateHandler receiverUpdateHandler = new ReceiverUpdateHandler(
			handler,
			updateDispatcher,
			services.synchronizeContacts
		);
		final ReceiversUpdateHandler receiversUpdateHandler = new ReceiversUpdateHandler(
			handler,
			updateDispatcher,
			services.contact
		);
		final AvatarUpdateHandler avatarUpdateHandler = new AvatarUpdateHandler(
			handler,
			updateDispatcher
		);
		final ConversationUpdateHandler conversationUpdateHandler = new ConversationUpdateHandler(
			handler,
			updateDispatcher,
			services.contact,
			services.group,
			services.distributionList,
			services.hiddenChat,
			this.sessionId
		);
		final MessageUpdateHandler messageUpdateHandler = new MessageUpdateHandler(
			handler,
			updateDispatcher,
			services.hiddenChat,
			services.file
		);
		final TypingUpdateHandler typingUpdateHandler = new TypingUpdateHandler(
			handler,
			updateDispatcher
		);

		final BatteryStatusUpdateHandler batteryStatusUpdateHandler = new BatteryStatusUpdateHandler(
			services.appContext,
			handler,
			this.sessionId,
			updateDispatcher
		);
		final VoipStatusUpdateHandler voipStatusUpdateHandler = new VoipStatusUpdateHandler(
			handler,
			this.sessionId,
			updateDispatcher
		);
		final ProfileUpdateHandler profileUpdateHandler = new ProfileUpdateHandler(
			handler,
			updateDispatcher,
			services.user,
			services.contact
		);

		// Register alert handler
		final AlertHandler alertHandler = new AlertHandler(handler, updateDispatcher);
		alertHandler.register();

		// Create request dispatcher and the handlers
		// Dispatchers
		final MessageDispatcher requestDispatcher = new MessageDispatcher(Protocol.TYPE_REQUEST,
			this, services.lifetime, services.messageQueue);

		// Client info requester
		requestDispatcher.addReceiver(new ClientInfoRequestHandler(
			responseDispatcher,
			services.preference,
			services.appContext,
			new ClientInfoRequestHandler.Listener() {
				@Override
				@WorkerThread
				public void onReceived(@NonNull final String userAgent) {
					WebClientListenerManager.serviceListener.handle(new HandleListener<WebClientServiceListener>() {
						@Override
						@WorkerThread
						public void handle(WebClientServiceListener listener) {
							listener.onStarted(model, Objects.requireNonNull(model.getKey()), userAgent);
						}
					});
				}

				@Override
				@WorkerThread
				public void onAnswered(@Nullable final String pushToken) {
					// Save the gcm token in the model
					if (!TestUtil.compare(model.getPushToken(), pushToken)) {
						WebClientListenerManager.serviceListener.handle(new HandleListener<WebClientServiceListener>() {
							@Override
							@WorkerThread
							public void handle(WebClientServiceListener listener) {
								listener.onPushTokenChanged(model, pushToken);
							}
						});
					}

					// TODO: Below block should happen after the connectionInfo handshake
					// Register battery status listener
					batteryStatusUpdateHandler.register();
					// VoIP status listener
					voipStatusUpdateHandler.register();
					// Send initial battery status
					batteryStatusUpdateHandler.trigger();

					SessionInstanceServiceImpl.this.logActionSinceStart("Client info sent");
				}
			}
		));
		// Key persisted info requester
		requestDispatcher.addReceiver(new KeyPersistedRequestHandler(
			new KeyPersistedRequestHandler.Listener() {
				@Override
				@WorkerThread
				public void onReceived() {
					WebClientListenerManager.serviceListener.handle(new HandleListener<WebClientServiceListener>() {
						@Override
						@WorkerThread
						public void handle(WebClientServiceListener listener) {
							listener.onKeyPersisted(model, true);
						}
					});
				}
			}
		));
		requestDispatcher.addReceiver(new ReceiversRequestHandler(
			responseDispatcher,
			services.contact,
			services.group,
			services.distributionList,
			new ReceiversRequestHandler.Listener() {
				private boolean registered = false;

				@Override
				@WorkerThread
				public void onReceived() {
					if (!registered) {
						registered = true;
						receiverUpdateHandler.register();
						receiversUpdateHandler.register();
						avatarUpdateHandler.register();
					}
				}

				@Override
				@WorkerThread
				public void onAnswered() {
					SessionInstanceServiceImpl.this.logActionSinceStart("Receivers sent");
				}
			}
		));
		requestDispatcher.addReceiver(new ConversationRequestHandler(
			responseDispatcher,
			services.conversation,
			new ConversationRequestHandler.Listener() {
				private boolean registered = false;

				@Override
				@WorkerThread
				public void onRespond() {
					if (!registered) {
						registered = true;
						conversationUpdateHandler.register();
						typingUpdateHandler.register();
					}
				}

				@Override
				@WorkerThread
				public void onAnswered() {
					SessionInstanceServiceImpl.this.logActionSinceStart("Conversations sent");
				}
			}
		));
		requestDispatcher.addReceiver(new MessageRequestHandler(
			responseDispatcher,
			services.message,
			services.hiddenChat,
			new MessageRequestHandler.Listener() {
				@Override
				@WorkerThread
				public void onReceive(ch.threema.app.messagereceiver.MessageReceiver receiver) {
					// Register for updates
					if (messageUpdateHandler.register(receiver)) {
						logger.info("Registered message updates");
					} else {
						logger.warn("Message updates not registered");
					}
				}
			}
		));
		requestDispatcher.addReceiver(new BlobRequestHandler (
			handler,
			responseDispatcher,
			services.message,
			services.file
		));
		requestDispatcher.addReceiver(new AvatarRequestHandler(responseDispatcher));
		requestDispatcher.addReceiver(new ThumbnailRequestHandler(
			responseDispatcher,
			services.message,
			services.file));

		requestDispatcher.addReceiver(new AcknowledgeRequestHandler(
			services.message,
			services.notification
		));
		requestDispatcher.addReceiver(new MessageReadRequestHandler(
			services.contact,
			services.group,
			services.message,
			services.notification
		));
		requestDispatcher.addReceiver(new ContactDetailRequestHandler(
			responseDispatcher,
			services.contact
		));

		requestDispatcher.addReceiver(new SyncGroupHandler(
			responseDispatcher,
			services.group
		));
		requestDispatcher.addReceiver(new ProfileRequestHandler(
			responseDispatcher,
			services.user,
			services.contact,
			new ProfileRequestHandler.Listener() {
				@Override
				@WorkerThread
				public void onReceived() {
					// Register for updates
					profileUpdateHandler.register();
					logger.info("Registered for profile updates");
				}

				@Override
				@WorkerThread
				public void onAnswered() {
					SessionInstanceServiceImpl.this.logActionSinceStart("Profile sent");
				}
			}
		));
		// Ignore battery status requests
		requestDispatcher.addReceiver(new IgnoreRequestHandler(
			Protocol.TYPE_REQUEST,
			Protocol.SUB_TYPE_BATTERY_STATUS
		));

		// Create 'create' dispatcher and the handlers
		final MessageDispatcher createDispatcher = new MessageDispatcher(Protocol.TYPE_CREATE,
			this, services.lifetime, services.messageQueue);
		createDispatcher.addReceiver(new TextMessageCreateHandler(
			createDispatcher,
			services.message,
			services.lifetime,
			services.blackList));
		createDispatcher.addReceiver(new FileMessageCreateHandler(
			createDispatcher,
			services.message,
			services.file,
			services.lifetime,
			services.blackList));

		createDispatcher.addReceiver(new CreateContactHandler(
			createDispatcher,
			services.contact
		));

		createDispatcher.addReceiver(new CreateGroupHandler(
			createDispatcher,
			services.group
		));

		createDispatcher.addReceiver(new CreateDistributionListHandler(
			createDispatcher,
			services.distributionList
		));

		updateDispatcher.addReceiver(new ModifyContactHandler(
			updateDispatcher,
			services.contact
		));
		updateDispatcher.addReceiver(new ModifyGroupHandler(
			updateDispatcher,
			services.group
		));
		updateDispatcher.addReceiver(new ModifyDistributionListHandler(
			updateDispatcher,
			services.distributionList
		));
		updateDispatcher.addReceiver(new ModifyProfileHandler(
			responseDispatcher,
			services.contact,
			services.user
		));
		updateDispatcher.addReceiver(new ModifyConversationHandler(
			responseDispatcher,
			services.conversation,
			services.conversationTag
		));
		updateDispatcher.addReceiver(new IsTypingHandler(
			services.user
		));
		updateDispatcher.addReceiver(new ConnectionInfoUpdateHandler());
		updateDispatcher.addReceiver(new ActiveConversationHandler(
			services.contact,
			services.group,
			services.conversation,
			services.conversationTag
		));

		deleteDispatcher.addReceiver(new DeleteMessageHandler(
			responseDispatcher,
			services.message
		));
		deleteDispatcher.addReceiver(new DeleteGroupHandler(
			responseDispatcher,
			services.group
		));
		deleteDispatcher.addReceiver(new DeleteDistributionListHandler(
			responseDispatcher,
			services.distributionList));
		deleteDispatcher.addReceiver(new CleanReceiverConversationRequestHandler(
			responseDispatcher,
			services.conversation));

		// Create update handlers array
		this.updaters = new MessageUpdater[]{
			receiverUpdateHandler,
			receiversUpdateHandler,
			avatarUpdateHandler,
			conversationUpdateHandler,
			messageUpdateHandler,
			typingUpdateHandler,
			batteryStatusUpdateHandler,
			voipStatusUpdateHandler,
			profileUpdateHandler,
			alertHandler,
		};

		// Create message dispatchers array
		this.dispatchers = new MessageDispatcher[]{
			requestDispatcher,
			responseDispatcher,
			updateDispatcher,
			createDispatcher,
			deleteDispatcher,
		};

		// Register listener for new web client messages
		this.messageListener = new WebClientMessageListener() {
			@Override
			@WorkerThread
			public void onMessage(MapValue message) {
				receive(message);
			}

			@Override
			@WorkerThread
			public boolean handle(WebClientSessionModel sessionModel) {
				return sessionModel.getId() == SessionInstanceServiceImpl.this.model.getId();
			}
		};
	}

	/**
	 * Return whether this session is in a non-terminal state.
	 */
	@Override
	@AnyThread // Should be safe, we're just checking a variable
	public boolean isRunning() {
		final WebClientSessionState state = this.stateManager.getState();
		switch (state) {
			case DISCONNECTED:
			case ERROR:
				return false;
			case CONNECTING:
			case CONNECTED:
				return true;
			default:
				throw new IllegalStateException("Unhandled state: " + state);
		}
	}

	/**
	 * Return the current state of the session.
	 */
	@Override
	@NonNull public WebClientSessionState getState() {
		return this.stateManager.getState();
	}

	/**
	 * Return whether the session needs to be restarted
	 * (if not currently running or due to a different affiliation id).
	 */
	@Override
	public boolean needsRestart(@Nullable final String affiliationId) {
		if (!this.isRunning()) {
			return true;
		}
		return affiliationId != null && (this.affiliationId == null || !this.affiliationId.equals(affiliationId));
	}

	/**
	 * Return the session model.
	 */
	@Override
	@NonNull public WebClientSessionModel getModel() {
		return this.model;
	}

	/**
	 * Start the session.
	 */
	@Override
	public void start(
		@NonNull final byte[] permanentKey,
		@NonNull byte[] authToken,
		@Nullable final String affiliationId
	) {
		// Update logger prefix
		if (logger instanceof ThreemaLogger) {
			((ThreemaLogger) logger).setPrefix(this.sessionId + "." + affiliationId);
		}
		logger.info("Starting Threema Web session");

		final KeyStore ks = new KeyStore(this.cryptoProvider);

		// Temporarily set the key on session model, but do not save
		this.model
			.setKey(permanentKey)
			.setPrivateKey(ks.getPrivateKey());

		// Create a builder with a new keystore, including a new permanent key pair.
		final SaltyRTCBuilder builder = this.getBuilder()
			.initiatorInfo(permanentKey, authToken)
			.withKeyStore(ks);
		this.init(builder, affiliationId);
	}

	/**
	 * Resume this session based on the data stored in the session model.
	 */
	@Override
	public void resume(@Nullable final String affiliationId) throws CryptoException {
		// Update logger prefix
		if (logger instanceof ThreemaLogger) {
			((ThreemaLogger) logger).setPrefix(this.sessionId + "." + affiliationId);
		}
		logger.info("Resuming Threema Web session");

		if (this.model.getKey() == null) {
			logger.error("No session key in model instance, aborting resume");
			return;
		}

		if (this.model.getPrivateKey() == null) {
			logger.error("No private key in model instance, aborting resume");
			return;
		}

		// Create a builder with a new keystore based on an existing session.
		final SaltyRTCBuilder builder = this.getBuilder()
			.withTrustedPeerKey(this.model.getKey())
			.withKeyStore(new KeyStore(this.cryptoProvider, this.model.getPrivateKey()));
		this.init(builder, affiliationId);
	}

	/**
	 * Get a SaltyRTC builder instance, pre-initialised with default values based
	 * on the app preferences.
	 */
	private @NonNull SaltyRTCBuilder getBuilder() {
		// Determine dual stack mode.
		// When IPv6 enabled for VoIP and web, try both.
		// When IPv6 disabled, only use IPv4.
		SaltyRTCBuilder.DualStackMode dualStackMode = SaltyRTCBuilder.DualStackMode.BOTH;
		if (!this.services.preference.allowWebrtcIpv6()) {
			dualStackMode = SaltyRTCBuilder.DualStackMode.IPV4_ONLY;
		}

		// Create builder instance
		return new SaltyRTCBuilder(this.cryptoProvider)
			.withWebSocketDualStackMode(dualStackMode);
	}

	/**
	 * Initialize the connection.
	 *
	 * Warning: The caller MUST ensure that the current state is either DISCONNECTED or ERROR!
	 */
	private void init(@NonNull final SaltyRTCBuilder builder, @Nullable final String affiliationId) {
		// Register listener (if not already registered)
		// Note: The message listener may already be registered in case a session is being
		//       restarted immediately by a pending wakeup.
		if (!WebClientListenerManager.messageListener.contains(this.messageListener)) {
			logger.debug("Registering message listener");
			WebClientListenerManager.messageListener.add(this.messageListener);
		} else {
			logger.debug("Message listener already registered");
		}

		// Store affiliation id and connect
		this.affiliationId = affiliationId;
		this.stateManager.setConnecting(builder, affiliationId);

		// Log start timestamp
		this.startTimeNs = System.nanoTime();
	}

	@Override
	public void stop(@NonNull final DisconnectContext reason) {
		logger.info("Stopping Threema Web session: {}", reason);

		// Run unregister procedure
		this.unregister();

		// IMPORTANT: Unregistering MUST be done before changing the state since this will
		//            trigger waking up pending sessions which may restart the session again!
		this.stateManager.setDisconnected(reason);
	}

	/**
	 * Should always be called when a stop request is being made or when being stopped.
	 */
	private void unregister() {
		// Deregister update handlers
		for (final MessageUpdater handler: this.updaters) {
			handler.unregister();
		}

		// Remove listener
		if (WebClientListenerManager.messageListener.contains(this.messageListener)) {
			logger.debug("Unregistering message listener");
			WebClientListenerManager.messageListener.remove(this.messageListener);
		} else {
			logger.error("Message listener was not registered!");
		}

		// Reset connection duration timer
		this.startTimeNs = -1;
	}

	/**
	 * Send a msgpack encoded message to the peer through the secure data channel.
	 */
	@Override
	public void send(@NonNull final ByteBuffer message, @NonNull final SendMode mode) {
		this.stateManager.send(message, mode);
	}

	/**
	 * Receive an incoming message.
	 */
	private void receive(MapValue message) {
		try {
			final Map<String, Value> map = new HashMap<>();
			for (Map.Entry<Value, Value> entry : message.entrySet()) {
				map.put(entry.getKey().asStringValue().asString(), entry.getValue());
			}

			// Get type and subtype
			final Value typeValue = map.get(Protocol.FIELD_TYPE);
			final String type = typeValue.asStringValue().asString();
			final Value subTypeValue = map.get(Protocol.FIELD_SUB_TYPE);
			final String subType = subTypeValue.asStringValue().asString();
			logger.debug("Received {}/{}", type, subType);

			boolean received = false;

			// We need to handle some control messages directly, without going through
			// the dispatcher. This is the case for messages like `update/connectionDisconnect`,
			// where we want to be able to access the session instance service directly,
			// without jumping through some hoops using listeners.
			final boolean isUpdate = Protocol.TYPE_UPDATE.equals(type);
			if (isUpdate && Protocol.SUB_TYPE_CONNECTION_DISCONNECT.equals(subType)) {
				this.receiveConnectionDisconnect(map);
				received = true;
			}

			// Dispatch message
			if (!received) {
				for (MessageDispatcher dispatcher : this.dispatchers) {
					if (dispatcher.dispatch(type, subType, map)) {
						received = true;
						break;
					}
				}
			}

			// Check that one dispatcher received the message
			if (!received) {
				logger.warn("Ignored message with type {}", type);
			}
		} catch (MessagePackException e) {
			logger.error("Protocol error due to invalid message", e);
			this.stop(DisconnectContext.byUs(DisconnectContext.REASON_ERROR));
		} catch (NullPointerException e) {
			// TODO: If you don't want this, recursively follow this code and all handlers and fix
			//       the potential NPEs. There are dozens...
			logger.error("Protocol error due to NPE", e);
			this.stop(DisconnectContext.byUs(DisconnectContext.REASON_ERROR));
		} catch (DispatchException e) {
			logger.warn("Could not dispatch message", e);
		}
	}

	/**
	 * Receive and handle an update/connectionDisconnect message.
	 */
	private void receiveConnectionDisconnect(final Map<String, Value> map) {
		// Extract data map
		if (!map.containsKey(Protocol.FIELD_DATA)) {
			logger.warn("Ignored connectionDisconnect message without data field");
			return;
		}
		final Value data = map.get(Protocol.FIELD_DATA);
		if (!data.isMapValue()) {
			logger.warn("Ignored connectionDisconnect message with non-map data field");
			return;
		}
		final Map<String, Value> dataMap = new HashMap<>();
		for (Map.Entry<Value, Value> entry : data.asMapValue().entrySet()) {
			dataMap.put(entry.getKey().asStringValue().asString(), entry.getValue());
		}

		// Extract reason
		if (!dataMap.containsKey(ConnectionDisconnect.REASON)) {
			logger.warn("Ignored connectionDisconnect message without reason field");
			return;
		}
		final Value reasonValue = dataMap.get(ConnectionDisconnect.REASON);
		if (!reasonValue.isStringValue()) {
			logger.warn("Ignored connectionDisconnect message with non-string reason field");
			return;
		}
		final String reasonText = reasonValue.asStringValue().toString();

		// Create DisconnectContext
		final DisconnectContext reason;
		switch (reasonText) {
			case ConnectionDisconnect.REASON_SESSION_STOPPED:
				reason = new DisconnectContext.ByPeer(DisconnectContext.REASON_SESSION_STOPPED);
				break;
			case ConnectionDisconnect.REASON_SESSION_DELETED:
				reason = new DisconnectContext.ByPeer(DisconnectContext.REASON_SESSION_DELETED);
				break;
			case ConnectionDisconnect.REASON_WEBCLIENT_DISABLED:
				reason = new DisconnectContext.ByPeer(DisconnectContext.REASON_WEBCLIENT_DISABLED);
				break;
			case ConnectionDisconnect.REASON_SESSION_REPLACED:
				reason = new DisconnectContext.ByPeer(DisconnectContext.REASON_SESSION_REPLACED);
				break;
			case ConnectionDisconnect.REASON_OUT_OF_MEMORY:
				reason = new DisconnectContext.ByPeer(DisconnectContext.REASON_OUT_OF_MEMORY);
				break;
			case ConnectionDisconnect.REASON_ERROR:
				reason = new DisconnectContext.ByPeer(DisconnectContext.REASON_ERROR);
				break;
			default:
				logger.warn("Ignored connectionDisconnect message with invalid reason field: " + reasonText);
				return;
		}

		logger.debug("Peer requested disconnecting via connectionDisconnect msg");
		this.stop(reason);
	}

	private void logActionSinceStart(@NonNull String message) {
		if (SessionInstanceServiceImpl.this.startTimeNs > 0) {
			long ms = (System.nanoTime() - SessionInstanceServiceImpl.this.startTimeNs) / 1000 / 1000;
			logger.info("{} after {} ms", message, ms);
		}
	}

}

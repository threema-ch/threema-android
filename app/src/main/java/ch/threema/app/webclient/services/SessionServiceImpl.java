/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2022 Threema GmbH
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

package ch.threema.app.webclient.services;

import android.content.Intent;

import org.saltyrtc.client.crypto.CryptoProvider;
import org.slf4j.Logger;

import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.crypto.NativeJnaclCryptoProvider;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.listeners.WebClientSessionListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.services.instance.SessionInstanceService;
import ch.threema.app.webclient.services.instance.SessionInstanceServiceImpl;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.storage.models.WebClientSessionModel;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Supplier;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;

@AnyThread
public class SessionServiceImpl implements SessionService {
	/**
	 * Container for a session instance service and a corresponding listener.
	 */
	private class SessionInstanceContainer {
		@NonNull private final SessionInstanceService instance;
		@NonNull private final WebClientServiceListener listener;


		private SessionInstanceContainer(
			@NonNull final SessionInstanceService instance,
			@NonNull final WebClientServiceListener listener
		) {
			this.instance = instance;
			this.listener = listener;
		}
	}

	private static final Logger logger = LoggingUtil.getThreemaLogger("SessionServiceImpl");

	// Worker thread
	@NonNull private final HandlerExecutor handler;

	// Services
	@NonNull private final ServicesContainer services;

	// NaCl crypto provider
	@NonNull private final CryptoProvider cryptoProvider;

	// Currently running instances
	@NonNull private final Map<Integer, SessionInstanceContainer> instances = new HashMap<>();

	public SessionServiceImpl(@NonNull final HandlerExecutor handler, @NonNull final ServicesContainer services) {
		this.handler = handler;
		this.services = services;

		// Create NaCl crypto provider
		this.cryptoProvider = new NativeJnaclCryptoProvider();
	}

	@Override
	public void setEnabled(final boolean enable) {
		this.handler.post(new Runnable() {
			@Override
			@WorkerThread
			public void run() {
				if (SessionServiceImpl.this.services.preference.isWebClientEnabled() == enable) {
					// No change
					return;
				}

				// Save enabled/disabled flag
				SessionServiceImpl.this.services.preference.setWebClientEnabled(enable);

				// Disable
				if (!enable) {
					// Discard all pending wakeups
					SessionServiceImpl.this.services.sessionWakeUp.discardPendingWakeups();

					// Stop all running session instances
					for (WebClientSessionModel model : SessionServiceImpl.this.getAllSessionModels()) {
						final SessionInstanceService instance = SessionServiceImpl.this.getInstanceService(model, false);
						@DisconnectContext.DisconnectReason int reason = DisconnectContext.REASON_WEBCLIENT_DISABLED;

						// Remove session if non-persistent
						if (!model.isPersistent()) {
							SessionServiceImpl.this.services.database.getWebClientSessionModelFactory().delete(model);
							reason = DisconnectContext.REASON_SESSION_DELETED;
						}

						// Stop session (if an instance exists)
						if (instance != null) {
							instance.stop(DisconnectContext.byUs(reason));
						}

						// Raise 'removed' event if session has been removed
						if (reason == DisconnectContext.REASON_SESSION_DELETED) {
							WebClientListenerManager.sessionListener.handle(new ListenerManager.HandleListener<WebClientSessionListener>() {
								@Override
								@WorkerThread
								public void handle(WebClientSessionListener listener) {
									listener.onRemoved(model);
								}
							});
						}
					}

					// Release all wake locks
					SessionServiceImpl.this.services.wakeLock.releaseAll();

					// Force stop the foreground service
					logger.info("Force stopping SessionAndroidService");
					if (SessionAndroidService.isRunning()) {
						final Intent intent = new Intent(
							SessionServiceImpl.this.services.appContext, SessionAndroidService.class);
						intent.setAction(SessionAndroidService.ACTION_FORCE_STOP);
						logger.info("Sending FORCE_STOP to SessionAndroidService");
						SessionServiceImpl.this.services.appContext.startService(intent);
					}
				}

				// Fire 'enabled' or 'disabled' event
				WebClientListenerManager.serviceListener.handle(listener -> {
					if (enable) {
						listener.onEnabled();
					} else {
						listener.onDisabled();
					}
				});
			}
		});
	}

	@Override
	public boolean isEnabled() {
		return this.services.preference.isWebClientEnabled()
			&& !AppRestrictionUtil.isWebDisabled(this.services.appContext)
			&& this.services.license.isLicensed();
	}

	@Override
	@NonNull public List<WebClientSessionModel> getAllSessionModels() {
		return this.services.database.getWebClientSessionModelFactory().getAll();
	}

	@Override
	@Nullable public synchronized SessionInstanceService getInstanceService(
		@NonNull final String publicKeySha256String,
		boolean createIfNotExists
	) {
		// Look up session instance
		SessionInstanceContainer container = Functional.select(
			this.instances,
			new IPredicateNonNull<SessionInstanceContainer>() {
				@Override
				@AnyThread
				public boolean apply(@NonNull final SessionInstanceContainer container) {
					final WebClientSessionModel model = container.instance.getModel();
					return TestUtil.compare(model.getKey256(), publicKeySha256String);
				}
			}
		);

		// If necessary, create new instance
		SessionInstanceService instance = null;
		if (container != null) {
			instance = container.instance;
		} else if (createIfNotExists) {
			final WebClientSessionModel model = this.services.database
				.getWebClientSessionModelFactory()
				.getByKey256(publicKeySha256String);
			if (model != null) {
				instance = this.getInstanceService(model, true);
			}
		}
		return instance;
	}

	@Override
	@Nullable public synchronized SessionInstanceService getInstanceService(
		@NonNull final WebClientSessionModel model,
		boolean createIfNotExists
	) {
		if (this.instances.containsKey(model.getId())) {
			final SessionInstanceContainer container = this.instances.get(model.getId());
			return container != null ? container.instance : null;
		} else if (!createIfNotExists) {
			return null;
		}
		final SessionInstanceContainer container = this.createInstanceService(model);
		this.instances.put(model.getId(), container);
		return container.instance;
	}

	@NonNull private SessionInstanceContainer createInstanceService(@NonNull final WebClientSessionModel model) {
		// Create session instance
		final SessionInstanceService instance = new SessionInstanceServiceImpl(
			this.services, this.cryptoProvider, model, this.handler);

		// Create service events listener
		final WebClientServiceListener listener = new WebClientServiceListener() {
			@Override
			@AnyThread
			public void onStarted(
				@NonNull final WebClientSessionModel eventModel,
				@NonNull final byte[] permanentKey,
				@NonNull final String browser
			) {
				synchronized (SessionServiceImpl.this) {
					if (!this.isLocalModel(eventModel)) {
						return;
					}

					// Update the session model
					model
						.setKey(permanentKey)
						.setClientDescription(browser)
						.setState(WebClientSessionModel.State.AUTHORIZED);

					// Save a hash of the permanent key
					try {
						model
							.setKey256(Utils.byteArrayToSha256HexString(permanentKey));
					} catch (NoSuchAlgorithmException e) {
						// Should never happen
						logger.error("Exception", e);
					}

					SessionServiceImpl.this.update(model);
				}
			}

			@Override
			@AnyThread
			public void onKeyPersisted(@NonNull WebClientSessionModel eventModel, final boolean persisted) {
				synchronized (SessionServiceImpl.this) {
					if (!this.isLocalModel(eventModel)) {
						return;
					}

					model.setPersistent(persisted);
					SessionServiceImpl.this.update(model);
				}
			}

			@Override
			@AnyThread
			public void onStateChanged(
				@NonNull final WebClientSessionModel eventModel,
				@NonNull final WebClientSessionState oldState,
				@NonNull final WebClientSessionState newState
			) {
				synchronized (SessionServiceImpl.this) {
					if (!this.isLocalModel(eventModel)) {
						return;
					}

					// Update last connection date when connected
					if (newState == WebClientSessionState.CONNECTED) {
						SessionServiceImpl.this.update(model.setLastConnection(new Date()));
					}
				}
			}

			@Override
			@AnyThread
			public void onPushTokenChanged(
				@NonNull final WebClientSessionModel eventModel,
				@Nullable final String newPushToken
			) {
				synchronized (SessionServiceImpl.this) {
					if (!this.isLocalModel(eventModel)) {
						return;
					}

					// Save the NEW gcm token
					SessionServiceImpl.this.update(model.setPushToken(newPushToken));
				}
			}

			@Override
			@AnyThread
			public void onStopped(
				@NonNull final WebClientSessionModel eventModel,
				@NonNull final DisconnectContext reason
			) {
				synchronized (SessionServiceImpl.this) {
					if (!this.isLocalModel(eventModel)) {
						return;
					}

					// Determine whether the session needs to be updated or deleted:
					//
					// 1. The disconnect reason indicates that the session has been removed by
					//    the peer or is being removed by us,
					// 2. We requested a disconnect and the model is not persistent.
					boolean removed = false;
					if (reason.shouldForget() || (reason instanceof DisconnectContext.ByUs && !model.isPersistent())) {
						removed = SessionServiceImpl.this.services.database.getWebClientSessionModelFactory()
							.delete(model) > 0;
					}

					// Update model
					if (model.getState() == WebClientSessionModel.State.INITIALIZING) {
						model.setState(WebClientSessionModel.State.ERROR);
					} else if (!removed) {
						model.setLastConnection(new Date());
					}
					SessionServiceImpl.this.update(model);

					// Unregister the listener and remove the session instance
					final SessionInstanceContainer container = SessionServiceImpl.this.instances.get(model.getId());
					if (container != null) {
						WebClientListenerManager.serviceListener.remove(container.listener);
						if (container.instance.isRunning()) {
							// This indicates a bug
							logger.error("Cannot remove running session instance!");
						} else {
							SessionServiceImpl.this.instances.remove(model.getId());
						}
					} else {
						logger.error("No session instance for session model {}", model.getId());
					}

					// Raise 'removed' event if session has been removed
					if (removed) {
						WebClientListenerManager.sessionListener.handle(new ListenerManager.HandleListener<WebClientSessionListener>() {
							@Override
							@AnyThread
							public void handle(WebClientSessionListener listener) {
								listener.onRemoved(model);
							}
						});
					}
				}
			}

			/**
			 * Return whether we want to handle events for the specified
			 * model.
			 *
			 * Return true if this session is the session we're managing.
			 */
			@AnyThread
			private boolean isLocalModel(@NonNull final WebClientSessionModel eventModel) {
				return eventModel.getId() == model.getId();
			}
		};

		// Register service events listener
		WebClientListenerManager.serviceListener.add(listener);

		// Return as container
		return new SessionInstanceContainer(instance, listener);
	}

	/**
	 * Update session model and fire the 'modified' event.
	 */
	private synchronized void update(@NonNull final WebClientSessionModel model) {
		if (this.services.database.getWebClientSessionModelFactory().createOrUpdate(model)) {
			WebClientListenerManager.sessionListener.handle(new ListenerManager.HandleListener<WebClientSessionListener>() {
				@Override
				@AnyThread
				public void handle(WebClientSessionListener listener) {
					listener.onModified(model);
				}
			});
		}
	}

	@Override
	@NonNull public synchronized WebClientSessionModel create(
		@NonNull byte[] permanentyKey,
		@NonNull byte[] authToken,
		@NonNull String saltyRtcHost,
		int saltyRtcPort,
		@Nullable byte[] serverKey,
		boolean isPermanent,
		boolean isSelfHosted,
		@Nullable String affiliationId
	) {

		// Create and save a database model
		final WebClientSessionModel model = new WebClientSessionModel()
			.setState(WebClientSessionModel.State.INITIALIZING)
			.setSaltyRtcPort(saltyRtcPort)
			.setSaltyRtcHost(saltyRtcHost)
			.setServerKey(serverKey)
			.setPersistent(isPermanent)
			.setPushToken(this.services.preference.getPushToken())
			.setSelfHosted(isSelfHosted);
		this.services.database.getWebClientSessionModelFactory().createOrUpdate(model);

		// Dispatch 'create' event
		WebClientListenerManager.sessionListener.handle(new ListenerManager.HandleListener<WebClientSessionListener>() {
			@Override
			@AnyThread
			public void handle(WebClientSessionListener listener) {
				listener.onCreated(model);
			}
		});

		// Automatically enable Threema Web
		if (!this.isEnabled()) {
			this.setEnabled(true);
		}

		// Get instance service
		final SessionInstanceService instance = this.getInstanceService(model, true);
		if (instance == null) {
			throw new IllegalStateException("Could not get session instance service");
		}

		// Start session asynchronously and return the model
		this.handler.post(new Runnable() {
			@Override
			@WorkerThread
			public void run() {
				// Start session
				instance.start(permanentyKey, authToken, affiliationId);
			}
		});
		return model;
	}

	@Override
	public long getRunningSessionsCount() {
		final Stream<SessionInstanceContainer> stream;
		synchronized(this) {
			stream = StreamSupport.stream(this.instances.values());
		}
		try {
			return CompletableFuture.supplyAsync(new Supplier<Long>() {
				@Override
				@WorkerThread
				@NonNull public Long get() {
					return stream
						.filter(container -> container.instance.isRunning())
						.count();
				}
			}, this.handler.getExecutor()).get();
		} catch (InterruptedException | ExecutionException error) {
			logger.error("Unable to count running sessions", error);
			return 0;
		}
	}

	@Override
	@NonNull public WebClientSessionState getState(@NonNull final WebClientSessionModel model) {
		SessionInstanceService instance = this.getInstanceService(model, false);
		if (instance == null) {
			return WebClientSessionState.DISCONNECTED;
		}

		// Dispatch 'isRunning' from the worker thread
		try {
			return CompletableFuture.supplyAsync(new Supplier<WebClientSessionState>() {
				@Override
				@WorkerThread
				public WebClientSessionState get() {
					return instance.getState();
				}
			}, this.handler.getExecutor()).get();
		} catch (InterruptedException | ExecutionException error) {
			logger.error("Unable to retrieve session state", error);
			return WebClientSessionState.ERROR;
		}
	}

	@Override
	public boolean isRunning(@NonNull final WebClientSessionModel model) {
		SessionInstanceService instance = this.getInstanceService(model, false);
		if (instance == null) {
			return false;
		}

		// Dispatch 'isRunning' from the worker thread
		try {
			return CompletableFuture.supplyAsync(new Supplier<Boolean>() {
				@Override
				@WorkerThread
				public Boolean get() {
					return instance.isRunning();
				}
			}, this.handler.getExecutor()).get();
		} catch (InterruptedException | ExecutionException error) {
			logger.error("Unable to check if session is running", error);
			return false;
		}
	}

	@Override
	public synchronized void stop(@NonNull final WebClientSessionModel model, @NonNull final DisconnectContext reason) {
		final SessionInstanceContainer container;

		// Remove and stop session instance
		logger.debug("Removing session instance for model {}", model.getId());
		container = this.instances.remove(model.getId());
		if (container != null) {
			this.handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					container.instance.stop(reason);
				}
			});
		} else {
			logger.warn("No session instance for session model {}", model.getId());
		}

		// Remove session model (if explicitly requested)
		// Note: We need to handle this here since the session may not be running. Thus, the
		//       stopped event may not be raised. Furthermore, we want to have the session removed
		//       in the UI ASAP.
		if (reason.shouldForget()) {
			// Remove and raise 'removed' event if the session has been removed
			final boolean removed = this.services.database.getWebClientSessionModelFactory().delete(model) > 0;
			if (removed) {
				WebClientListenerManager.sessionListener.handle(new ListenerManager.HandleListener<WebClientSessionListener>() {
					@Override
					@AnyThread
					public void handle(WebClientSessionListener listener) {
						listener.onRemoved(model);
					}
				});
			}
		}
	}

	@Override
	public synchronized void stopAll(@NonNull final DisconnectContext reason) {
		for (WebClientSessionModel model : this.getAllSessionModels()) {
			this.stop(model, reason);
		}
	}
}

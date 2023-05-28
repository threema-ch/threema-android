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

package ch.threema.app.webclient.services;

import android.content.Context;
import android.widget.Toast;

import org.saltyrtc.client.crypto.CryptoException;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.NotificationService;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.listeners.WebClientWakeUpListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.manager.WebClientServiceManager;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.services.instance.SessionInstanceService;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKey;

@WorkerThread
public class SessionWakeUpServiceImpl implements SessionWakeUpService {
	private enum StartResult {
		OK,
		SERVICE_NOT_AVAILABLE,
		SERVICE_DISABLED,
		HOST_CONSTRAINED_BY_MDM,
		SESSION_UNKNOWN,
		RESTARTED,
		ALREADY_STARTED,
		EXCEPTION,
	}

	// Timeouts
	public static final int DEFAULT_WAKEUP_SECONDS = 60;
	public static final int DISCONNECT_WAKEUP_SECONDS = 20;

	// Logger
	@NonNull private static final Logger logger = LoggingUtil.getThreemaLogger("SessionWakeUpServiceImpl");

	// Singleton
	@Nullable private static SessionWakeUpService instance = null;

	// Service manager
	@Nullable private ServiceManager serviceManager = null;

	// Master key. Do not access this directly, use getMasterKey instead.
	@Nullable private final MasterKey masterKey;

	// Queue of pending wakeups. Do not access this directly, use getPendingWakeUps instead.
	private final Queue<PendingWakeup> pendingWakeUps = new ArrayDeque<>();

	@AnyThread
	@NonNull public synchronized static SessionWakeUpService getInstance() {
		if (instance == null) {
			instance = new SessionWakeUpServiceImpl(ThreemaApplication.getMasterKey());
		}
		return instance;
	}

	@AnyThread
	public synchronized static void clear() {
		instance = null;
	}

	@AnyThread
	private SessionWakeUpServiceImpl(@Nullable MasterKey masterKey) {
		this.masterKey = masterKey;
	}

	@NonNull private Queue<PendingWakeup> getPendingWakeUps() {
		return this.pendingWakeUps;
	}

	@AnyThread
	@Nullable private MasterKey getMasterKey() {
		return this.masterKey;
	}

	private Context getContext() {
		return ThreemaApplication.getAppContext();
	}

	/**
	 * Returns true if...
	 *
	 * - the service manager is available, and
	 * - the master key is available.
	 */
	@AnyThread
	private synchronized boolean isAvailable() {
		if (this.serviceManager == null) {
			this.serviceManager = ThreemaApplication.getServiceManager();
		}
		return this.serviceManager != null && this.getMasterKey() != null;
	}

	/**
	 * Returns the session service if...
	 *
	 * - the service manager is available,
	 * - the master key is available, and
	 * - the session service is available.
	 *
	 * Otherwise, it raises ThreemaException.
	 */
	@NonNull private SessionService getSessionService() throws ThreemaException {
		if (!this.isAvailable()) {
			throw new ThreemaException("Service manager or master key unavailable");
		}
		return Objects.requireNonNull(serviceManager).getWebClientServiceManager().getSessionService();
	}

	@Override
	@AnyThread
	public synchronized void resume(
		@NonNull final String publicKeySha256String,
		final int version,
		@Nullable final String affiliationId
	) {
		logger.info("Attempting to resume session (public-key={}, version={}, affiliation={})",
			publicKeySha256String, version, affiliationId);
		if (!this.isAvailable()) {
			logger.error("Service unavailable");
			return;
		}

		// Validate protocol version
		if (Protocol.PROTOCOL_VERSION != version) {
			logger.error("Unexpected protocol version: {}", version);
			WebClientListenerManager.wakeUpListener.handle(new ListenerManager.HandleListener<WebClientWakeUpListener>() {
				@Override
				@AnyThread
				public void handle(WebClientWakeUpListener listener) {
					listener.onProtocolError();
				}
			});
		}

		// Ensure the web client service manager is available
		final WebClientServiceManager manager;
		try {
			manager = Objects.requireNonNull(this.serviceManager).getWebClientServiceManager();
		} catch (ThreemaException error) {
			logger.error("Cannot access web client service manager", error);
			return;
		}
		if (this.serviceManager == null) {
			logger.error("Cannot resume or schedule wakeup, web client service manager unavailable");
			return;
		}

		// Handle locked master key
		final MasterKey masterKey = this.getMasterKey();
		if (masterKey != null && masterKey.isLocked()) {
			logger.warn("Master key is locked, scheduling wakeup");
			manager.getHandler().post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					SessionWakeUpServiceImpl.this.schedule(
						publicKeySha256String, affiliationId, DEFAULT_WAKEUP_SECONDS * 1000);
				}
			});
			return;
		}

		// Try to start session on the worker thread
		manager.getHandler().post(new Runnable() {
			@Override
			@WorkerThread
			public void run() {
				switch (SessionWakeUpServiceImpl.this.start(publicKeySha256String, affiliationId)) {
					case SERVICE_DISABLED:
						logger.warn("Threema Web service is disabled, store pending wakeup");
						RuntimeUtil.runOnUiThread(() -> {
							// Show a toast
							final Context context = SessionWakeUpServiceImpl.this.getContext();
							final String text = context.getString(R.string.webclient_cannot_restore) + ": "
								+ context.getString(R.string.webclient_disabled);
							final Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
							toast.show();
						});
						SessionWakeUpServiceImpl.this.schedule(publicKeySha256String, affiliationId, DEFAULT_WAKEUP_SECONDS * 1000);
						break;
					case SERVICE_NOT_AVAILABLE:
						logger.warn("Service not available, store pending wakeup");
						SessionWakeUpServiceImpl.this.schedule(publicKeySha256String, affiliationId, DEFAULT_WAKEUP_SECONDS * 1000);
						break;
					case SESSION_UNKNOWN:
						logger.warn("Session unknown, ignoring");
						break;
					case RESTARTED:
						logger.info("Session has been restarted");
						break;
					case ALREADY_STARTED:
						logger.warn("Already started, store pending wakeup");
						SessionWakeUpServiceImpl.this.schedule(publicKeySha256String, affiliationId, DISCONNECT_WAKEUP_SECONDS * 1000);
						break;
					case HOST_CONSTRAINED_BY_MDM:
						logger.warn("Could not resume session, host constrained by administrator");
						SessionWakeUpServiceImpl.this.showWarningNotification(R.string.webclient_constrained_by_mdm);
						break;
					case EXCEPTION:
						logger.error("Exception while trying to wake up session");
						break;
					case OK:
						logger.info("Session has been started");
						break;
					default:
						logger.error("Warning: Unhandled StartResult!");
						break;
				}
			}
		});
	}

	/**
	 * Schedule a wakeup for the specific session.
	 */
	private void schedule(
		@NonNull final String publicKeySha256String,
		@Nullable final String affiliationId,
		final int lifetimeMs
	) {
		if (lifetimeMs > 0) {
			final long expiration = System.currentTimeMillis() + lifetimeMs;
			final Queue<PendingWakeup> pendingQueue = this.getPendingWakeUps();

			// Update existing wakeup (if any)
			for (PendingWakeup pending : pendingQueue) {
				if (pending.publicKeySha256String.equals(publicKeySha256String)) {
					logger.info("Wakeup already scheduled, refreshing expiration +{} ms", lifetimeMs);
					pending.expiration = expiration;
					pending.affiliationId = affiliationId;
					return;
				}
			}

			// Add new wakeup
			logger.info("Wakeup scheduled, expiration +{} ms", lifetimeMs);
			pendingQueue.add(new PendingWakeup(publicKeySha256String, affiliationId, expiration));
		}
	}

	private StartResult start(@NonNull final String publicKeySha256String, @Nullable final String affiliationId) {
		// Get session service
		final SessionService service;
		try {
			service = this.getSessionService();
		} catch (ThreemaException error) {
			logger.debug("Service unavailable: {}", error.getMessage());
			return StartResult.SERVICE_NOT_AVAILABLE;
		}

		// Ensure the web client is enabled
		if (!service.isEnabled()) {
			return StartResult.SERVICE_DISABLED;
		}

		// Ensure there is a session instance
		logger.debug("Retrieving session instance");
		final SessionInstanceService webClientInstanceService =
			service.getInstanceService(publicKeySha256String, true);
		if (webClientInstanceService == null) {
			return StartResult.SESSION_UNKNOWN;
		}

		// Check if the session is still running
		if (webClientInstanceService.isRunning()) {
			// Stop current session if affiliation ID has changed
			if (webClientInstanceService.needsRestart(affiliationId)) {
				logger.info("Restarting session", affiliationId);
				this.schedule(publicKeySha256String, affiliationId, DISCONNECT_WAKEUP_SECONDS * 1000);
				webClientInstanceService.stop(DisconnectContext.byPeer(DisconnectContext.REASON_SESSION_REPLACED));
				return StartResult.RESTARTED;
			} else {
				logger.debug("Session already started", affiliationId);
				return StartResult.ALREADY_STARTED;
			}
		}

		// MDM constraints
		if (ConfigUtils.isWorkRestricted()) {
			final String hostname = webClientInstanceService.getModel().getSaltyRtcHost();
			if (!AppRestrictionUtil.isWebHostAllowed(this.getContext(), hostname)) {
				return StartResult.HOST_CONSTRAINED_BY_MDM;
			}
		}

		// Resume the session
		logger.info("Resuming session", affiliationId);
		try {
			webClientInstanceService.resume(affiliationId);
		} catch (CryptoException error) {
			logger.error("Unable to resume session", error);
			return StartResult.EXCEPTION;
		}
		return StartResult.OK;
	}

	/**
	 * Process pending wakeups asynchronously.
	 */
	@Override
	@AnyThread
	public synchronized void processPendingWakeupsAsync() {
		if (!this.isAvailable()) {
			logger.error("Service unavailable");
			return;
		}

		// Ensure the web client service manager is available
		final WebClientServiceManager manager;
		try {
			manager = Objects.requireNonNull(this.serviceManager).getWebClientServiceManager();
		} catch (ThreemaException error) {
			logger.error("Cannot access web client service manager", error);
			return;
		}

		// Process pending wakeups on worker thread
		manager.getHandler().post(new Runnable() {
			@Override
			@WorkerThread
			public void run() {
				SessionWakeUpServiceImpl.this.processPendingWakeups();
			}
		});
	}

	/**
	 * Process pending wakeups.
	 */
	@Override
	public void processPendingWakeups() {
		final Queue<PendingWakeup> pendingQueue = this.getPendingWakeUps();
		logger.info("Process {} pending wakeups", pendingQueue.size());

		// Try to resume each pending session
		PendingWakeup pending;
		final List<PendingWakeup> failed = new ArrayList<>();
		final long now = System.currentTimeMillis();
		while ((pending = pendingQueue.poll()) != null) {
			// Check if expired
			if (now > pending.expiration) {
				logger.info("Pending wakeup expired, ignoring");
				continue;
			}

			// Try a wakeup
			final MasterKey masterKey = this.getMasterKey();
			if (masterKey != null && masterKey.isLocked()) {
				logger.error("Cannot wake up {}, master key is locked", pending.publicKeySha256String);
				failed.add(pending);
				continue;
			}
			try {
				// Get session service
				final SessionService sessionService = this.getSessionService();
				if (!sessionService.isEnabled()) {
					logger.error("Cannot wake up {}, session service is disabled", pending.publicKeySha256String);
					failed.add(pending);
					continue;
				}

				// Get session instance
				final SessionInstanceService webClientInstanceService =
					sessionService.getInstanceService(pending.publicKeySha256String.trim(), true);
				if (webClientInstanceService == null) {
					logger.error("Cannot wake up {}, session instance not found, remove from pending list", pending.publicKeySha256String);
					continue;
				}

				// Check MDM constraints
				if (ConfigUtils.isWorkRestricted()) {
					final String hostname = webClientInstanceService.getModel().getSaltyRtcHost();
					if (!AppRestrictionUtil.isWebHostAllowed(this.getContext(), hostname)) {
						logger.warn("Cannot wake up session {}, disabled by administrator", pending.publicKeySha256String);
						continue;
					}
				}

				// Resume session instance
				webClientInstanceService.resume(pending.affiliationId);
				logger.info("Resumed session {} from pending wakeup list", pending.publicKeySha256String);
			} catch (CryptoException error) {
				logger.error("Exception while waking up session", error);
				// Note: Don't reschedule here as this is not recoverable!
			} catch (ThreemaException error) {
				logger.error("Exception while waking up session", error);
				failed.add(pending);
			}
		}

		// Reschedule failed wakeup attempts
		if (!failed.isEmpty()) {
			logger.info("Re-scheduling {} pending wakeups", failed.size());
			pendingQueue.addAll(failed);
			failed.clear();
		}
	}

	/**
	 * Discard pending wakeups.
	 */
	@Override
	public void discardPendingWakeups() {
		final Queue<PendingWakeup> pendingQueue = this.getPendingWakeUps();
		logger.info("Discarding {} pending wakeups", pendingQueue.size());
		pendingQueue.clear();
	}

	private void showWarningNotification(@StringRes int message) {
		NotificationService notificationService = Objects.requireNonNull(this.serviceManager).getNotificationService();
		if (notificationService != null) {
			final String msg = this.getContext().getString(R.string.webclient_cannot_restore) + ": "
				+ this.getContext().getString(message);
			notificationService.showWebclientResumeFailed(msg);
		}
	}
}

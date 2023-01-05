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
import android.os.Build;
import android.os.PowerManager;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.app.BuildConfig;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.WebClientSessionModel;

/**
 * Acquire both a CPU wakelock and a network connection to the Threema server
 * while Threema Web is running.
 */
@AnyThread
public class WakeLockServiceImpl implements WakeLockService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("WakeLockService");

	private static final String WAKELOCK_TAG = BuildConfig.APPLICATION_ID + ":webClientWakeLock";
	private static final String LIFETIME_SERVICE_TAG = "wcWakeLockService";

	private final Context appContext;
	private final LifetimeService lifetimeService;
	private volatile boolean lifetimeServiceConnectionAcquired = false;

	/**
	 * All acquired webclient session
	 */
	private final List<Integer> acquiredSessionIds = new ArrayList<>();

	/**
	 * The webclient wakelock.
	 */
	private PowerManager.WakeLock wakeLock;

	public WakeLockServiceImpl(@NonNull Context appContext, @NonNull LifetimeService lifetimeService) {
		this.appContext = appContext;
		this.lifetimeService = lifetimeService;
	}

	@Override
	public synchronized boolean acquire(WebClientSessionModel session) {
		logger.debug("acquire webclient wakelock for session {}", session.getId());
		if (!this.acquiredSessionIds.contains(session.getId())) {
			this.acquiredSessionIds.add(session.getId());
		}
		return this.execute();
	}

	@Override
	public synchronized boolean release(WebClientSessionModel session) {
		logger.debug("release webclient wakelock for session {}", session.getId());
		if (this.acquiredSessionIds.contains(session.getId())) {
			this.acquiredSessionIds.remove((Integer)session.getId());
		}
		return this.execute();
	}

	@Override
	public synchronized boolean releaseAll() {
		this.acquiredSessionIds.clear();
		return this.execute();
	}

	@Override
	public synchronized boolean isHeld() {
		return this.wakeLock != null
				&& this.wakeLock.isHeld();
	}

	private synchronized boolean execute() {
		if (this.acquiredSessionIds.size() > 0) {
			// Create wakelock if it wasn't instantiated yet
			if (this.wakeLock == null) {
				logger.debug("create new wakelock");
				PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
				this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
			}

			// Acquire wakelock if not already held
			if (!this.wakeLock.isHeld()) {
				if (ConfigUtils.isNokiaDevice() && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
					// Do not hold wake lock for longer than 15 minutes to prevent evenwell power "saver" from killing the app
					logger.warn("Limiting wake lock to 15 minutes, to avoid being killed by Evenwell");
					this.wakeLock.acquire(15 * DateUtils.MINUTE_IN_MILLIS);
				} else {
					this.wakeLock.acquire();
				}
				logger.debug("acquired wakelock");
			} else {
				logger.debug("already acquired wakelock");
			}

			// Acquire network connection if necessary
			if (!this.lifetimeServiceConnectionAcquired) {
				// Note: This connection is unpauseable, so that even messages without push flag
				//       are delivered immediately. Since we have a wakelock anyways, the overhead
				//       and battery impact is negligible.
				this.lifetimeService.acquireUnpauseableConnection(LIFETIME_SERVICE_TAG);
				this.lifetimeServiceConnectionAcquired = true;
				logger.debug("acquired network connection");
			}

			return true;
		} else {
			// Release wakelock if held
			if (this.wakeLock != null && this.wakeLock.isHeld()) {
				this.wakeLock.release();
				// to be sure, remove the wakelock
				this.wakeLock = null;
				logger.debug("released wakelock");
			} else {
				logger.debug("already released wakelock");
			}

			// Release network connection if acquired
			if (this.lifetimeServiceConnectionAcquired) {
				this.lifetimeService.releaseConnectionLinger(LIFETIME_SERVICE_TAG, 5000);
				this.lifetimeServiceConnectionAcquired = false;
				logger.debug("released network connection");
			}

			return false;
		}
	}
}

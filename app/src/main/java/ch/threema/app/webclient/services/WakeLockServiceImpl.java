/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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
import android.os.PowerManager;
import android.text.format.DateUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.BuildConfig;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.storage.models.WebClientSessionModel;

public class WakeLockServiceImpl implements WakeLockService {
	private static final Logger logger = LoggerFactory.getLogger(WakeLockServiceImpl.class);
	private static final String WAKELOCK_TAG = BuildConfig.APPLICATION_ID + ":webClientWakeLock";
	private final Context context;
	/**
	 * all acquired webclient session
	 */
	private final List<Integer> acquiredSessionIds = new ArrayList<>();

	/**
	 * the webclient wakelock
	 */
	private PowerManager.WakeLock wakeLock;

	public WakeLockServiceImpl(Context context) {
		this.context = context;
	}

	@Override
	public boolean acquire(WebClientSessionModel session) {
		logger.debug("acquire webclient wakelock for session " + session.getId());
		if (!this.acquiredSessionIds.contains(session.getId())) {
			this.acquiredSessionIds.add(session.getId());
		}

		return this.execute();
	}

	@Override
	public boolean release(WebClientSessionModel session) {
		logger.debug("release webclient wakelock for session " + session.getId());
		if (this.acquiredSessionIds.contains(session.getId())) {
			this.acquiredSessionIds.remove((Integer)session.getId());
		}

		return this.execute();
	}

	@Override
	public boolean releaseAll() {
		this.acquiredSessionIds.clear();
		return this.execute();
	}

	@Override
	public boolean isHeld() {
		return this.wakeLock != null
				&& this.wakeLock.isHeld();
	}

	private boolean execute() {
		if (this.acquiredSessionIds.size() > 0) {
			if (this.wakeLock == null) {
				logger.debug("create new wakelock");
				PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
				this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
			}

			if(!this.wakeLock.isHeld()) {
				if (ConfigUtils.isNokiaDevice()) {
					// do not hold wake lock for longer than 15 minutes to prevent evenwell power "saver" from killing the app
					this.wakeLock.acquire(15 * DateUtils.MINUTE_IN_MILLIS);
				} else {
					this.wakeLock.acquire();
				}
				logger.debug("acquired");
			} else {
				logger.debug("already acquired");
			}
			return true;
		}
		else {
			if (this.wakeLock != null && this.wakeLock.isHeld()) {
				this.wakeLock.release();
				// to be sure, remove the wakelock
				this.wakeLock = null;
				logger.debug("released");
			} else {
				logger.debug("already released");
			}
			return false;
		}
	}
}

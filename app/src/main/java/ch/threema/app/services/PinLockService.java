/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.receivers.AlarmManagerBroadcastReceiver;
import ch.threema.app.utils.WidgetUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE;

public class PinLockService implements LockAppService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("PinLockService");

	private final Context context;
	private final PreferenceService preferencesService;
	private NotificationService notificationService;
	private final UserService userService;
	private boolean locked;
	private final AlarmManager alarmManager;
	private PendingIntent lockTimerIntent;
	private long lockTimeStamp = 0;
	private final CopyOnWriteArrayList<OnLockAppStateChanged> lockAppStateChangedItems = new CopyOnWriteArrayList<>();

	public PinLockService(Context context, PreferenceService preferencesService, UserService userService) {
		this.context = context;
		this.preferencesService = preferencesService;
		this.userService = userService;
		this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		this.locked = preferencesService.isAppLockEnabled();
	}

	@Override
	public boolean isLockingEnabled() {
		logger.debug("isLockingEnabled");

		return (preferencesService.isAppLockEnabled() &&
				this.userService.hasIdentity());
	}

	@Override
	public boolean unlock(String pin) {
		logger.debug("unlock");

		if ((PreferenceService.LockingMech_PIN.equals(preferencesService.getLockMechanism()) &&
				this.preferencesService.isPinCodeCorrect(pin)) ||
				PreferenceService.LockingMech_SYSTEM.equals(preferencesService.getLockMechanism()) ||
				PreferenceService.LockingMech_BIOMETRIC.equals(preferencesService.getLockMechanism())) {
			this.resetLockTimer(false);
			this.updateState(false);
			this.lockTimeStamp = 0;
			return !this.locked;
		}
		return false;
	}

	@Override
	public void lock() {
		logger.debug("lock");

		if (isLockingEnabled()) {
			this.updateState(true);

			try {
				if (this.notificationService == null) {
					notificationService = ThreemaApplication.getServiceManager().getNotificationService();
				}
				if (this.notificationService != null) {
					notificationService.cancelConversationNotificationsOnLockApp();
				}
			} catch (Exception e){
				logger.warn("Could not cancel conversation notifications when locking app:");
			}
		}
	}

	@Override
	public boolean checkLock() {
		if (lockTimeStamp > 0 && System.currentTimeMillis() > lockTimeStamp) {
			lock();
		}
		return true;
	}

	private void updateState(boolean locked) {
		logger.info("update locked stated to: {} ", isLocked());
		if(this.locked != locked) {

			this.locked = locked;

			synchronized (this.lockAppStateChangedItems) {
				ArrayList<OnLockAppStateChanged> toRemove = new ArrayList<>();

				for (OnLockAppStateChanged c: this.lockAppStateChangedItems) {
					if (c.changed(locked)) {
						toRemove.add(c);
					}
				}
				this.lockAppStateChangedItems.removeAll(toRemove);
			}

			// update widget
			WidgetUtil.updateWidgets(context);
		}
	}

	@Override
	public boolean isLocked() {
		return this.locked;
	}

	@Override
	public LockAppService resetLockTimer(boolean restartAfterReset) {

		if(this.lockTimerIntent != null) {
			this.lockTimeStamp = 0;
			alarmManager.cancel(this.lockTimerIntent);
		}

		if(restartAfterReset) {
			int time = this.preferencesService.getPinLockGraceTime();
			if(time > 0) {
				Intent lockingIntent = new Intent(context, AlarmManagerBroadcastReceiver.class);
				lockingIntent.putExtra(LifetimeServiceImpl.REQUEST_CODE_KEY, LifetimeServiceImpl.REQUEST_LOCK_APP);
				this.lockTimerIntent = PendingIntent.getBroadcast(context, LifetimeServiceImpl.REQUEST_LOCK_APP, lockingIntent, PENDING_INTENT_FLAG_MUTABLE);
				this.lockTimeStamp = System.currentTimeMillis() + time * DateUtils.SECOND_IN_MILLIS;
				alarmManager.set(AlarmManager.RTC_WAKEUP, this.lockTimeStamp, this.lockTimerIntent);
			} else {
				this.lockTimeStamp = 0;
			}
		}

		return this;
	}

	@Override
	public void addOnLockAppStateChanged(OnLockAppStateChanged c) {
		synchronized (this.lockAppStateChangedItems) {
			this.lockAppStateChangedItems.add(c);
		}
	}

	@Override
	public void removeOnLockAppStateChanged(OnLockAppStateChanged c) {
		synchronized (this.lockAppStateChangedItems) {
			int index = this.lockAppStateChangedItems.indexOf(c);
			if (index >= 0) {
				this.lockAppStateChangedItems.remove(index);
			}
		}
	}
}

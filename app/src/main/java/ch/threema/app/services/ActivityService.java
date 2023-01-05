/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.slf4j.Logger;

import java.lang.ref.WeakReference;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.PinLockActivity;
import ch.threema.app.utils.BiometricUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKey;

public class ActivityService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ActivityService");
	private final Context context;
	private final LockAppService lockAppService;
	private final PreferenceService preferenceService;
	private WeakReference<Activity> currentActivityReference = new WeakReference<>(null);

	public ActivityService(final Context context, LockAppService lockAppService, PreferenceService preferenceService) {
		this.context = context;
		this.lockAppService = lockAppService;
		this.preferenceService = preferenceService;

		this.lockAppService.addOnLockAppStateChanged(new LockAppService.OnLockAppStateChanged() {
			@Override
			public boolean changed(final boolean locked) {
				handLockedState(locked);
				return false;
			}
		});
	}

	private synchronized void handLockedState(final boolean locked) {
		logger.debug("handLockedState currentActivity: " + currentActivityReference.get());

		MasterKey masterKey = ThreemaApplication.getMasterKey();
		if (masterKey != null && masterKey.isLocked()) {
			return;
		}

		boolean isPinLock = currentActivityReference == null || currentActivityReference.get() == null || currentActivityReference.get() instanceof PinLockActivity;

		if (!isPinLock) {
			RuntimeUtil.runOnUiThread(() -> {
				logger.info("handLockedState - locked = {}", locked);

				if (locked) {
					if (currentActivityReference.get() != null) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
								(preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_SYSTEM) ||
										preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_BIOMETRIC))) {
							BiometricUtil.showUnlockDialog(currentActivityReference.get(), false, 0, null);
						} else {
							try {
								Intent intent = new Intent(context, PinLockActivity.class);
								currentActivityReference.get().startActivity(intent);
								currentActivityReference.get().overridePendingTransition(0, 0);
							} catch (Exception x) {
								logger.error("Exception", x);
							}
						}
					}
				}
			});
		}
	}

	public void resume(Activity currentActivity) {
		this.currentActivityReference = new WeakReference<>(currentActivity);

		if (this.lockAppService.checkLock()) {
			if (this.timeLocking()) {
				this.handLockedState(true);
			}
		}
	}

	public void pause(Activity pausedActivity) {
		if(this.currentActivityReference.get() == pausedActivity) {
			this.currentActivityReference.clear();
		}
	}

	public void destroy(Activity destroyedActivity) {
		if(this.currentActivityReference.get() == destroyedActivity) {
			this.currentActivityReference.clear();
		}
	}

	public void userInteract(Activity interactedActivity) {
		this.currentActivityReference.clear();
		this.currentActivityReference = new WeakReference<>(interactedActivity);
		this.timeLocking();
	}

	private boolean timeLocking() {
		logger.debug("timeLocking");
		if(this.lockAppService.isLockingEnabled()) {
			if(!this.lockAppService.isLocked()) {
				this.lockAppService.resetLockTimer(true);
			}
			else {
				//hand locked state to resuming activity
				return true;
			}
		}
		return false;
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import ch.threema.app.activities.PinLockActivity;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.BiometricUtil;
import ch.threema.app.utils.RuntimeUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.localcrypto.MasterKeyProvider;

public class ActivityService {
    private static final Logger logger = getThreemaLogger("ActivityService");

    private final Context context;
    private final LockAppService lockAppService;
    private final PreferenceService preferenceService;
    @NonNull
    private final MasterKeyProvider masterKeyProvider;

    private WeakReference<Activity> currentActivityReference = new WeakReference<>(null);

    public ActivityService(
        final Context context,
        LockAppService lockAppService,
        PreferenceService preferenceService,
        @NonNull MasterKeyProvider masterKeyProvider
    ) {
        this.context = context;
        this.lockAppService = lockAppService;
        this.preferenceService = preferenceService;
        this.masterKeyProvider = masterKeyProvider;

        this.lockAppService.addOnLockAppStateChanged(locked -> {
            handleLockedState(locked);
            return false;
        });
    }

    private synchronized void handleLockedState(final boolean locked) {
        logger.debug("handleLockedState currentActivity: " + currentActivityReference.get());

        if (masterKeyProvider.isLocked()) {
            return;
        }

        boolean isPinLock = currentActivityReference == null || currentActivityReference.get() == null || currentActivityReference.get() instanceof PinLockActivity;

        if (!isPinLock) {
            RuntimeUtil.runOnUiThread(() -> {
                logger.info("handLockedState - locked = {}", locked);

                if (locked) {
                    if (currentActivityReference.get() != null) {
                        if (preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_SYSTEM) ||
                                preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_BIOMETRIC)) {
                            BiometricUtil.showUnlockDialog(currentActivityReference.get(), null, false, 0, null);
                        } else {
                            try {
                                Intent intent = PinLockActivity.createIntent(context);
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
                this.handleLockedState(true);
            }
        }
    }

    public void pause(Activity pausedActivity) {
        if (this.currentActivityReference.get() == pausedActivity) {
            this.currentActivityReference.clear();
        }
    }

    public void destroy(Activity destroyedActivity) {
        if (this.currentActivityReference.get() == destroyedActivity) {
            this.currentActivityReference.clear();
        }
    }

    public void userInteract(Activity interactedActivity) {
        this.currentActivityReference.clear();
        this.currentActivityReference = new WeakReference<>(interactedActivity);
        this.timeLocking();
    }

    private boolean timeLocking() {
        if (this.lockAppService.isLockingEnabled()) {
            if (!this.lockAppService.isLocked()) {
                this.lockAppService.resetLockTimer(true);
            } else {
                //hand locked state to resuming activity
                return true;
            }
        }
        return false;
    }

    public static boolean activityResumed(Activity currentActivity) {
        logger.debug("*** App ActivityResumed");
        ActivityService activityService = KoinJavaComponent.getOrNull(ActivityService.class);
        if (activityService != null) {
            activityService.resume(currentActivity);
            return true;
        }
        return false;
    }

    public static void activityPaused(Activity pausedActivity) {
        logger.debug("*** App ActivityPaused");
        ActivityService activityService = KoinJavaComponent.getOrNull(ActivityService.class);
        if (activityService != null) {
            activityService.pause(pausedActivity);
        }
    }

    public static void activityDestroyed(Activity destroyedActivity) {
        logger.debug("*** App ActivityDestroyed");
        ActivityService activityService = KoinJavaComponent.getOrNull(ActivityService.class);
        if (activityService != null) {
            activityService.destroy(destroyedActivity);
        }
    }

    public static void activityUserInteract(Activity interactedActivity) {
        ActivityService activityService = KoinJavaComponent.getOrNull(ActivityService.class);
        if (activityService != null) {
            activityService.userInteract(interactedActivity);
        }
    }
}

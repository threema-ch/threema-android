package ch.threema.app.services;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.pinlock.PinLockActivity;
import ch.threema.app.applock.AppLockActivity;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.RuntimeUtil;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.localcrypto.MasterKeyProvider;

public class ActivityService {
    private static final Logger logger = getThreemaLogger("ActivityService");

    private final Context context;
    private final LockAppService lockAppService;
    private final PreferenceService preferenceService;
    private final @NonNull MasterKeyProvider masterKeyProvider;
    private @NonNull WeakReference<Activity> currentActivityReference = new WeakReference<>(null);

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

        this.lockAppService.addOnLockAppStateListener(isLocked -> {
            handleLockedState(isLocked);
            return false;
        });
    }

    private synchronized void handleLockedState(final boolean locked) {
        logger.debug("handleLockedState currentActivity: {}", currentActivityReference.get());

        if (masterKeyProvider.isLocked()) {
            return;
        }

        boolean isPinLock = currentActivityReference == null ||
            currentActivityReference.get() == null ||
            currentActivityReference.get() instanceof PinLockActivity;
        if (isPinLock) {
            return;
        }

        logger.info("handleLockedState - locked = {}", locked);
        if (!locked) {
            return;
        }

        var lockMechanism = preferenceService.getLockMechanism();
        RuntimeUtil.runOnUiThread(() -> {
            var activity = currentActivityReference.get();
            if (activity != null) {
                Intent intent;
                if (lockMechanism.equals(PreferenceService.LOCKING_MECH_SYSTEM) ||
                    lockMechanism.equals(PreferenceService.LOCKING_MECH_BIOMETRIC)) {
                    intent = AppLockActivity.createIntent(activity);

                } else {
                    intent = PinLockActivity.createIntent(context);
                }
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
        });
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

    public void userInteract(@Nullable Activity interactedActivity) {
        if (currentActivityReference.get() != interactedActivity) {
            this.currentActivityReference.clear();
            this.currentActivityReference = new WeakReference<>(interactedActivity);
        }
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

    public static void activityUserInteract(@Nullable Activity interactedActivity) {
        ActivityService activityService = KoinJavaComponent.getOrNull(ActivityService.class);
        if (activityService != null) {
            activityService.userInteract(interactedActivity);
        }
    }
}

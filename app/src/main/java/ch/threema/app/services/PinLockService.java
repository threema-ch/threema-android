package ch.threema.app.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.receivers.AlarmManagerBroadcastReceiver;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.widget.WidgetUpdater;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE;

public class PinLockService implements LockAppService {
    private static final Logger logger = getThreemaLogger("PinLockService");

    private final Context context;
    private final PreferenceService preferencesService;
    private NotificationService notificationService;
    private final UserService userService;
    private boolean locked;
    private final AlarmManager alarmManager;
    private PendingIntent lockTimerIntent;
    private long lockTimeStamp = 0;
    private final CopyOnWriteArrayList<OnLockAppStateListener> onLockAppStateListeners = new CopyOnWriteArrayList<>();

    public PinLockService(@NonNull Context context, @NonNull PreferenceService preferencesService, @NonNull UserService userService) {
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

        boolean isLockMechanismPin = PreferenceService.LOCKING_MECH_PIN.equals(preferencesService.getLockMechanism());

        if ((isLockMechanismPin && pin != null && preferencesService.isPinCodeCorrect(pin)) ||
            PreferenceService.LOCKING_MECH_SYSTEM.equals(preferencesService.getLockMechanism()) ||
            PreferenceService.LOCKING_MECH_BIOMETRIC.equals(preferencesService.getLockMechanism())) {
            this.resetLockTimer(false);
            this.updateState(false);
            this.lockTimeStamp = 0;
            return !this.locked;
        } else if (isLockMechanismPin) {
            logger.info("Incorrect PIN entered");
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
            } catch (Exception e) {
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

    private void updateState(boolean isLocked) {
        if (this.locked == isLocked) {
            return;
        }
        logger.info("updating locked stated from {} to {} ", this.locked, isLocked);
        this.locked = isLocked;

        synchronized (this.onLockAppStateListeners) {
            ArrayList<OnLockAppStateListener> toRemove = new ArrayList<>();
            for (OnLockAppStateListener onLockAppStateListener : this.onLockAppStateListeners) {
                if (onLockAppStateListener.changed(isLocked)) {
                    toRemove.add(onLockAppStateListener);
                }
            }
            this.onLockAppStateListeners.removeAll(toRemove);
        }

        WidgetUpdater.update();
    }

    @Override
    public boolean isLocked() {
        return this.locked;
    }

    @Override
    public void resetLockTimer(boolean restartAfterReset) {
        if (this.lockTimerIntent != null) {
            this.lockTimeStamp = 0;
            alarmManager.cancel(this.lockTimerIntent);
        }

        if (restartAfterReset) {
            int time = this.preferencesService.getPinLockGraceTime();
            if (time > 0) {
                Intent lockingIntent = new Intent(context, AlarmManagerBroadcastReceiver.class);
                lockingIntent.putExtra(LifetimeServiceImpl.REQUEST_CODE_KEY, LifetimeServiceImpl.REQUEST_LOCK_APP);
                this.lockTimerIntent = PendingIntent.getBroadcast(context, LifetimeServiceImpl.REQUEST_LOCK_APP, lockingIntent, PENDING_INTENT_FLAG_MUTABLE);
                this.lockTimeStamp = System.currentTimeMillis() + time * DateUtils.SECOND_IN_MILLIS;
                alarmManager.set(AlarmManager.RTC_WAKEUP, this.lockTimeStamp, this.lockTimerIntent);
            } else {
                this.lockTimeStamp = 0;
            }
        }
    }

    @Override
    public void addOnLockAppStateListener(OnLockAppStateListener onLockAppStateListener) {
        synchronized (this.onLockAppStateListeners) {
            this.onLockAppStateListeners.add(onLockAppStateListener);
        }
    }
}

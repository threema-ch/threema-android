package ch.threema.app.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import ch.threema.app.R;
import ch.threema.app.activities.DummyActivity;
import ch.threema.app.home.HomeActivity;
import ch.threema.app.passphrase.PassphraseLockActivity;
import ch.threema.app.notifications.NotificationChannels;
import ch.threema.app.notifications.NotificationIDs;
import ch.threema.app.services.notification.NotificationService;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING;
import static ch.threema.app.di.DIJavaCompat.getMasterKeyManager;

public class PassphraseService extends Service {
    private static final Logger logger = getThreemaLogger("PassphraseService");
    private static Intent service;
    private static final int FG_SERVICE_TYPE =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ? FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            : 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        logger.debug("onCreate");

        try {
            showPersistentNotification();
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.debug("onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        logger.debug("onDestroy");
        removePersistentNotification(this);
        stopForeground(true);
        service = null;
    }

    /**
     * Workaround for Android bug:
     * https://code.google.com/p/android/issues/detail?id=53313
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        logger.info("*** PassphraseService task removed");
        Intent intent = new Intent(this, DummyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showPersistentNotification() {
        logger.debug("showPersistentNotification");

        // The Intent to launch our activity if the user selects this notification
        Intent notificationIntent = HomeActivity.createIntent(this);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
            Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.setAction(Long.toString(System.currentTimeMillis()));

        Intent stopIntent = PassphraseLockActivity.createIntent(this);
        stopIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        PendingIntent stopPendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack
        stackBuilder.addParentStack(HomeActivity.class);
        // Adds the Intent to the top of the stack
        stackBuilder.addNextIntent(notificationIntent);
        // Gets a PendingIntent containing the entire back stack
        PendingIntent pendingIntent = stackBuilder.getPendingIntent((int) System.currentTimeMillis(), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationChannels.NOTIFICATION_CHANNEL_PASSPHRASE)
            .setSmallIcon(R.drawable.ic_noti_passguard)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.masterkey_is_unlocked))
            .setPriority(Notification.PRIORITY_MIN)
            .addAction(R.drawable.ic_lock_grey600_24dp, getString(R.string.title_lock), stopPendingIntent);

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent);
        }

        ServiceCompat.startForeground(
            this,
            NotificationIDs.PASSPHRASE_SERVICE_NOTIFICATION_ID,
            builder.build(),
            FG_SERVICE_TYPE);
    }

    private static void removePersistentNotification(Context context) {
        logger.debug("removePersistentNotification");

        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.cancel(NotificationIDs.PASSPHRASE_SERVICE_NOTIFICATION_ID);

        NotificationService notificationService = KoinJavaComponent.getOrNull(NotificationService.class);
        if (notificationService != null) {
            notificationService.cancelConversationNotificationsOnLockApp();
        }
    }

    public static boolean isRunning() {
        logger.debug("isRunning");
        return (service != null);
    }

    /**
     * Start the passphrase service if the masterkey is protected and not locked!
     */
    public static void start(final Context context) {
        logger.debug("start");
        var masterKeyManager = getMasterKeyManager();

        // start service, if not yet started
        if (service == null) {
            if (masterKeyManager.isLockedWithPassphrase() || !masterKeyManager.isProtectedWithPassphrase()) {
                return;
            }
            service = new Intent(context, PassphraseService.class);
            ContextCompat.startForegroundService(context, service);
        } else {
            if (!masterKeyManager.isProtectedWithPassphrase()) {
                stop(context);
            }
        }
    }

    public static void stop(final Context context) {
        logger.debug("stop");
        if (service != null) {
            removePersistentNotification(context);
            context.stopService(service);
            service = null;
        }
    }
}

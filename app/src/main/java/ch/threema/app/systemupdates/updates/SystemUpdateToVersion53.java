package ch.threema.app.systemupdates.updates;

import android.content.Context;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import ch.threema.app.BuildConfig;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * remove old pre-API26 notification channels
 */
public class SystemUpdateToVersion53 implements SystemUpdate {
    private static final Logger logger = getThreemaLogger("SystemUpdateToVersion53");

    @NonNull
    private final Context context;

    public SystemUpdateToVersion53(@NonNull Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        try {
            notificationManagerCompat.deleteNotificationChannel("passphrase_service");
            notificationManagerCompat.deleteNotificationChannel("webclient");
            notificationManagerCompat.deleteNotificationChannel(BuildConfig.APPLICATION_ID + "passphrase_service");
            notificationManagerCompat.deleteNotificationChannel(BuildConfig.APPLICATION_ID + "webclient");
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @Override
    public int getVersion() {
        return 53;
    }
}

package ch.threema.app.systemupdates.updates;

import android.content.Context;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.core.app.NotificationManagerCompat;
import ch.threema.app.BuildConfig;
import kotlin.Lazy;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * remove old pre-API26 notification channels
 */
public class SystemUpdateToVersion53 implements SystemUpdate {
    private static final Logger logger = getThreemaLogger("SystemUpdateToVersion53");

    private final Lazy<Context> appContextLazy = KoinJavaComponent.inject(Context.class);

    @Override
    public void run() {
        var appContext = appContextLazy.getValue();
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(appContext);
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

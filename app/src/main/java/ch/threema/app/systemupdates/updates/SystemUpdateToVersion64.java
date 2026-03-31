package ch.threema.app.systemupdates.updates;

import android.content.Context;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;

import androidx.core.app.NotificationManagerCompat;
import androidx.work.WorkManager;
import kotlin.Lazy;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/* clean up image labeler */
public class SystemUpdateToVersion64 implements SystemUpdate {
    private static final Logger logger = getThreemaLogger("SystemUpdateToVersion64");

    private final Lazy<Context> appContextLazy = KoinJavaComponent.inject(Context.class);

    @Override
    public void run() {
        deleteMediaLabelsDatabase();
    }

    private void deleteMediaLabelsDatabase() {
        var appContext = appContextLazy.getValue();

        WorkManager.getInstance(appContext).cancelAllWorkByTag("ImageLabelsPeriodic");
        WorkManager.getInstance(appContext).cancelAllWorkByTag("ImageLabelsOneTime");

        try {
            final String[] files = new String[]{
                "media_items.db",
                "media_items.db-shm",
                "media_items.db-wal",
            };
            for (String filename : files) {
                final File databasePath = appContext.getDatabasePath(filename);
                if (databasePath.exists() && databasePath.isFile()) {
                    logger.info("Removing file {}", filename);
                    if (!databasePath.delete()) {
                        logger.warn("Could not remove file {}", filename);
                    }
                } else {
                    logger.debug("File {} not found", filename);
                }
            }
        } catch (Exception e) {
            logger.error("Exception while deleting media labels database");
        }

        // remove notification channel
        String NOTIFICATION_CHANNEL_IMAGE_LABELING = "il";
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(appContext);
        notificationManagerCompat.deleteNotificationChannel(NOTIFICATION_CHANNEL_IMAGE_LABELING);
    }

    @Override
    public String getDescription() {
        return "delete media labels database";
    }

    @Override
    public int getVersion() {
        return 64;
    }
}

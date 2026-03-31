package ch.threema.app.systemupdates.updates;

import android.os.Environment;
import android.util.Log;

import java.io.File;

import ch.threema.app.BuildConfig;

/**
 * Remove old message log
 */
public class SystemUpdateToVersion55 implements SystemUpdate {
    private final static String TAG = "SystemUpdateToVersion55";

    @Override
    public void run() {
        try {
            final File threemaDir = new File(Environment.getExternalStorageDirectory(), BuildConfig.MEDIA_PATH);
            if (threemaDir.exists()) {
                final File messageLog = new File(threemaDir, "message_log.txt");
                final File debugLog = new File(threemaDir, "debug_log.txt");

                final boolean hasMessageLog = messageLog.exists() && messageLog.isFile();
                final boolean hasDebugLog = debugLog.exists() && debugLog.isFile();

                if (hasMessageLog && !hasDebugLog) {
                    // Rename
                    boolean success = messageLog.renameTo(debugLog);
                    if (!success) {
                        Log.w(TAG, "Renaming message log failed");
                    }
                } else if (hasMessageLog) {
                    // Delete
                    boolean success = messageLog.delete();
                    if (!success) {
                        Log.w(TAG, "Removing message log failed");
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Exception: " + e.getMessage());
        }
    }

    @Override
    public int getVersion() {
        return 55;
    }
}

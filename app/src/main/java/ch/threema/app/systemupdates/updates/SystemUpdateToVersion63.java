package ch.threema.app.systemupdates.updates;

import android.content.Context;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;

import androidx.annotation.NonNull;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class SystemUpdateToVersion63 implements SystemUpdate {
    private static final Logger logger = getThreemaLogger("SystemUpdateToVersion63");
    private @NonNull final Context context;

    public SystemUpdateToVersion63(@NonNull Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        deleteDir(new File(context.getFilesDir(), "tmp"));
        deleteDir(new File(context.getExternalFilesDir(null), "data.blob"));
        deleteDir(new File(context.getExternalFilesDir(null), "tmp"));
    }

    private void deleteDir(File tmpPath) {
        if (tmpPath.exists()) {
            try {
                FileUtils.deleteDirectory(tmpPath);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    public String getDescription() {
        return "delete obsolete temp directories";
    }

    @Override
    public int getVersion() {
        return 63;
    }
}

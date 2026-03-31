package ch.threema.app.systemupdates.updates;

import android.content.Context;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;

import kotlin.Lazy;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.common.JavaCompat.deleteRecursively;

public class SystemUpdateToVersion63 implements SystemUpdate {
    private static final Logger logger = getThreemaLogger("SystemUpdateToVersion63");

    private final Lazy<Context> appContextLazy = KoinJavaComponent.inject(Context.class);

    @Override
    public void run() {
        var appContext = appContextLazy.getValue();
        deleteDir(new File(appContext.getFilesDir(), "tmp"));
        deleteDir(new File(appContext.getExternalFilesDir(null), "data.blob"));
        deleteDir(new File(appContext.getExternalFilesDir(null), "tmp"));
    }

    private void deleteDir(File tmpPath) {
        if (tmpPath.exists()) {
            try {
                deleteRecursively(tmpPath);
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

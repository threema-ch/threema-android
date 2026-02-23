package ch.threema.app.utils;

import org.slf4j.Logger;

import androidx.fragment.app.FragmentManager;
import ch.threema.app.dialogs.GenericProgressDialog;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class LoadingUtil {
    private static final Logger logger = getThreemaLogger("LoadingUtil");

    private static String DIALOG_TAG_PROGRESS_LOADINGUTIL = "lou";

    /**
     * Run a {screen} in a thread and show a loading alert with {subjectId} and {textId}
     *
     * @param fragmentManager
     * @param subjectId
     * @param textId
     * @param script
     * @return
     */
    public static Thread runInAlert(FragmentManager fragmentManager, int subjectId, int textId, final Runnable script) {
        GenericProgressDialog.newInstance(subjectId, textId).show(fragmentManager, DIALOG_TAG_PROGRESS_LOADINGUTIL);

        Thread t = new Thread(() -> {
            try {
                script.run();
            } catch (Exception x) {
                logger.error("Exception", x);
            } finally {
                RuntimeUtil.runOnUiThread(() -> DialogUtil.dismissDialog(fragmentManager, DIALOG_TAG_PROGRESS_LOADINGUTIL, true));
            }

        });

        t.start();
        return t;
    }
}

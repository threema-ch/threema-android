package ch.threema.app.ui;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import ch.threema.app.utils.RuntimeUtil;

public class ResumePauseHandler {
    private static final Map<String, ResumePauseHandler> instances = new HashMap<>();
    private static final Object lock = new Object();

    private final Map<String, RunIfActive> runIfActiveList = new HashMap<>();
    private final WeakReference<Activity> activityReference;
    private boolean isActive;
    private boolean hasHandlers = false;

    private ResumePauseHandler(@NonNull Activity activity) {
        this.activityReference = new WeakReference<>(activity);
    }

    @NonNull
    public static ResumePauseHandler getByActivity(@NonNull Object useInObject, @NonNull Activity activity) {
        final String key = useInObject.getClass().toString();
        synchronized (lock) {
            ResumePauseHandler instance = instances.get(key);
            if (instance == null || instance.getActivity() == null) {
                instance = new ResumePauseHandler(activity);
                instances.put(key, instance);
            }
            return instance;
        }
    }

    public interface RunIfActive {
        void runOnUiThread();
    }

    public void runOnActive(String tag, RunIfActive runIfActive) {
        this.runOnActive(tag, runIfActive, false);
    }

    public void runOnActive(String tag, RunIfActive runIfActive, boolean lowPriority) {
        if (runIfActive == null) {
            return;
        }

        if (this.isActive) {
            this.run(runIfActive);
        } else {
            //pending
            synchronized (this.runIfActiveList) {
                if (!lowPriority || !this.runIfActiveList.containsKey(tag)) {
                    this.runIfActiveList.put(tag, runIfActive);
                    this.hasHandlers = true;
                }
            }
        }
    }

    public void onResume() {
        if (!this.isActive) {
            this.isActive = true;
            if (this.hasHandlers) {
                synchronized (this.runIfActiveList) {
                    for (RunIfActive r : this.runIfActiveList.values()) {
                        if (r != null && this.isActive) {
                            this.run(r);
                        }
                    }

                    this.runIfActiveList.clear();
                    this.hasHandlers = false;
                }
            }
        }
    }

    public void onPause() {
        this.isActive = false;
    }

    public void onDestroy(Object object) {
        synchronized (this.runIfActiveList) {
            this.isActive = false;
            this.runIfActiveList.clear();
            instances.remove(object.getClass().toString());
        }
    }

    private void run(final RunIfActive runIfActive) {
        if (runIfActive != null && activityReference.get() != null) {
            RuntimeUtil.runOnUiThread(runIfActive::runOnUiThread);
        }
    }

    private Activity getActivity() {
        return this.activityReference.get();
    }
}

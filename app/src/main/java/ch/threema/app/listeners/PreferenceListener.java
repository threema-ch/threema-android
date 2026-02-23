package ch.threema.app.listeners;

import androidx.annotation.AnyThread;

public interface PreferenceListener {
    @AnyThread
    void onChanged(String key, Object value);
}

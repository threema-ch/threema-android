package ch.threema.app.adapters;

import androidx.annotation.MainThread;

public interface FilterResultsListener {
    @MainThread
    void onResultsAvailable(int count);
}

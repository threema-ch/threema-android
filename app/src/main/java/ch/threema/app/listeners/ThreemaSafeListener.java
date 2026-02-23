package ch.threema.app.listeners;

import androidx.annotation.AnyThread;

public interface ThreemaSafeListener {
    @AnyThread
    void onBackupStatusChanged();
}


package ch.threema.app.listeners;

import androidx.annotation.AnyThread;

public interface ContactCountListener {
    @AnyThread
    void onNewContactsCountUpdated(int last24hoursCount);
}

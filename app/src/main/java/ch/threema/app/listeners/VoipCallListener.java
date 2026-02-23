package ch.threema.app.listeners;

import androidx.annotation.AnyThread;

public interface VoipCallListener {
    @AnyThread
    void onStart(String contact, long elpasedTimeMs);

    @AnyThread
    void onEnd();
}

package ch.threema.app.webclient.listeners;

import androidx.annotation.AnyThread;

@AnyThread
public interface WebClientWakeUpListener {
    void onProtocolError();
}

package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.storage.models.ServerMessageModel;

public interface ServerMessageListener {
    @AnyThread
    void onAlert(ServerMessageModel serverMessage);

    @AnyThread
    void onError(ServerMessageModel serverMessage);
}

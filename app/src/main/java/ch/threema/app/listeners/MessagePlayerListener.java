package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import androidx.media3.session.MediaController;

import com.google.common.util.concurrent.ListenableFuture;

import ch.threema.storage.models.AbstractMessageModel;

public interface MessagePlayerListener {
    @AnyThread
    default void onAudioPlayEnded(AbstractMessageModel messageModel, ListenableFuture<MediaController> mediaControllerFuture) {
    }
}

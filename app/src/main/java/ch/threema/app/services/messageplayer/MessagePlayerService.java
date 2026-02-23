package ch.threema.app.services.messageplayer;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.session.MediaController;

import com.google.common.util.concurrent.ListenableFuture;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;

public interface MessagePlayerService {
    MessagePlayer createPlayer(AbstractMessageModel m, Activity activity, MessageReceiver<?> messageReceiver, @Nullable ListenableFuture<MediaController> mediaControllerFuture);

    void release();

    void stopAll();

    void pauseAll(int source);

    void resumeAll(Activity activity, MessageReceiver<?> messageReceiver, int source);

    void setTranscodeProgress(@NonNull AbstractMessageModel messageModel, int progress);

    void setTranscodeStart(@NonNull AbstractMessageModel messageModel);

    void setTranscodeFinished(@NonNull AbstractMessageModel messageModel, boolean success, @Nullable String message);
}

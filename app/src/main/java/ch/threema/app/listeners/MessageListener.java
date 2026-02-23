package ch.threema.app.listeners;

import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.storage.models.AbstractMessageModel;

/**
 * Listen for new, changed or removed messages.
 */
public interface MessageListener {
    @AnyThread
    default void onNew(AbstractMessageModel newMessage) {}

    @AnyThread
    default void onModified(List<AbstractMessageModel> modifiedMessageModel) {}

    @AnyThread
    default void onRemoved(AbstractMessageModel removedMessageModel) {}

    @AnyThread
    default void onRemoved(List<AbstractMessageModel> removedMessageModels) {}

    @AnyThread
    default void onProgressChanged(AbstractMessageModel messageModel, int newProgress) {}

    @AnyThread
    default void onResendDismissed(@NonNull AbstractMessageModel messageModel) {}
}

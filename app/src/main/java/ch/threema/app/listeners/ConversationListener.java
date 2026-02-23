package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.storage.models.ConversationModel;

public interface ConversationListener {
    @AnyThread
    void onNew(@NonNull ConversationModel conversationModel);

    @AnyThread
    void onModified(@NonNull ConversationModel modifiedConversationModel, @Nullable Integer oldPosition);

    @AnyThread
    void onRemoved(@NonNull ConversationModel conversationModel);

    @AnyThread
    void onModifiedAll();
}

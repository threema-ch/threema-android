package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.storage.models.ContactModel;

public interface ContactTypingListener {
    @AnyThread
    void onContactIsTyping(@NonNull ContactModel contactModel, boolean isTyping);
}

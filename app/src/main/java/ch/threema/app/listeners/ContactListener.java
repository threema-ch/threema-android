package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.storage.models.ContactModel;

public interface ContactListener {
    /**
     * A new contact is added.
     */
    @AnyThread
    default void onNew(final @NonNull String identity) {
    }

    /**
     * Called when the contact with the specified identity is modified.
     */
    @AnyThread
    default void onModified(final @NonNull String identity) {
    }

    /**
     * Called when the contact avatar was changed.
     */
    @AnyThread
    default void onAvatarChanged(final @NonNull String identity) {
    }

    /**
     * The contact was removed.
     */
    @AnyThread
    default void onRemoved(final @NonNull String identity) {
    }
}

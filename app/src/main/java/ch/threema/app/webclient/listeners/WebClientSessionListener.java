package ch.threema.app.webclient.listeners;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import ch.threema.storage.models.WebClientSessionModel;

/**
 * This listener is mainly used by the session activity.
 */
@AnyThread
public interface WebClientSessionListener {
    void onModified(@NonNull WebClientSessionModel model);

    void onRemoved(@NonNull WebClientSessionModel model);

    void onCreated(@NonNull WebClientSessionModel model);
}

package ch.threema.app.listeners;

import java.util.List;

import androidx.annotation.AnyThread;
import ch.threema.data.models.ContactModel;

/**
 * Listen for new contacts added via sync.
 */
public interface NewSyncedContactsListener {
    @AnyThread
    void onNew(List<ContactModel> contactModels);
}

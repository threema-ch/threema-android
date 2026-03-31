package ch.threema.app.backuprestore;

import java.io.File;

import ch.threema.base.SessionScoped;
import androidx.annotation.NonNull;
import ch.threema.storage.models.ConversationModel;

@SessionScoped
public interface BackupChatService {

    boolean backupChatToZip(
        @NonNull ConversationModel conversationModel,
        @NonNull File outputFile,
        @NonNull String password,
        boolean includeMedia
    );

    void cancel();
}

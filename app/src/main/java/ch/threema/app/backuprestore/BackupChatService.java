package ch.threema.app.backuprestore;

import java.io.File;

import ch.threema.base.SessionScoped;
import ch.threema.storage.models.ConversationModel;

@SessionScoped
public interface BackupChatService {
    boolean backupChatToZip(
        ConversationModel conversationModel,
        File outputFile,
        String password,
        boolean includeMedia
    );

    void cancel();
}

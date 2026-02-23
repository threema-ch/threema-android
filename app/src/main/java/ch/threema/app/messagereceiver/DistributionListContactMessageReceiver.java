package ch.threema.app.messagereceiver;

import java.util.Collection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.domain.models.MessageId;
import ch.threema.storage.models.MessageModel;

public class DistributionListContactMessageReceiver extends ContactMessageReceiver {
    private SymmetricEncryptionResult fileEncryptionResult;
    private byte[] thumbnailBlobId;
    private byte[] fileBlobId;

    public DistributionListContactMessageReceiver(ContactMessageReceiver contactMessageReceiver) {
        super(contactMessageReceiver);
    }

    @Override
    public boolean sendMediaData() {
        return false;
    }

    @Override
    public void createAndSendFileMessage(
        @Nullable byte[] thumbnailBlobIdIgnored,
        @Nullable byte[] fileBlobIdIgnored,
        @Nullable SymmetricEncryptionResult encryptionResultIgnored,
        @NonNull MessageModel messageModel,
        @Nullable MessageId messageId,
        @Nullable Collection<String> recipientIdentities
    ) throws ThreemaException {
        if (fileBlobId == null || fileEncryptionResult == null) {
            throw new ThreemaException("Required values have not been set by responsible DistributionListMessageReceiver");
        }
        super.createAndSendFileMessage(thumbnailBlobId, fileBlobId, fileEncryptionResult, messageModel, messageId, recipientIdentities);
    }

    public void setFileMessageParameters(
        @NonNull byte[] thumbnailBlobId,
        @NonNull byte[] fileBlobId,
        @NonNull SymmetricEncryptionResult fileEncryptionResult
    ) {
        this.fileBlobId = fileBlobId;
        this.thumbnailBlobId = thumbnailBlobId;
        this.fileEncryptionResult = fileEncryptionResult;
    }

    @Override
    public boolean equals(Object o) {
        // There are no distinguishing properties added in this subclass
        // equality is based on the contact model which is handled by the super-implementation
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        // There are no distinguishing properties added in this subclass
        // hashCode is based on the contact model which is handled by the super-implementation
        return super.hashCode();
    }
}

package ch.threema.storage.models.data.media;

import ch.threema.storage.models.data.MessageDataInterface;

public interface MediaMessageDataInterface extends MessageDataInterface {
    byte[] getEncryptionKey();

    byte[] getBlobId();

    boolean isDownloaded();

    void isDownloaded(boolean isDownloaded);

    byte[] getNonce();
}

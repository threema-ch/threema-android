package ch.threema.domain.protocol.csp.messages;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import androidx.annotation.Nullable;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.common.OutputStreamExtensionsKt.writeLittleEndianInt;

import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A group message that has an image (stored on the blob server) as its content.
 * <p>
 * The contents are referenced by the {@code blobId}, the file {@code size} in bytes,
 * and the symmetric encryption key to be used when decrypting the image blob.
 *
 * @Deprecated Use GroupFileMessage instead
 */
@Deprecated
public class GroupImageMessage extends AbstractGroupMessage {

    private static final Logger logger = getThreemaLogger("GroupImageMessage");

    private byte[] blobId;
    private int size;
    private byte[] encryptionKey;

    public GroupImageMessage() {
        super();
    }

    @Override
    public int getType() {
        return ProtocolDefines.MSGTYPE_GROUP_IMAGE;
    }

    @Override
    public boolean flagSendPush() {
        return true;
    }

    @Override
    @Nullable
    public Version getMinimumRequiredForwardSecurityVersion() {
        return Version.V1_2;
    }

    @Override
    public boolean allowUserProfileDistribution() {
        return true;
    }

    @Override
    public boolean exemptFromBlocking() {
        return false;
    }

    @Override
    public boolean createImplicitlyDirectContact() {
        return false;
    }

    @Override
    public boolean protectAgainstReplay() {
        return true;
    }

    @Override
    public boolean reflectIncoming() {
        return true;
    }

    @Override
    public boolean reflectOutgoing() {
        return true;
    }

    @Override
    public boolean reflectSentUpdate() {
        return true;
    }

    @Override
    public boolean sendAutomaticDeliveryReceipt() {
        return false;
    }

    @Override
    public boolean bumpLastUpdate() {
        return true;
    }

    @Override
    public byte[] getBody() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(getGroupCreator().getBytes(StandardCharsets.US_ASCII));
            bos.write(getApiGroupId().getGroupId());
            bos.write(blobId);
            writeLittleEndianInt(bos, size);
            bos.write(encryptionKey);
            return bos.toByteArray();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    public byte[] getBlobId() {
        return blobId;
    }

    public void setBlobId(byte[] blobId) {
        this.blobId = blobId;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public byte[] getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(byte[] encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}

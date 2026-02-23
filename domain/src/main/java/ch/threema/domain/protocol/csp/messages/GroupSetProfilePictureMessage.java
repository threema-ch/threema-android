package ch.threema.domain.protocol.csp.messages;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;

import androidx.annotation.Nullable;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.common.OutputStreamExtensionsKt.writeLittleEndianInt;

import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A group message sent by the group creator that causes a new photo to be set for the group.
 */
public class GroupSetProfilePictureMessage extends AbstractGroupMessage {

    private static final Logger logger = getThreemaLogger("GroupSetPhotoMessage");

    private byte[] blobId;
    private int size;
    private byte[] encryptionKey;

    public GroupSetProfilePictureMessage() {
        super();
    }

    @Override
    public int getType() {
        return ProtocolDefines.MSGTYPE_GROUP_SET_PHOTO;
    }

    @Override
    @Nullable
    public Version getMinimumRequiredForwardSecurityVersion() {
        return Version.V1_2;
    }

    @Override
    public boolean allowUserProfileDistribution() {
        return false;
    }

    @Override
    public boolean exemptFromBlocking() {
        return true;
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
        return false;
    }

    @Override
    public boolean sendAutomaticDeliveryReceipt() {
        return false;
    }

    @Override
    public boolean bumpLastUpdate() {
        return false;
    }

    @Override
    public byte[] getBody() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
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

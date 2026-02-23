package ch.threema.domain.protocol.csp.messages;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;

import androidx.annotation.Nullable;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * This message is sent by a group creator to the group members to request the group picture to be removed.
 */
public class GroupDeleteProfilePictureMessage extends AbstractGroupMessage {

    private static final Logger logger = getThreemaLogger("GroupDeletePhotoMessage");

    public GroupDeleteProfilePictureMessage() {
        super();
    }

    @Override
    public int getType() {
        return ProtocolDefines.MSGTYPE_GROUP_DELETE_PHOTO;
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
    @Nullable
    public byte[] getBody() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(getApiGroupId().getGroupId());
            return bos.toByteArray();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }
}

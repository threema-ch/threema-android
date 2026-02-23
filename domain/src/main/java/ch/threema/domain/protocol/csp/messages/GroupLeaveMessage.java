package ch.threema.domain.protocol.csp.messages;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import androidx.annotation.Nullable;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A group message that indicates that the sender wants to be removed from the group.
 */
public class GroupLeaveMessage extends AbstractGroupMessage {

    private static final Logger logger = getThreemaLogger("GroupLeaveMessage");

    public GroupLeaveMessage() {
        super();
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
    public int getType() {
        return ProtocolDefines.MSGTYPE_GROUP_LEAVE;
    }

    @Override
    public byte[] getBody() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(getGroupCreator().getBytes(StandardCharsets.US_ASCII));
            bos.write(getApiGroupId().getGroupId());
            return bos.toByteArray();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }
}

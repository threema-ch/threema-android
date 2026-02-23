package ch.threema.domain.protocol.csp.messages;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import androidx.annotation.Nullable;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A group creation message.
 */
public class GroupSetupMessage extends AbstractGroupMessage {
    private static final Logger logger = getThreemaLogger("GroupCreateMessage");

    private String[] members;

    public GroupSetupMessage() {
        super();
    }

    @Override
    public int getType() {
        return ProtocolDefines.MSGTYPE_GROUP_CREATE;
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
        return true;
    }

    @Override
    public boolean createImplicitlyDirectContact() {
        return true;
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
        // Note: lastUpdate should be triggered only for new groups. This logic is handled
        //       in the corresponding tasks, so we can set the flag below to false.
        return false;
    }

    @Override
    public byte[] getBody() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(getApiGroupId().getGroupId());
            for (String member : members)
                bos.write(member.getBytes(StandardCharsets.US_ASCII));
            return bos.toByteArray();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    public String[] getMembers() {
        return members;
    }

    public void setMembers(String[] members) {
        this.members = members;
    }
}

package ch.threema.domain.protocol.csp.messages;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.domain.models.GroupId;

public class GroupDeleteProfilePictureMessageTest {

    @Test
    public void testGetBody() {
        final GroupDeleteProfilePictureMessage msg = new GroupDeleteProfilePictureMessage();
        msg.setGroupCreator("GRCREATE");
        GroupId groupId = new GroupId();
        msg.setApiGroupId(groupId);

        Assertions.assertArrayEquals(groupId.getGroupId(), msg.getBody());
    }

}

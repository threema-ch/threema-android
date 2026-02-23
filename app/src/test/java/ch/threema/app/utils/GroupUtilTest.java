package ch.threema.app.utils;

import org.junit.Assert;
import org.junit.Test;

import ch.threema.storage.models.GroupModel;

public class GroupUtilTest {

    private String[][] getGroup_SendToCreator() {
        return new String[][]{
            {"The Group", "ECHOECHO"},
            {"☁The Group", "ECHOECHO"},
            {"☁", "ECHOECHO"},
            {"", "ECHOECHO"},
            {"☁The Group", "*THREEMA"},
            {"☁", "*THREEMA"},
        };
    }


    private String[][] getGroup_DontSendToCreator() {
        return new String[][]{
            {"The Group", "*THREEMA"},
            {"", "*THREEMA"},
            {"No ☁", "*THREEMA"}
        };
    }

    @Test
    public void shouldSendMessagesToCreator_WithModel() {
        for (String[] g : this.getGroup_SendToCreator()) {
            Assert.assertTrue(
                String.format("Send to creator should be true with name: \"%s\" and creator: \"%s\"", g[0], g[1]), GroupUtil.shouldSendMessagesToCreator((new GroupModel())
                    .setName(g[0])
                    .setCreatorIdentity(g[1])));
        }

        for (String[] g : this.getGroup_DontSendToCreator()) {
            Assert.assertFalse(
                String.format("Send to creator should be false with name: \"%s\" and creator: \"%s\"", g[0], g[1]),
                GroupUtil.shouldSendMessagesToCreator((new GroupModel())
                    .setName(g[0])
                    .setCreatorIdentity(g[1])));
        }
    }

    @Test
    public void shouldSendMessagesToCreator_WithString() {
        for (String[] g : this.getGroup_SendToCreator()) {
            Assert.assertTrue(
                String.format("Send to creator should be true with name: \"%s\" and creator: \"%s\"", g[0], g[1]),
                GroupUtil.shouldSendMessagesToCreator(g[1], g[0])
            );
        }

        for (String[] g : this.getGroup_DontSendToCreator()) {
            Assert.assertFalse(
                String.format("Send to creator should be false with name: \"%s\" and creator: \"%s\"", g[0], g[1]),
                GroupUtil.shouldSendMessagesToCreator(g[1], g[0])
            );
        }
    }

}

package ch.threema.app.utils;

import org.junit.Test;

import java.util.Date;

import ch.threema.storage.models.MessageModel;

import static junit.framework.Assert.assertTrue;

public class MessageDateUtilTest {
    @Test
    public void outgoing() {
        Date dateA = new Date(2014, 1, 1);
        Date dateB = new Date(2014, 1, 2);
        Date dateC = new Date(2014, 1, 3);

        MessageModel messageModel = new MessageModel();
        messageModel.setCreatedAt(dateA);
        messageModel.setPostedAt(dateB);
        messageModel.setModifiedAt(dateC);
        messageModel.setOutbox(true);

        assertTrue(messageModel.isOutbox());

//        Date displayDate = MessageUtil.getDisplayDate()
    }
}

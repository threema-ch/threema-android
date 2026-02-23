package ch.threema.storage.models;

import java.util.Date;

/**
 * Only for the the divider
 */
public class FirstUnreadMessageModel extends AbstractMessageModel {
    @Override
    public int getId() {
        return 0;
    }

    @Override
    public String getUid() {
        return null;
    }

    @Override
    public boolean isStatusMessage() {
        return true;
    }

    @Override
    public String getIdentity() {
        return null;
    }

    @Override
    public boolean isOutbox() {
        return false;
    }

    @Override
    public MessageType getType() {
        return MessageType.STATUS;
    }

    @Override
    public String getBody() {
        return null;
    }

    @Override
    public boolean isRead() {
        return false;
    }

    @Override
    public boolean isSaved() {
        return false;
    }

    @Override
    public MessageState getState() {
        return null;
    }

    @Override
    public Date getModifiedAt() {
        return null;
    }

    @Override
    public Date getPostedAt() {
        return null;
    }

    @Override
    public String getApiMessageId() {
        return null;
    }
}

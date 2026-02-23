package ch.threema.storage.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

public class GroupMessageModel extends AbstractMessageModel {
    public static final String TABLE = "m_group_message";
    public static final String COLUMN_GROUP_ID = "groupId";
    public static final String COLUMN_GROUP_MESSAGE_STATES = "groupMessageStates";

    private int groupId;

    // TODO(ANDR-3325): This is only used for group ack/dec and can therefore be removed
    //  when the database is migrated.
    private Map<String, Object> groupMessageStates;

    public GroupMessageModel() {
        super();
    }

    public GroupMessageModel(boolean isStatusMessage) {
        super(isStatusMessage);
    }

    /**
     * Returns the ID of the group model this message belongs to. This is different from the GroupID object!
     *
     * @return ID of group model
     */
    public int getGroupId() {
        return this.groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    @Nullable
    public Map<String, Object> getGroupMessageStates() {
        return this.groupMessageStates;
    }

    public GroupMessageModel setGroupMessageStates(@Nullable Map<String, Object> groupMessageStates) {
        this.groupMessageStates = groupMessageStates;
        return this;
    }

    @Override
    public String toString() {
        return "group_message.id = " + this.getId();
    }

    /**
     * Copy relevant data (i.e. data that may change later on) from specified source model to this model.
     * This is used to update the GroupMessageCache. Don't forget to add new fields here!
     *
     * @param sourceModel GroupMessageModel from which the data should be copied over
     */
    public void copyFrom(@NonNull GroupMessageModel sourceModel) {
        setType(sourceModel.getType());
        setDataObject(sourceModel.getDataObject());
        setGroupMessageStates(sourceModel.getGroupMessageStates());
        setCorrelationId(sourceModel.getCorrelationId());
        setSaved(sourceModel.isSaved());
        setState(sourceModel.getState());
        setModifiedAt(sourceModel.getModifiedAt());
        setDeliveredAt(sourceModel.getDeliveredAt());
        setReadAt(sourceModel.getReadAt());
        setEditedAt(sourceModel.getEditedAt());
        setDeletedAt(sourceModel.getDeletedAt());
        setRead(sourceModel.isRead());
        setBody(sourceModel.getBody());
        setCaption(sourceModel.getCaption());
        setQuotedMessageId(sourceModel.getQuotedMessageId());
        setForwardSecurityMode(sourceModel.getForwardSecurityMode());
    }
}

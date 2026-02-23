package ch.threema.domain.protocol.csp.messages;

import ch.threema.domain.models.GroupId;

public abstract class AbstractGroupMessage extends AbstractMessage {
    private String groupCreator;
    private GroupId apiGroupId;

    public String getGroupCreator() {
        return groupCreator;
    }

    public void setGroupCreator(String groupCreator) {
        this.groupCreator = groupCreator;
    }

    public GroupId getApiGroupId() {
        return apiGroupId;
    }

    public void setApiGroupId(GroupId groupId) {
        this.apiGroupId = groupId;
    }

    @Override
    public boolean flagGroupMessage() {
        return true;
    }
}

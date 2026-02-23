package ch.threema.storage.models.access;

public class GroupAccessModel {
    private Access canReceiveMessage = new Access();
    private Access canSendMessage = new Access();

    public GroupAccessModel setCanReceiveMessageAccess(Access access) {
        this.canReceiveMessage = access;
        return this;
    }

    public Access getCanReceiveMessageAccess() {
        return this.canReceiveMessage;
    }

    public GroupAccessModel setCanSendMessageAccess(Access access) {
        this.canSendMessage = access;
        return this;
    }

    public Access getCanSendMessageAccess() {
        return this.canSendMessage;
    }
}

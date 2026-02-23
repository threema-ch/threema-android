package ch.threema.storage.models;

public class MessageModel extends AbstractMessageModel {
    public static final String TABLE = "message";

    public MessageModel() {
        super();
    }

    public MessageModel(boolean isStatusMessage) {
        super(isStatusMessage);
    }

    @Override
    public String toString() {
        return "message.id = " + this.getId();
    }
}

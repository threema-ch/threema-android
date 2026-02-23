package ch.threema.storage.models;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;

public class ServerMessageModel {
    /**
     * The table name
     */
    public static final String TABLE = "server_messages";
    /**
     * The message as string
     */
    public static final String COLUMN_MESSAGE = "message";
    /**
     * The message type
     */
    public static final String COLUMN_TYPE = "type";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_ALERT, TYPE_ERROR})
    public @interface ServerMessageModelType {
    }

    public static final int TYPE_ALERT = 0;
    public static final int TYPE_ERROR = 1;

    private final String message;
    private final @ServerMessageModelType int type;

    public ServerMessageModel(String message, @ServerMessageModelType int type) {
        this.message = message;
        this.type = type;
    }

    public String getMessage() {
        return this.message;
    }

    @ServerMessageModelType
    public int getType() {
        return this.type;
    }

}

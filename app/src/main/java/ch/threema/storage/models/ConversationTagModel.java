package ch.threema.storage.models;

import java.util.Date;

public class ConversationTagModel {
    public static final String TABLE = "conversation_tag";
    public static final String COLUMN_CONVERSATION_UID = "conversationUid";
    public static final String COLUMN_TAG = "tag";
    public static final String COLUMN_CREATED_AT = "createdAt";

    private String conversationUid;
    private String tag;
    private Date createdAt;

    public ConversationTagModel(String conversationUid, String tag) {
        this.conversationUid = conversationUid;
        this.tag = tag;
        this.createdAt = new Date();
    }

    public ConversationTagModel(String conversationUid, ConversationTag tag) {
        this(conversationUid, tag.value);
    }

    public ConversationTagModel() {
    }


    public ConversationTagModel setConversationUid(String conversationUid) {
        this.conversationUid = conversationUid;
        return this;
    }

    public String getConversationUid() {
        return this.conversationUid;
    }

    public ConversationTagModel setTag(String tag) {
        this.tag = tag;
        return this;
    }

    public String getTag() {
        return this.tag;
    }

    public ConversationTagModel setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getCreatedAt() {
        return this.createdAt;
    }
}

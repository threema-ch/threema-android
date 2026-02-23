package ch.threema.storage.models;

public class GroupMemberModel {

    public static final String TABLE = "group_member";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_IDENTITY = "identity";
    public static final String COLUMN_GROUP_ID = "groupId";

    private int id;
    private String identity;
    private int groupId;

    public int getGroupId() {
        return groupId;
    }

    public GroupMemberModel setGroupId(int groupId) {
        this.groupId = groupId;
        return this;
    }

    public String getIdentity() {
        return identity;
    }

    public GroupMemberModel setIdentity(String identity) {
        this.identity = identity;
        return this;
    }

    public int getId() {
        return id;
    }

    public GroupMemberModel setId(int id) {
        this.id = id;
        return this;
    }
}

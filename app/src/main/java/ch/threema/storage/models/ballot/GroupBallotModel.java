package ch.threema.storage.models.ballot;

public class GroupBallotModel implements LinkBallotModel {
    public static final String TABLE = "group_ballot";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_GROUP_ID = "groupId";
    public static final String COLUMN_BALLOT_ID = "ballotId";

    private int id;
    private int groupId;
    private int ballotId;

    public int getId() {
        return this.id;
    }

    public GroupBallotModel setId(int id) {
        this.id = id;
        return this;
    }

    public int getGroupId() {
        return groupId;
    }

    public GroupBallotModel setGroupId(int groupId) {
        this.groupId = groupId;
        return this;
    }

    public int getBallotId() {
        return ballotId;
    }

    @Override
    public Type getType() {
        return Type.GROUP;
    }

    public GroupBallotModel setBallotId(int ballotId) {
        this.ballotId = ballotId;
        return this;
    }
}

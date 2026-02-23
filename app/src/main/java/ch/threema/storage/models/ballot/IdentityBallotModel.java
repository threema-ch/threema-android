package ch.threema.storage.models.ballot;

public class IdentityBallotModel implements LinkBallotModel {
    public static final String TABLE = "identity_ballot";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_IDENTITY = "identity";
    public static final String COLUMN_BALLOT_ID = "ballotId";

    private int id;
    private String identity;
    private int ballotId;

    public int getId() {
        return this.id;
    }

    public IdentityBallotModel setId(int id) {
        this.id = id;
        return this;
    }

    public String getIdentity() {
        return identity;
    }

    public IdentityBallotModel setIdentity(String identity) {
        this.identity = identity;
        return this;
    }

    public int getBallotId() {
        return ballotId;
    }

    @Override
    public Type getType() {
        return Type.CONTACT;
    }

    public IdentityBallotModel setBallotId(int ballotId) {
        this.ballotId = ballotId;
        return this;
    }
}

package ch.threema.storage.models.ballot;

import java.util.Date;

public class BallotVoteModel {
    public static final String TABLE = "ballot_vote";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_BALLOT_ID = "ballotId";
    public static final String COLUMN_BALLOT_CHOICE_ID = "ballotChoiceId";
    public static final String COLUMN_VOTING_IDENTITY = "votingIdentity";
    public static final String COLUMN_CHOICE = "choice";
    public static final String COLUMN_CREATED_AT = "createdAt";
    public static final String COLUMN_MODIFIED_AT = "modifiedAt";

    private int id;
    private int ballotId;
    private int ballotChoiceId;
    private String votingIdentity;
    private int choice;
    private Date createdAt;
    private Date modifiedAt;

    public int getBallotChoiceId() {
        return ballotChoiceId;
    }

    public BallotVoteModel setBallotChoiceId(int ballotChoiceId) {
        this.ballotChoiceId = ballotChoiceId;
        return this;
    }

    public int getId() {
        return id;
    }

    public BallotVoteModel setId(int id) {
        this.id = id;
        return this;
    }

    public String getVotingIdentity() {
        return votingIdentity;
    }

    public BallotVoteModel setVotingIdentity(String votingIdentity) {
        this.votingIdentity = votingIdentity;
        return this;
    }

    public int getChoice() {
        return choice;
    }

    public BallotVoteModel setChoice(int choice) {
        this.choice = choice;
        return this;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public BallotVoteModel setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getModifiedAt() {
        return modifiedAt;
    }

    public BallotVoteModel setModifiedAt(Date modifiedAt) {
        this.modifiedAt = modifiedAt;
        return this;
    }

    public int getBallotId() {
        return ballotId;
    }

    public BallotVoteModel setBallotId(int ballotId) {
        this.ballotId = ballotId;
        return this;
    }
}

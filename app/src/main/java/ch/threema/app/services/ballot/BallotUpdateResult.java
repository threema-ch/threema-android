package ch.threema.app.services.ballot;


import ch.threema.storage.models.ballot.BallotModel;

public class BallotUpdateResult extends BallotResult {
    public enum Operation {
        CREATE,
        UPDATE,
        CLOSE
    }

    private final BallotModel ballotModel;
    private final Operation operation;

    public BallotUpdateResult(BallotModel ballotModel, Operation operation) {
        this.ballotModel = ballotModel;
        this.operation = operation;
    }

    public BallotModel getBallotModel() {
        return ballotModel;
    }

    public Operation getOperation() {
        return operation;
    }
}

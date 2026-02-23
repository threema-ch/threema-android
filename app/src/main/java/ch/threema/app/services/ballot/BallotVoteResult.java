package ch.threema.app.services.ballot;


public class BallotVoteResult {
    private final boolean success;

    public BallotVoteResult(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}

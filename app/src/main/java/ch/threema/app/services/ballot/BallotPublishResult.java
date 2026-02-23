package ch.threema.app.services.ballot;


public class BallotPublishResult extends BallotResult {
    @Override
    protected BallotPublishResult error(int messageResourceId) {
        super.error(messageResourceId);
        return this;
    }
}

package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.storage.models.ballot.BallotModel;

public interface BallotVoteListener {
    @AnyThread
    void onSelfVote(final BallotModel ballotModel);

    @AnyThread
    void onVoteChanged(final BallotModel ballotModel, String votingIdentity, boolean isFirstVote);

    @AnyThread
    void onVoteRemoved(final BallotModel ballotModel, String votingIdentity);

    /**
     * return true, if the event have to be handled!
     *
     * @param ballotModel
     * @return
     */
    @AnyThread
    boolean handle(final BallotModel ballotModel);
}

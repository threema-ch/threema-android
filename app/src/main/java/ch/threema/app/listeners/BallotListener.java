package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.storage.models.ballot.BallotModel;

public interface BallotListener {
    @AnyThread
    void onClosed(final BallotModel ballotModel);

    @AnyThread
    void onModified(final BallotModel ballotModel);

    @AnyThread
    void onCreated(final BallotModel ballotModel);

    @AnyThread
    void onRemoved(final BallotModel ballotModel);

    /**
     * return true, if the event has to be handled
     */
    @AnyThread
    boolean handle(final BallotModel ballotModel);
}

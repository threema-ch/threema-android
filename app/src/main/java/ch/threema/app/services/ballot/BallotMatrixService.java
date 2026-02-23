package ch.threema.app.services.ballot;

import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotVoteModel;

public interface BallotMatrixService {

    interface Participant {
        boolean hasVoted();

        String getIdentity();

        int getPos();
    }

    interface Choice {
        BallotChoiceModel getBallotChoiceModel();

        boolean isWinner();

        int getVoteCount();

        int getPos();
    }

    interface DataKeyBuilder {
        String build(Participant p, Choice c);
    }

    Participant createParticipant(String identity);

    Choice createChoice(BallotChoiceModel choiceModel);

    BallotMatrixService addVote(BallotVoteModel ballotVoteModel) throws ThreemaException;

    BallotMatrixData finish();
}

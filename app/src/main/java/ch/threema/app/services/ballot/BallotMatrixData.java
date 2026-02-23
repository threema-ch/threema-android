package ch.threema.app.services.ballot;

import java.util.List;

import ch.threema.app.services.ballot.BallotMatrixService.Choice;
import ch.threema.app.services.ballot.BallotMatrixService.Participant;
import ch.threema.storage.models.ballot.BallotVoteModel;

public interface BallotMatrixData {
    List<Participant> getParticipants();

    List<Choice> getChoices();

    BallotVoteModel getVote(final Participant participant, final Choice choice);
}

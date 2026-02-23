package ch.threema.app.services.ballot;

import java.util.List;
import java.util.Map;

import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.BallotVoteModel;

public class BallotMatrixDataImpl implements BallotMatrixData {

    private final List<BallotMatrixService.Participant> participants;
    private final List<BallotMatrixService.Choice> choices;
    private final Map<String, BallotVoteModel> data;
    private final BallotMatrixService.DataKeyBuilder keyBuilder;

    public BallotMatrixDataImpl(BallotModel ballotModel,
                                List<BallotMatrixService.Participant> participants,
                                List<BallotMatrixService.Choice> choices,
                                Map<String, BallotVoteModel> data,
                                BallotMatrixService.DataKeyBuilder keyBuilder) {
        this.participants = participants;
        this.choices = choices;
        this.data = data;
        this.keyBuilder = keyBuilder;
    }

    @Override
    public List<BallotMatrixService.Participant> getParticipants() {
        return this.participants;
    }

    @Override
    public List<BallotMatrixService.Choice> getChoices() {
        return this.choices;
    }

    @Override
    public BallotVoteModel getVote(final BallotMatrixService.Participant participant, final BallotMatrixService.Choice choice) {
        synchronized (this.data) {
            String key = this.keyBuilder.build(participant, choice);
            if (key != null) {
                return this.data.get(key);
            }
        }
        return null;
    }


}

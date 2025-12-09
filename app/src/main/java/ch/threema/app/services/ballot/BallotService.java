/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.services.ballot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.base.SessionScoped;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.MessageTooLongException;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.ballot.BallotId;
import ch.threema.domain.protocol.csp.messages.ballot.BallotSetupInterface;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVoteInterface;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.BallotVoteModel;
import ch.threema.storage.models.ballot.LinkBallotModel;

@SessionScoped
public interface BallotService {

    interface BallotFilter {
        MessageReceiver<?> getReceiver();

        BallotModel.State[] getStates();

        default String createdOrNotVotedByIdentity() {
            return null;
        }
    }

    BallotModel create(
        ContactModel contactModel,
        String description,
        BallotModel.State state,
        BallotModel.Assessment assessment,
        BallotModel.Type type,
        BallotModel.ChoiceType choiceType,
        @NonNull BallotId ballotId
    ) throws NotAllowedException;

    BallotModel create(
        GroupModel groupModel,
        String description,
        BallotModel.State state,
        BallotModel.Assessment assessment,
        BallotModel.Type type,
        BallotModel.ChoiceType choiceType,
        @NonNull BallotId ballotId
    ) throws NotAllowedException;

    void modifyFinished(
        @NonNull BallotModel ballotModel,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws MessageTooLongException;

    boolean viewingBallot(BallotModel ballotModel, boolean view);

    boolean update(BallotModel ballotModel, BallotChoiceModel choice) throws NotAllowedException;

    boolean close(
        Integer ballotModelId,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws NotAllowedException, MessageTooLongException;

    boolean send(
        BallotModel ballotModel,
        ListenerManager.HandleListener<BallotListener> handleListener,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws MessageTooLongException;

    @Nullable
    BallotModel get(int ballotId);

    BallotModel get(String id, String creator);

    List<BallotModel> getBallots(BallotFilter filter) throws NotAllowedException;

    long countBallots(BallotFilter filter);

    boolean belongsToMe(Integer ballotModelId, MessageReceiver<?> messageReceiver) throws NotAllowedException;

    /**
     * Create / Update ballot from createMessage
     *
     * @param createMessage BallotCreateMessage received from server
     * @return BallotUpdateResult
     * @throws ThreemaException if an error occurred during processing
     */
    @NonNull
    BallotUpdateResult update(
        @NonNull BallotSetupInterface createMessage,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws ThreemaException, BadMessageException;

    boolean update(BallotModel ballotModel);

    BallotPublishResult publish(
        MessageReceiver<?> messageReceiver,
        BallotModel ballotModel,
        AbstractMessageModel abstractMessageModel,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws NotAllowedException, MessageTooLongException;

    BallotPublishResult publish(
        MessageReceiver messageReceiver,
        BallotModel ballotModel,
        AbstractMessageModel abstractMessageModel,
        @Nullable Collection<String> receivingIdentities,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws NotAllowedException, MessageTooLongException;

    LinkBallotModel getLinkedBallotModel(BallotModel ballotModel) throws NotAllowedException;

    boolean remove(BallotModel ballotModel) throws NotAllowedException;

    boolean remove(MessageReceiver<?> receiver);

    /*
    choice stuff
     */
    List<BallotChoiceModel> getChoices(Integer ballotModelId) throws NotAllowedException;

    /*
    voting stuff
    */

    BallotVoteResult vote(Integer ballotModelId, Map<Integer, Integer> values, @NonNull TriggerSource triggerSource) throws NotAllowedException;

    BallotVoteResult vote(BallotVoteInterface ballotVoteMessage) throws NotAllowedException;

    /**
     * return the count of votings depending on the ballot properties
     */
    int getVotingCount(BallotChoiceModel choiceModel);

    boolean removeVotes(MessageReceiver<?> receiver, String identity);

    @NonNull
    List<String> getVotedParticipants(Integer ballotModelId);

    @NonNull
    List<String> getPendingParticipants(Integer ballotModelId);

    @NonNull
    String[] getParticipants(Integer ballotModelId);

    boolean hasVoted(Integer ballotModelId, String fromIdentity);

    /**
     * get my votes
     */
    @NonNull
    List<BallotVoteModel> getMyVotes(Integer ballotModelId) throws NotAllowedException;

    /**
     * get all votes of a ballot
     */
    List<BallotVoteModel> getBallotVotes(Integer ballotModelId) throws NotAllowedException;

    MessageReceiver<?> getReceiver(BallotModel ballotModel);

    BallotMatrixData getMatrixData(int ballotModelId);

    boolean removeAll();
}

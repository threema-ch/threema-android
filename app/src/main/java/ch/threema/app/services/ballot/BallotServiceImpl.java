/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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


import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.threema.app.R;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.MessageTooLongException;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.ballot.BallotCreateInterface;
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotData;
import ch.threema.domain.protocol.csp.messages.ballot.BallotDataChoice;
import ch.threema.domain.protocol.csp.messages.ballot.BallotId;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVoteInterface;
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.GroupBallotModelFactory;
import ch.threema.storage.factories.IdentityBallotModelFactory;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.BallotVoteModel;
import ch.threema.storage.models.ballot.GroupBallotModel;
import ch.threema.storage.models.ballot.IdentityBallotModel;
import ch.threema.storage.models.ballot.LinkBallotModel;

public class BallotServiceImpl implements BallotService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("BallotServiceImpl");

	private static final int REQUIRED_CHOICE_COUNT = 2;

	private final SparseArray<BallotModel> ballotModelCache;
	private final SparseArray<LinkBallotModel> linkBallotModelCache;

	private final DatabaseServiceNew databaseServiceNew;
	private final UserService userService;
	private final GroupService groupService;
	private final ContactService contactService;
	private final ServiceManager serviceManager;

	private int openBallotId = 0;

	public BallotServiceImpl(SparseArray<BallotModel> ballotModelCache,
	                         SparseArray<LinkBallotModel> linkBallotModelCache,
	                         DatabaseServiceNew databaseServiceNew,
	                         UserService userService,
	                         GroupService groupService,
	                         ContactService contactService,
	                         ServiceManager serviceManager) {
		this.ballotModelCache = ballotModelCache;
		this.linkBallotModelCache = linkBallotModelCache;
		this.databaseServiceNew = databaseServiceNew;
		this.userService = userService;
		this.groupService = groupService;
		this.contactService = contactService;
		this.serviceManager = serviceManager;
	}

	@Override
	public BallotModel create(GroupModel groupModel,
							  String description,
							  BallotModel.State state,
							  BallotModel.Assessment assessment,
							  BallotModel.Type type,
							  BallotModel.ChoiceType choiceType) throws NotAllowedException {

		final BallotModel model = this.create(description, state, assessment, type, choiceType);
		if (model != null) {
			this.link(groupModel, model);
			//handle
		}


		return model;
	}

	@Override
	public boolean modifyFinished(final BallotModel ballotModel) throws MessageTooLongException {
		if(ballotModel != null) {
			switch (ballotModel.getState()) {
				case TEMPORARY:
					ballotModel.setState(BallotModel.State.OPEN);
					try {
						this.checkAccess();
						this.databaseServiceNew.getBallotModelFactory().update(
								ballotModel
						);
					} catch (NotAllowedException e) {
						logger.error("Exception", e);
						return false;
					}

					try {
						return this.send(ballotModel, listener -> {
							if (listener.handle(ballotModel)) {
								listener.onCreated(ballotModel);
							}
						});
					} catch (MessageTooLongException e) {
						ballotModel.setState(BallotModel.State.TEMPORARY);
						this.databaseServiceNew.getBallotModelFactory().update(
							ballotModel
						);
						throw new MessageTooLongException();
					}
				default:
					this.handleModified(ballotModel);
					break;
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean viewingBallot(BallotModel ballotModel,  boolean view) {
		if(ballotModel != null) {
			if(view) {
				ballotModel.setLastViewedAt(new Date());
				this.databaseServiceNew.getBallotModelFactory().update(
						ballotModel);
				this.openBallotId = ballotModel.getId();
				//disabled for the moment!
//						this.handleModified(ballotModel);
				return true;
			}
			else if(this.openBallotId == ballotModel.getId()) {
				this.openBallotId = 0;
			}

		}
		return false;
	}

	@Override
	public BallotModel create(ContactModel contactModel,
							  String description,
							  BallotModel.State state,
							  BallotModel.Assessment assessment,
							  BallotModel.Type type,
							  BallotModel.ChoiceType choiceType) throws NotAllowedException {
		final BallotModel model = this.create(description,state,assessment,type, choiceType);
		if(model != null) {
			this.link(contactModel, model);
		}

		return model;
	}

	private BallotModel create(String description,
							  BallotModel.State state,
							  BallotModel.Assessment assessment,
							  BallotModel.Type type,
							  BallotModel.ChoiceType choiceType) throws NotAllowedException {
		//create a new blank model
		try {
			this.checkAccess();



			final BallotModel ballotModel = new BallotModel();
			//unique id
			String randomId = UUID.randomUUID().toString();
			BallotId newBallotId = new BallotId(
					Utils.hexStringToByteArray(
							randomId.substring(randomId.length() - (ProtocolDefines.BALLOT_ID_LEN * 2))
					));

			ballotModel.setApiBallotId(Utils.byteArrayToHexString(newBallotId.getBallotId()));
			ballotModel.setCreatorIdentity(this.userService.getIdentity());
			ballotModel.setCreatedAt(new Date());
			ballotModel.setModifiedAt(new Date());
			ballotModel.setName(description);
			ballotModel.setState(state);
			ballotModel.setAssessment(assessment);
			ballotModel.setType(type);
			ballotModel.setChoiceType(choiceType);
			ballotModel.setDisplayType(BallotModel.DisplayType.LIST_MODE); // default display type for ballots created on mobile client.
			ballotModel.setLastViewedAt(new Date());

			this.databaseServiceNew.getBallotModelFactory().create(
					ballotModel
			);

			this.cache(ballotModel);

			return ballotModel;

		}
		catch (NotAllowedException notAllowedException) {
			logger.error("Not allowed", notAllowedException);
			throw notAllowedException;
		}
		catch (ThreemaException e) {
			logger.error("Exception", e);
			return null;
		}
	}

	@Override
	public boolean update(BallotModel ballotModel, BallotChoiceModel choice) throws NotAllowedException {
		if(choice.getId() > 0 && choice.getBallotId() > 0 && choice.getBallotId() != ballotModel.getId()) {
			throw new NotAllowedException("choice already set on another ballot");
		}

		if(choice.getApiBallotChoiceId() <= 0) {
			throw new NotAllowedException("no api ballot choice id set");
		}
		choice.setBallotId(ballotModel.getId());

		if(choice.getCreatedAt() == null) {
			choice.setCreatedAt(new Date());
		}

		choice.setModifiedAt(new Date());

		return this.databaseServiceNew.getBallotChoiceModelFactory().create(
				choice
		);
	}

	@Override
	public boolean close(Integer ballotModelId) throws NotAllowedException, MessageTooLongException {
		//be sure to use the cached ballot model!
		final BallotModel ballotModel = this.get(ballotModelId);

		//if i am not the creator
		if(!BallotUtil.canClose(ballotModel, this.userService.getIdentity())) {
			throw new NotAllowedException();
		}

		MessageReceiver messageReceiver = this.getReceiver(ballotModel);
		if(messageReceiver == null) {
			return false;
		}

		//save model
		ballotModel.setState(BallotModel.State.CLOSED);
		if (this.update(ballotModel)) {
			return this.send(ballotModel, listener -> {
				if (listener.handle(ballotModel)) {
					listener.onClosed(ballotModel);
				}
			});
		}
		return false;
	}

	@Override
	public boolean send(BallotModel ballotModel, ListenerManager.HandleListener<BallotListener> handleListener) throws MessageTooLongException {
		//add message
		if (TestUtil.compare(userService.getIdentity(), ballotModel.getCreatorIdentity())) {
			//ok, i am the creator.... send a message to every participant
			try {
				if (serviceManager.getMessageService() != null) {
					if (serviceManager.getMessageService().sendBallotMessage(ballotModel) != null) {
						ListenerManager.ballotListeners.handle(handleListener);
						return true;
					}
				}
			} catch (ThreemaException e) {
				logger.error("Exception", e);
				if (e instanceof MessageTooLongException) {
					throw new MessageTooLongException();
				}
			}
		}
		return false;
	}

	@Override
	@Nullable
	public BallotModel get(int ballotId) {
		BallotModel model = this.getFromCache(ballotId);
		if(model == null) {
			model = this.databaseServiceNew.getBallotModelFactory().getById(
					ballotId
			);

			this.cache(model);
		}
		return model;
	}

	@Override
	@NonNull
	public BallotUpdateResult update(BallotCreateInterface createMessage) throws ThreemaException, BadMessageException {
		//check if allowed
		BallotData data = createMessage.getData();
		if (data == null) {
			throw new ThreemaException("invalid format");
		}

		final BallotModel.State toState;
		final BallotModel ballotModel;

		Date date = ((AbstractMessage)createMessage).getDate();
		BallotModel existingModel = this.get(createMessage.getBallotId().toString(), createMessage.getBallotCreator());

		if (existingModel != null) {
			if (data.getDisplayType() != null && existingModel.getDisplayType() != null && data.getDisplayType().ordinal() != existingModel.getDisplayType().ordinal()) {
				throw new BadMessageException("Ballot display mode not allowed to change. Discarding message");
			}
			if (data.getState() == BallotData.State.CLOSED) {
				ballotModel = existingModel;
				toState = BallotModel.State.CLOSED;
			}
			else {
				throw new BadMessageException("Ballot with same ID already exists. Discarding message.");
			}
		} else {
			if (data.getState() != BallotData.State.CLOSED) {
				ballotModel = new BallotModel();
				ballotModel.setCreatorIdentity(createMessage.getBallotCreator());
				ballotModel.setApiBallotId(createMessage.getBallotId().toString());
				ballotModel.setCreatedAt(date);
				ballotModel.setLastViewedAt(null);
				toState = BallotModel.State.OPEN;
			} else {
				throw new BadMessageException("New ballot with closed state requested. Discarding message.");
			}
		}

		ballotModel.setName(data.getDescription());
		ballotModel.setModifiedAt(new Date());

		switch (data.getAssessmentType()) {
			case MULTIPLE:
				ballotModel.setAssessment(BallotModel.Assessment.MULTIPLE_CHOICE);
				break;
			case SINGLE:
				ballotModel.setAssessment(BallotModel.Assessment.SINGLE_CHOICE);
				break;
		}

		switch (data.getType()) {
			case RESULT_ON_CLOSE:
				ballotModel.setType(BallotModel.Type.RESULT_ON_CLOSE);
				break;
			case INTERMEDIATE:
				ballotModel.setType(BallotModel.Type.INTERMEDIATE);
				break;
		}

		switch (data.getChoiceType()) {
			case TEXT:
				ballotModel.setChoiceType(BallotModel.ChoiceType.TEXT);
				break;
		}

		switch (data.getDisplayType()) {
			case SUMMARY_MODE:
				ballotModel.setDisplayType(BallotModel.DisplayType.SUMMARY_MODE);
				break;
			case LIST_MODE:
			default:
				ballotModel.setDisplayType(BallotModel.DisplayType.LIST_MODE);
				break;

		}

		ballotModel.setState(toState);

		if (toState == BallotModel.State.OPEN) {
			this.databaseServiceNew.getBallotModelFactory().create(
					ballotModel
			);
		}
		else {
			this.databaseServiceNew.getBallotModelFactory().update(
					ballotModel
			);
		}

		if(createMessage instanceof GroupPollSetupMessage) {
			GroupModel groupModel;
			groupModel = this.groupService.getByGroupMessage((GroupPollSetupMessage) createMessage);
			if (groupModel == null) {
				throw new ThreemaException("invalid group");
			}
			//link with group
			this.link(groupModel, ballotModel);
		}
		else if(createMessage instanceof PollSetupMessage) {
			ContactModel contactModel = this.contactService.getByIdentity(createMessage.getBallotCreator());
			if (contactModel == null) {
				throw new ThreemaException("invalid identity");
			}
			//link with group
			this.link(contactModel, ballotModel);
		}
		else {
			throw new ThreemaException("invalid");
		}

		if (toState == BallotModel.State.CLOSED && ballotModel.getDisplayType() == BallotModel.DisplayType.LIST_MODE) {
			//first remove all previously known votes if result should be shown in list mode to ensure a common result for all participants
			this.databaseServiceNew.getBallotVoteModelFactory().deleteByBallotId(
					ballotModel.getId()
			);
		}

		//create choices of ballot
		for (BallotDataChoice apiChoice: data.getChoiceList()) {
			//check if choice already exist
			BallotChoiceModel ballotChoiceModel = this.getChoiceByApiId(ballotModel, apiChoice.getId());
			if(ballotChoiceModel == null) {
				ballotChoiceModel = new BallotChoiceModel();
				ballotChoiceModel.setBallotId(ballotModel.getId());
				ballotChoiceModel.setApiBallotChoiceId(apiChoice.getId());
			}

			// save returned total vote count if ballot is in summary mode (case broadcast poll)
			if (ballotModel.getDisplayType() == BallotModel.DisplayType.SUMMARY_MODE) {
				ballotChoiceModel.setVoteCount(apiChoice.getTotalVotes());
			}

			ballotChoiceModel.setName(apiChoice.getName());
			ballotChoiceModel.setOrder(apiChoice.getOrder());
			switch (data.getChoiceType()) {
				case TEXT:
					ballotChoiceModel.setType(BallotChoiceModel.Type.Text);
					break;
			}
			ballotChoiceModel.setCreatedAt(date);

			this.databaseServiceNew.getBallotChoiceModelFactory().createOrUpdate(
					ballotChoiceModel
			);

			//save individual votes received in case result should be shown in list mode for each participant (case mobile client user poll)
			if (ballotModel.getDisplayType() == BallotModel.DisplayType.LIST_MODE && !data.getParticipants().isEmpty()) {
				int participantPos = 0;
				for(String p: data.getParticipants()) {
					BallotVoteModel voteModel = new BallotVoteModel();
					voteModel.setBallotId(ballotModel.getId());
					voteModel.setBallotChoiceId(ballotChoiceModel.getId());
					voteModel.setVotingIdentity(p);
					voteModel.setChoice(apiChoice.getResult(participantPos));
					voteModel.setModifiedAt(new Date());
					voteModel.setCreatedAt(new Date());

					this.databaseServiceNew.getBallotVoteModelFactory().create(
						voteModel
					);

					participantPos++;
				}
			}
		}

		if (toState == BallotModel.State.OPEN) {
			this.cache(ballotModel);
			this.send(ballotModel, listener -> {
				if (listener.handle(ballotModel)) {
					listener.onCreated(ballotModel);
				}
			});

			return new BallotUpdateResult(ballotModel, BallotUpdateResult.Operation.CREATE);
		}
		else {
			// toState == BallotModel.State.CLOSED
			this.send(ballotModel, listener -> {
				if (listener.handle(ballotModel)) {
					listener.onClosed(ballotModel);
				}
			});
			return new BallotUpdateResult(ballotModel, BallotUpdateResult.Operation.CLOSE);
		}
	}

	@Override
	public BallotModel get(String id, String creator) {
		if(TestUtil.empty(id, creator)) {
			return null;
		}

		BallotModel model = this.getFromCache(id, creator);
		if(model == null) {
			model = this.databaseServiceNew.getBallotModelFactory().getByApiBallotIdAndIdentity(
					id,
					creator
			);

			this.cache(model);
		}

		return model;
	}

	@Override
	public List<BallotModel> getBallots(final BallotFilter filter) {
		List<BallotModel> ballots = this.databaseServiceNew.getBallotModelFactory().filter(
				filter
		);
		this.cache(ballots);

		if(filter != null) {
			return Functional.filter(ballots, new IPredicateNonNull<BallotModel>() {
				@Override
				public boolean apply(@NonNull BallotModel type) {
					return filter.filter(type);
				}
			});
		}
		else {
			return ballots;
		}
	}

	@Override
	public long countBallots(final BallotFilter filter) {
		return this.databaseServiceNew.getBallotModelFactory().count(filter);
	}

	@Override
	public List<BallotChoiceModel> getChoices(Integer ballotModelId) throws NotAllowedException {
		if(ballotModelId == null) {
			throw new NotAllowedException();
		}

		return this.databaseServiceNew.getBallotChoiceModelFactory().getByBallotId(
				ballotModelId
		);
	}

	@Override
	public int getVotingCount(BallotChoiceModel choiceModel) {
		BallotModel b = this.get(choiceModel.getBallotId());
		if(b == null) {
			return 0;
		}

		return this.getCalculatedVotingCount(choiceModel);
	}


	@Override
	public boolean update(final BallotModel ballotModel) {
		ballotModel.setModifiedAt(new Date());
		this.databaseServiceNew.getBallotModelFactory().update(
				ballotModel);

		this.handleModified(ballotModel);
		return true;
	}

	@Override
	public boolean removeVotes(final MessageReceiver receiver, final String identity) {
		List<BallotModel> ballots = this.getBallots(new BallotFilter() {
			@Override
			public MessageReceiver getReceiver() {
				return receiver;
			}

			@Override
			public BallotModel.State[] getStates() {
				return new BallotModel.State[0];
			}

			@Override
			public boolean filter(BallotModel ballotModel) {
				return true;
			}
		});

		for(final BallotModel ballotModel: ballots) {
			this.databaseServiceNew.getBallotVoteModelFactory().deleteByBallotIdAndVotingIdentity(
					ballotModel.getId(),
					identity
			);

			ListenerManager.ballotVoteListeners.handle(new ListenerManager.HandleListener<BallotVoteListener>() {
				@Override
				public void handle(BallotVoteListener listener) {
					if(listener.handle(ballotModel)) {
						listener.onVoteRemoved(ballotModel, identity);
					}
				}
			});
		}

		return true;
	}

	@Override
	@NonNull
	public List<String> getVotedParticipants(Integer ballotModelId) {
		List<String> identities = new ArrayList<>();

		if(ballotModelId !=null) {
			List<BallotVoteModel> ballotVotes = this.getBallotVotes(ballotModelId);
			for (BallotVoteModel v : ballotVotes) {
				if (!identities.contains(v.getVotingIdentity())) {
					identities.add(v.getVotingIdentity());
				}
			}
		}
		return identities;
	}

	@Override
	@NonNull
	public List<String> getPendingParticipants(Integer ballotModelId) {
		String[] allParticipants = this.getParticipants(ballotModelId);
		List<String> pendingParticipants = new ArrayList<>();
		if(allParticipants.length > 0) {
			for(String i: allParticipants) {
				List<BallotVoteModel> voteModels = this.getVotes(ballotModelId, i);
				if(voteModels == null || voteModels.size() == 0) {
					pendingParticipants.add(i);
				}
			}
		}

		return pendingParticipants;
	}


	@Override
	@NonNull
	public String[] getParticipants(MessageReceiver messageReceiver) {
		if(messageReceiver != null) {
			switch (messageReceiver.getType()) {
				case MessageReceiver.Type_GROUP:
					return this.groupService.getGroupIdentities(((GroupMessageReceiver)messageReceiver).getGroup());

				case MessageReceiver.Type_CONTACT:
					return new String[] {
							this.userService.getIdentity(),
							((ContactMessageReceiver)messageReceiver).getContact().getIdentity()
					};
				case MessageReceiver.Type_DISTRIBUTION_LIST:
					break;
			}
		}
		return new String[0];
	}

	@Override
	@NonNull
	public String[] getParticipants(Integer ballotModelId) {
		BallotModel b = this.get(ballotModelId);
		if (b != null) {
			try {
				LinkBallotModel link = this.getLinkedBallotModel(b);
				if (link != null) {
					switch (link.getType()) {
						case GROUP:
							GroupModel groupModel = this.getGroupModel(link);
							if (groupModel != null) {
								return this.groupService.getGroupIdentities(this.getGroupModel(link));
							}
							break;
						case CONTACT:
							ContactModel contactModel = this.getContactModel(link);
							if (contactModel != null) {
								return new String[]{
										this.userService.getIdentity(),
										contactModel.getIdentity()};
							}
							break;

						default:
							throw new NotAllowedException("invalid type");
					}
				}
			} catch (NotAllowedException e) {
				logger.error("Exception", e);
			}
		}

		return new String[0];
	}

	private List<BallotVoteModel> getVotes(Integer ballotModelId, String fromIdentity) {
		if(ballotModelId == null) {
			return null;
		}

		return this.databaseServiceNew.getBallotVoteModelFactory().getByBallotIdAndVotingIdentity(
				ballotModelId,
				fromIdentity
		);
	}

	@Override
	public boolean hasVoted(Integer ballotModelId, String fromIdentity) {
		if(ballotModelId == null) {
			return false;
		}

		return this.databaseServiceNew.getBallotVoteModelFactory().countByBallotIdAndVotingIdentity(
			ballotModelId,
			fromIdentity
		) > 0L;
	}

	@Override
	public List<BallotVoteModel> getMyVotes(Integer ballotModelId) {
		return this.getVotes(ballotModelId, this.userService.getIdentity());
	}

	@Override
	public List<BallotVoteModel> getBallotVotes(Integer ballotModelId) {
		if(ballotModelId == null) {
			return null;
		}
		return this.databaseServiceNew.getBallotVoteModelFactory().getByBallotId(
				ballotModelId);
	}


	@Override
	public boolean removeAll() {
		this.databaseServiceNew.getBallotModelFactory().deleteAll();
		this.databaseServiceNew.getBallotVoteModelFactory().deleteAll();
		this.databaseServiceNew.getBallotChoiceModelFactory().deleteAll();
		this.databaseServiceNew.getGroupBallotModelFactory().deleteAll();
		return true;
	}

	@Override
	public BallotPublishResult publish(MessageReceiver messageReceiver, final BallotModel ballotModel, AbstractMessageModel abstractMessageModel) throws NotAllowedException, MessageTooLongException {
		return this.publish(messageReceiver, ballotModel, abstractMessageModel, null);
	}

	@Override
	public BallotPublishResult publish(MessageReceiver messageReceiver,
	                                   final BallotModel ballotModel,
	                                   AbstractMessageModel abstractMessageModel,
	                                   @Nullable Collection<String> receivingIdentities
	) throws NotAllowedException, MessageTooLongException {
		BallotPublishResult result = new BallotPublishResult();

		this.checkAccess();

		if(!TestUtil.required(messageReceiver, ballotModel)) {
			return result;
		}

		// validate choices
		List<BallotChoiceModel> choices = this.getChoices(ballotModel.getId());
		if(choices == null || choices.size() < REQUIRED_CHOICE_COUNT) {
			return result.error(R.string.ballot_error_more_than_x_choices);
		}

		switch (messageReceiver.getType()) {
			case MessageReceiver.Type_GROUP:
				this.link(((GroupMessageReceiver)messageReceiver).getGroup(), ballotModel);
				break;

			case MessageReceiver.Type_CONTACT:
				this.link(((ContactMessageReceiver)messageReceiver).getContact(), ballotModel);
				break;
		}

		final boolean isClosing = ballotModel.getState() == BallotModel.State.CLOSED;

		BallotData ballotData = new BallotData();
		ballotData.setDescription(ballotModel.getName());

		switch (ballotModel.getChoiceType()) {
			case TEXT:
				ballotData.setChoiceType(BallotData.ChoiceType.TEXT);
				break;
		}

		switch (ballotModel.getType()) {
			case RESULT_ON_CLOSE:
				ballotData.setType(BallotData.Type.RESULT_ON_CLOSE);
				break;
			case INTERMEDIATE:
			default:
				ballotData.setType(BallotData.Type.INTERMEDIATE);
		}

		switch (ballotModel.getAssessment()) {
			case MULTIPLE_CHOICE:
				ballotData.setAssessmentType(BallotData.AssessmentType.MULTIPLE);
				break;
			case SINGLE_CHOICE:
			default:
				ballotData.setAssessmentType(BallotData.AssessmentType.SINGLE);
		}

		switch (ballotModel.getState()) {
			case CLOSED:
				ballotData.setState(BallotData.State.CLOSED);
				break;
			case OPEN:
			default:
				ballotData.setState(BallotData.State.OPEN);
		}

		switch (ballotModel.getDisplayType()) {
			case SUMMARY_MODE:
				ballotData.setDisplayType(BallotData.DisplayType.SUMMARY_MODE);
				break;
			case LIST_MODE:
			default:
				ballotData.setDisplayType(BallotData.DisplayType.LIST_MODE);
				break;
		}

		HashMap<String, Integer> votersPositions = new HashMap<>();
		List<BallotVoteModel> voteModels = null;
		int votersCount = 0;
		if (isClosing || receivingIdentities != null) {
			// load a list of voters
			String[] voters = this.getVotedParticipants(ballotModel.getId()).toArray(new String[0]);

			for (String s : voters) {
				ballotData.addParticipant(s);
				votersPositions.put(s, votersCount);
				votersCount++;
			}

			voteModels = this.getBallotVotes(ballotModel.getId());
		}
		// if closing, add result!
		for(final BallotChoiceModel c: choices) {
			BallotDataChoice choice = new BallotDataChoice(votersCount);
			choice.setId(c.getApiBallotChoiceId());
			choice.setName(c.getName());
			choice.setOrder(c.getOrder());

			if ((isClosing || receivingIdentities != null) && TestUtil.required(voteModels, votersPositions)) {

				for(BallotVoteModel v: Functional.filter(voteModels, new IPredicateNonNull<BallotVoteModel>() {
					@Override
					public boolean apply(@NonNull BallotVoteModel type) {
						return type.getBallotChoiceId() == c.getId();
					}
				})){
					int pos = votersPositions.get(v.getVotingIdentity());
					if(pos >= 0) {
						choice.addResult(pos, v.getChoice());
					}
				}

			}
			ballotData.getChoiceList().add(choice);
		}

		try {
			messageReceiver.createAndSendBallotSetupMessage(
				ballotData,
				ballotModel,
				abstractMessageModel,
				null,
				receivingIdentities
			);

			//set as open
			if(ballotModel.getState() == BallotModel.State.TEMPORARY) {
				ballotModel.setState(BallotModel.State.OPEN);
				ballotModel.setModifiedAt(new Date());

				this.databaseServiceNew.getBallotModelFactory().update(
						ballotModel
				);

			}

			result.success();
		} catch (ThreemaException e) {
			logger.error("create boxed ballot failed", e);
			if (e instanceof MessageTooLongException) {
				throw new MessageTooLongException();
			}
		}

		return result;
	}

	@Override
	public LinkBallotModel getLinkedBallotModel(BallotModel ballotModel) throws NotAllowedException {
		if(ballotModel == null) {
			return null;
		}

		LinkBallotModel linkBallotModel = this.getLinkModelFromCache(ballotModel.getId());
		if(linkBallotModel != null) {
			return linkBallotModel;
		}

		GroupBallotModel group = this.databaseServiceNew.getGroupBallotModelFactory().getByBallotId(
				ballotModel.getId());

		if(group != null) {
			this.cache(group);
			return group;
		}

		IdentityBallotModel identityBallotModel = this.databaseServiceNew.getIdentityBallotModelFactory().getByBallotId(
				ballotModel.getId()
		);
		if(identityBallotModel != null) {
			this.cache(identityBallotModel);
			return identityBallotModel;
		}

		return null;
	}

	@Override
	public boolean remove(final BallotModel ballotModel) throws NotAllowedException {
		if (serviceManager == null) {
			logger.debug("Unable to delete ballot, ServiceManager is not available");
			return false;
		}

		MessageService messageService;
		try {
			messageService = serviceManager.getMessageService();
		} catch (ThreemaException e) {
			logger.error("Unable to delete ballot, MessageService not available", e);
			return false;
		}

		if (ballotModel != null) {
			List<AbstractMessageModel> messageModels = messageService.getMessageForBallot(ballotModel);

			//remove all votes
			this.databaseServiceNew.getBallotVoteModelFactory().deleteByBallotId(
				ballotModel.getId());

			//remove choices
			this.databaseServiceNew.getBallotChoiceModelFactory().deleteByBallotId(
				ballotModel.getId());

			//remove link
			this.databaseServiceNew.getGroupBallotModelFactory().deleteByBallotId(
				ballotModel.getId());

			this.databaseServiceNew.getIdentityBallotModelFactory().deleteByBallotId(
				ballotModel.getId());

			//remove ballot
			this.databaseServiceNew.getBallotModelFactory().delete(
				ballotModel
			);

			// delete associated messages
			if (messageModels != null) {
				for (AbstractMessageModel m : messageModels) {
					if (m != null) {
						try {
							logger.debug("Removing ballot message {} of type {}", m.getApiMessageId() != null ? m.getApiMessageId() : m.getId(), m.getBallotData().getType());
							messageService.remove(m);
						} catch (Exception e) {
							logger.error("Unable to remove message", e);
						}
					}
				}
			}

			// remove ballot from cache
			this.resetCache(ballotModel);

			ListenerManager.ballotListeners.handle(listener -> {
				if (listener.handle(ballotModel)) {
					listener.onRemoved(ballotModel);
				}
			});
		}
		return true;
	}

	@Override
	public boolean remove(final MessageReceiver receiver) {
		try {
			for(BallotModel ballotModel: this.getBallots(new BallotFilter() {
				@Override
				public MessageReceiver getReceiver() {
					return receiver;
				}

				@Override
				public BallotModel.State[] getStates() {
					return null;
				}

				@Override
				public boolean filter(BallotModel ballotModel) {
					return true;
				}
			})) {
				if(!this.remove(ballotModel)) {
					return false;
				}
			}
		}
		catch (NotAllowedException x) {
			//do nothing more
			logger.error("Exception", x);
			return false;
		}

		return true;
	}

	@Override
	public boolean belongsToMe(Integer ballotModelId, MessageReceiver messageReceiver) throws NotAllowedException {
		BallotModel ballotModel = this.get(ballotModelId);

		if(!TestUtil.required(ballotModel, messageReceiver)) {
			return false;
		}

		switch (messageReceiver.getType()) {
			case MessageReceiver.Type_CONTACT:
			case MessageReceiver.Type_GROUP:
				LinkBallotModel l = this.getLinkedBallotModel(ballotModel);
				if(l != null) {
					if(messageReceiver.getType() == MessageReceiver.Type_GROUP && l.getType() == LinkBallotModel.Type.GROUP) {
						return ((GroupBallotModel)l).getGroupId() == ((GroupMessageReceiver)messageReceiver).getGroup().getId();
					}
					else if(messageReceiver.getType() == MessageReceiver.Type_CONTACT && l.getType() == LinkBallotModel.Type.CONTACT) {
						return TestUtil.compare(((IdentityBallotModel)l).getIdentity(), ((ContactMessageReceiver)messageReceiver).getContact().getIdentity());
					}
				}
		}

		return false;
	}

	@Override
	public BallotVoteResult vote(Integer ballotModelId, Map<Integer, Integer> voting) throws NotAllowedException {
		BallotModel ballotModel = this.get(ballotModelId);

		if(!TestUtil.required(ballotModel, voting)) {
			return new BallotVoteResult(false);
		}

		List<BallotChoiceModel> allChoices = this.getChoices(ballotModel.getId());
		if(allChoices == null) {
			return new BallotVoteResult(false);
		}

		LinkBallotModel link = this.getLinkedBallotModel(ballotModel);
		MessageReceiver messageReceiver = this.getReceiver(link);

		if(messageReceiver == null) {
			return new BallotVoteResult(false);
		}

		//prepare all messages and save local
		BallotVote[] votes = new BallotVote[allChoices.size()];
		int n = 0;
		for(final BallotChoiceModel choiceModel: allChoices) {
			BallotVote vote = new BallotVote();
			vote.setId(choiceModel.getApiBallotChoiceId());

			//change if other values implement
			if(voting.containsKey(choiceModel.getId())){
				vote.setValue(voting.get(choiceModel.getId()));
			}
			else {
				vote.setValue(0);
			}

			votes[n] = vote;
			n++;
		}

		try {
			//send
			messageReceiver.createAndSendBallotVoteMessage(votes, ballotModel);

			//and save
			this.databaseServiceNew.getBallotVoteModelFactory().deleteByBallotIdAndVotingIdentity(
					ballotModel.getId(),
					this.userService.getIdentity()
			);

			for(BallotChoiceModel choiceModel: allChoices) {
				BallotVoteModel ballotVoteModel = new BallotVoteModel();
				ballotVoteModel.setVotingIdentity(this.userService.getIdentity());
				ballotVoteModel.setBallotId(ballotModel.getId());
				ballotVoteModel.setBallotChoiceId(choiceModel.getId());

				if(voting.containsKey(choiceModel.getId())){
					ballotVoteModel.setChoice(voting.get(choiceModel.getId()));
				}
				else {
					ballotVoteModel.setChoice(0);
				}

				ballotVoteModel.setModifiedAt(new Date());
				ballotVoteModel.setCreatedAt(new Date());
				this.databaseServiceNew.getBallotVoteModelFactory().create(
						ballotVoteModel
				);
			}
		} catch (ThreemaException e) {
			logger.error("create boxed ballot failed", e);
			return new BallotVoteResult(false);
		}

		ListenerManager.ballotVoteListeners.handle(listener -> {
			if(listener.handle(ballotModel)) {
				listener.onSelfVote(
					ballotModel);
			}
		});

		return new BallotVoteResult(true);
	}

	@Override
	public BallotVoteResult vote(final BallotVoteInterface voteMessage) throws NotAllowedException {
		final BallotModel ballotModel = this.get(voteMessage.getBallotId().toString(), voteMessage.getBallotCreator());

		//invalid ballot model
		if(ballotModel == null) {
			return new BallotVoteResult(false);
		}

		if(ballotModel.getType() == BallotModel.Type.RESULT_ON_CLOSE && !TestUtil.compare(
				ballotModel.getCreatorIdentity(),
				this.userService.getIdentity())) {
			logger.error("this is not a intermediate ballot and not mine, ingore the message");
			//return true to ack the message
			return new BallotVoteResult(true);
		}

		//if the ballot is closed, ignore any votes
		if(ballotModel.getState() == BallotModel.State.CLOSED) {
			logger.error("this is a closed ballot, ignore this message");
			return new BallotVoteResult(true);
		}

		final String fromIdentity = ((AbstractMessage)voteMessage).getFromIdentity();

		//load existing votes of user
		List<BallotVoteModel> existingVotes = this.getVotes(ballotModel.getId(), fromIdentity);
		final boolean firstVote = existingVotes == null || existingVotes.size() == 0;

		List<BallotVoteModel> savingVotes = new ArrayList<>();
		List<BallotChoiceModel> choices = this.getChoices(ballotModel.getId());

		for(final BallotVote apiVoteModel: voteMessage.getBallotVotes()) {
			apiVoteModel.getId();

			//check if the choice correct
			final BallotChoiceModel c = Functional.select(choices, new IPredicateNonNull<BallotChoiceModel>() {
				@Override
				public boolean apply(@NonNull BallotChoiceModel type) {
					return type.getApiBallotChoiceId() == apiVoteModel.getId();
				}
			});

			if(c != null) {
				//cool, correct choice
				BallotVoteModel ballotVoteModel = Functional.select(existingVotes, new IPredicateNonNull<BallotVoteModel>() {
					@Override
					public boolean apply(@NonNull BallotVoteModel type) {
						return type.getBallotChoiceId() == c.getId();
					}
				});

				if(ballotVoteModel == null) {
					//ok, a new vote
					ballotVoteModel = new BallotVoteModel();
					ballotVoteModel.setBallotId(ballotModel.getId());
					ballotVoteModel.setBallotChoiceId(c.getId());
					ballotVoteModel.setVotingIdentity(fromIdentity);
					ballotVoteModel.setCreatedAt(new Date());
				}
				else {
					//remove from existing votes
					if(existingVotes != null) {
						existingVotes.remove(ballotVoteModel);
					}
				}

				if(
						//is a new vote...
						ballotVoteModel.getId() <= 0
						//... or a modified
					|| ballotVoteModel.getChoice() != apiVoteModel.getValue()) {

					ballotVoteModel.setChoice(apiVoteModel.getValue());
					ballotVoteModel.setModifiedAt(new Date());
					savingVotes.add(ballotVoteModel);
				}
			}
		}

		//remove votes
		boolean hasModifications = false;

		if(existingVotes != null && existingVotes.size() > 0) {
			int[] ids = new int[existingVotes.size()];
			for(int n = 0; n < ids.length; n++) {
				ids[n] = existingVotes.get(n).getId();
			}

			this.databaseServiceNew.getBallotVoteModelFactory().deleteByIds(
					ids);

			hasModifications = true;
		}

		for(BallotVoteModel ballotVoteModel: savingVotes) {
			this.databaseServiceNew.getBallotVoteModelFactory().createOrUpdate(
					ballotVoteModel
			);
			hasModifications = true;
		}

		if(hasModifications) {

			ListenerManager.ballotVoteListeners.handle(new ListenerManager.HandleListener<BallotVoteListener>() {
				@Override
				public void handle(BallotVoteListener listener) {
					if(listener.handle(ballotModel)) {
						listener.onVoteChanged(
								ballotModel,
								fromIdentity,
								firstVote);
					}
				}
			});
		}
		return new BallotVoteResult(true);
	}



	private GroupModel getGroupModel(LinkBallotModel link) {
		if(link.getType() != LinkBallotModel.Type.GROUP) {
			return null;
		}

		int groupId = ((GroupBallotModel)link).getGroupId();
		return this.groupService.getById(groupId);
	}


	private ContactModel getContactModel(LinkBallotModel link) {
		if(link.getType() != LinkBallotModel.Type.CONTACT) {
			return null;
		}

		String identity = ((IdentityBallotModel)link).getIdentity();
		return this.contactService.getByIdentity(identity);

	}

	@Override
	public MessageReceiver getReceiver(BallotModel ballotModel) {
		try {
			LinkBallotModel link = this.getLinkedBallotModel(ballotModel);
			return this.getReceiver(link);
		} catch (NotAllowedException e) {
			logger.error("Exception", e);
			return null;
		}
	}

	@Override
	public BallotMatrixData getMatrixData(int ballotModelId) {
		try {
			BallotModel ballotModel = this.get(ballotModelId);

			//ok, ballot not found
			if(ballotModel == null) {
				throw new ThreemaException("invalid ballot");
			}

			BallotMatrixService matrixService = new BallotMatrixServiceImpl(ballotModel);

			String[] participants = this.getParticipants(ballotModelId);

			if (participants.length > 0) {
				for (String identity : participants) {
					matrixService.createParticipant(identity);
				}

				for (BallotChoiceModel choice : this.getChoices(ballotModelId)) {
					matrixService.createChoice(choice);
				}

				for (BallotVoteModel ballotVoteModel : this.getBallotVotes(ballotModelId)) {
					matrixService.addVote(ballotVoteModel);
				}

				return matrixService.finish();
			}
		}
		catch (ThreemaException x) {
			logger.error("Exception", x);
		}
		return null;
	}

	private MessageReceiver getReceiver(LinkBallotModel link) {
		if(link != null) {
			switch (link.getType()) {
				case GROUP:
					GroupModel groupModel = this.getGroupModel(link);
					return this.groupService.createReceiver(groupModel);
				case CONTACT:
					ContactModel contactModel = this.getContactModel(link);
					return this.contactService.createReceiver(contactModel);
			}
		}
		return null;
	}

	private int getCalculatedVotingCount(BallotChoiceModel choiceModel) {
		return (int) this.databaseServiceNew.getBallotVoteModelFactory().countByBallotChoiceIdAndChoice(
				choiceModel.getId(),
				1);
	}

	private BallotChoiceModel getChoiceByApiId(BallotModel ballotModel, int choiceId) {
		return this.databaseServiceNew.getBallotChoiceModelFactory().getByBallotIdAndApiChoiceId(
				ballotModel.getId(),
				choiceId
		);
	}

	/**
	 * Link a ballot with a contact
	 *
	 * @return success
	 */
	private boolean link(ContactModel contactModel, BallotModel ballotModel) {
		IdentityBallotModelFactory identityBallotModelFactory = this.databaseServiceNew.getIdentityBallotModelFactory();
		if(identityBallotModelFactory.getByIdentityAndBallotId(
				contactModel.getIdentity(),
				ballotModel.getId()
		) != null) {
			//already linked
			return true;
		}

		IdentityBallotModel m = new IdentityBallotModel();
		m.setBallotId(ballotModel.getId());
		m.setIdentity(contactModel.getIdentity());
		identityBallotModelFactory.create(
				m);

		this.cache(m);

		return true;
	}

	/**
	 * Link a a ballot with a group
	 *
	 * @return success
	 */
	private boolean link(GroupModel groupModel, BallotModel ballotModel) {
		GroupBallotModelFactory groupBallotModelFactory = this.databaseServiceNew.getGroupBallotModelFactory();
		if(groupBallotModelFactory.getByGroupIdAndBallotId(
				groupModel.getId(),
				ballotModel.getId()
		) != null) {
			//already linked
			return true;
		}

		GroupBallotModel m = new GroupBallotModel();
		m.setBallotId(ballotModel.getId());
		m.setGroupId(groupModel.getId());
		groupBallotModelFactory.create(
				m);

		this.cache(m);
		return true;
	}

	private void handleModified(final BallotModel ballotModel) {
		ListenerManager.ballotListeners.handle(new ListenerManager.HandleListener<BallotListener>() {
			@Override
			public void handle(BallotListener listener) {
				if(listener.handle(ballotModel)) {
					listener.onModified(ballotModel);
				}
			}
		});
	}

	private void checkAccess() throws NotAllowedException {
		if(!this.userService.hasIdentity()) {
			throw new NotAllowedException();
		}
	}

	private void cache(List<BallotModel> ballotModels) {
		for(BallotModel m: ballotModels) {
			this.cache(m);
		}
	}

	private void cache(BallotModel ballotModel) {
		if(ballotModel != null) {
			synchronized (this.ballotModelCache) {
				this.ballotModelCache.put(ballotModel.getId(), ballotModel);
			}
		}
	}

	private void cache(LinkBallotModel linkBallotModel) {
		if(linkBallotModel != null) {
			synchronized (this.linkBallotModelCache) {
				this.linkBallotModelCache.put(linkBallotModel.getBallotId(), linkBallotModel);
			}
		}
	}

	private void resetCache(BallotModel ballotModel) {
		if(ballotModel != null) {
			synchronized (this.ballotModelCache) {
				this.ballotModelCache.remove(ballotModel.getId());
			}
		}
	}

	@Nullable
	private BallotModel getFromCache(int id) {
		synchronized (this.ballotModelCache) {
			if(this.ballotModelCache.indexOfKey(id) >= 0) {
				return this.ballotModelCache.get(id);
			}
		}

		return null;
	}

	private LinkBallotModel getLinkModelFromCache(int ballotId) {
		synchronized (this.linkBallotModelCache) {
			if(this.linkBallotModelCache.indexOfKey(ballotId) >= 0) {
				return this.linkBallotModelCache.get(ballotId);
			}
		}
		return null;
	}

	private BallotModel getFromCache(final String apiId, final String creator) {
		synchronized (this.ballotModelCache) {
			return Functional.select(this.ballotModelCache, new IPredicateNonNull<BallotModel>() {
				@Override
				public boolean apply(@NonNull BallotModel type) {
					return TestUtil.compare(type.getApiBallotId(), apiId)
							&& TestUtil.compare(type.getCreatorIdentity(), creator);
				}
			});
		}
	}
}

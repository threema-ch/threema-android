/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.BallotVoteModel;

public class BallotMatrixServiceImpl implements BallotMatrixService {
	private static final Logger logger = LoggerFactory.getLogger(BallotMatrixServiceImpl.class);

	private abstract class AxisElement {
		private final int pos;
		protected boolean[] otherChoose;

		protected AxisElement(int pos) {
			this.pos = pos;
		}

		public int getPos() {
			return this.pos;
		}

		protected boolean hasOtherChoose(int pos) {
			return otherChoose != null
					&& pos >= 0
					&& otherChoose.length > pos
					&& otherChoose[pos];
		}
	}

	public class Participant extends AxisElement implements BallotMatrixService.Participant {
		private final String identity;
		private boolean hasVoted;

		public Participant(int pos, String identity) {
			super(pos);
			this.identity = identity;
		}

		@Override
		public boolean hasVoted() {
			return this.hasVoted;
		}

		@Override
		public String getIdentity() {
			return this.identity;
		}

	}

	public class Choice extends AxisElement implements BallotMatrixService.Choice
	{
		private final BallotChoiceModel choiceModel;
		private int voteCount = 0;
		private boolean isWinner = false;

		public Choice(int pos, BallotChoiceModel choiceModel) {
			super(pos);
			this.choiceModel = choiceModel;
		}

		@Override
		public BallotChoiceModel getBallotChoiceModel() {
			return this.choiceModel;
		}

		@Override
		public boolean isWinner() {
			return this.isWinner;
		}

		@Override
		public int getVoteCount() {
			return this.voteCount;
		}
	}

	private boolean finished = false;

	private final BallotModel ballotModel;
	private final List<Participant> participants = new ArrayList<>();
	private final List<Choice> choices = new ArrayList<>();
	private final Map<String, BallotVoteModel> data = new HashMap<>();
	private final DataKeyBuilder dataKeyBuilder = new DataKeyBuilder() {
		@Override
		public String build(BallotMatrixService.Participant p, BallotMatrixService.Choice c) {
			return p.getPos()+"_"+c.getPos();
		}
	};

	public BallotMatrixServiceImpl(BallotModel ballotModel) {
		this.ballotModel = ballotModel;
	}

	@Override
	public Participant createParticipant(String identity) {
		if(this.finished) {
			return null;
		}

		synchronized (this.participants) {
			int pos = participants.size();
			Participant p = new Participant(pos, identity);
			this.participants.add(p);
			return p;
		}
	}

	@Override
	public Choice createChoice(BallotChoiceModel choiceModel) {
		if(this.finished) {
			return null;
		}

		synchronized (this.choices) {
			int pos = choices.size();
			Choice c = new Choice(pos, choiceModel);
			this.choices.add(c);
			return c;
		}
	}


	@Override
	public BallotMatrixServiceImpl addVote(BallotVoteModel ballotVoteModel) throws ThreemaException {
		if(this.finished) {
			return this;
		}

		String voter = ballotVoteModel.getVotingIdentity();
		int choiceModelId = ballotVoteModel.getBallotChoiceId();

		BallotMatrixService.Participant participant = null;
		BallotMatrixService.Choice choice = null;

		//get position in axis
		for(int x = 0; x < this.participants.size(); x++) {
			if(TestUtil.compare(voter, this.participants.get(x).getIdentity())) {
				participant = this.participants.get(x);
				break;
			}
		}
		for(int y = 0; y < this.choices.size(); y++) {
			if(choiceModelId == this.choices.get(y).getBallotChoiceModel().getId()) {
				choice = this.choices.get(y);
				break;
			}
		}

		if(participant == null) {
			//participant do not exist
			//possible reason: the user left the group
			//do not crash at this time
			logger.error("a participant was not recognized");
			return this;
		}


		if(choice == null) {
			logger.error("choice " + ballotVoteModel.getBallotChoiceId() + " not found, ignore result");
			return this;
		}

		synchronized (this.data) {
			this.data.put(this.dataKeyBuilder.build(participant, choice), ballotVoteModel);
		}

		return this;
	}

	private BallotVoteModel getVote(final Participant participant , final Choice choice) {
		synchronized (this.data) {
			String key = this.dataKeyBuilder.build(participant, choice);
			if(key != null) {
				return this.data.get(key);
			}
		}
		return null;
	}

	@Override
	public BallotMatrixData finish() {
		for(int x = 0; x < this.participants.size(); x++) {
			//get all votes by participants
			boolean[] choices = new boolean[this.choices.size()];
			boolean hasVoted = false;

			Participant p = this.participants.get(x);
			for(int y = 0; y < choices.length; y++) {
				BallotVoteModel v = this.getVote(p, this.choices.get(y));
				hasVoted = hasVoted || v != null;
				choices[y] = v != null
						&& v.getChoice() > 0;
			}
			p.otherChoose = choices;
			p.hasVoted = hasVoted;
		}

		for(int y = 0; y < this.choices.size(); y++) {
			//get all votes by participants
			boolean[] participant = new boolean[this.participants.size()];
			Choice c = this.choices.get(y);
			for(int x = 0; x < participant.length; x++) {
				BallotVoteModel v = this.getVote(this.participants.get(x), c);
				participant[x] = v != null
						&& v.getChoice() > 0;
			}
			c.otherChoose = participant;
		}

		int maxPoints = 0;
		for(Choice c: this.choices) {
			int point = 0;
			for(Participant p: this.participants) {
				point += p.hasOtherChoose(c.getPos()) ? 1 : 0;
			}
			c.voteCount = point;
			maxPoints = Math.max(point, maxPoints);
		}

		for(Choice c: this.choices) {
			//only a choice with more than 0 points can win
			c.isWinner = maxPoints > 0 && c.getVoteCount() == maxPoints;
		}
		return new BallotMatrixDataImpl(this.ballotModel,
				(List<BallotMatrixService.Participant>)(List<?>) this.participants,
				(List<BallotMatrixService.Choice>)(List<?>) this.choices,
				this.data,
				this.dataKeyBuilder);
	}

}

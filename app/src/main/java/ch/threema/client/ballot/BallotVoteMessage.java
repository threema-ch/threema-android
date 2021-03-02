/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client.ballot;

import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ch.threema.client.AbstractMessage;
import ch.threema.client.BadMessageException;
import ch.threema.client.ProtocolDefines;

/**
 * A group creation message.
 */
public class BallotVoteMessage extends AbstractMessage
	implements BallotVoteInterface {

	private static final Logger logger = LoggerFactory.getLogger(BallotVoteMessage.class);

	private BallotId ballotId;
	private String ballotCreatorId;

	private final List<BallotVote> ballotVotes = new ArrayList<>();

	@Override
	public void setBallotId(BallotId ballotId) {
		this.ballotId = ballotId;
	}

	@Override
	public void setBallotCreator(String ballotCreator) {
		this.ballotCreatorId = ballotCreator;
	}

	@Override
	public BallotId getBallotId() {
		return this.ballotId;
	}

	@Override
	public String getBallotCreator() {
		return this.ballotCreatorId;
	}

	@Override
	public List<BallotVote> getBallotVotes() {
		return this.ballotVotes;
	}

	public void parseVotes(String votes) throws BadMessageException{
		try {
			JSONArray array = new JSONArray(votes);
			for(int n = 0; n < array.length(); n++) {
				this.ballotVotes.add(BallotVote.parse(array.getJSONArray(n)));
			}
		} catch (JSONException e) {
			throw new BadMessageException("TM035");
		}
	}

	@Override
	public byte[] getBody() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			//ballot stuff
			bos.write(this.getBallotCreator().getBytes(StandardCharsets.US_ASCII));
			bos.write(this.getBallotId().getBallotId());

			JSONArray jsonArray = new JSONArray();
			for(BallotVote c: this.ballotVotes) {
				jsonArray.put(c.getJsonArray());
			}
			bos.write(jsonArray.toString().getBytes(StandardCharsets.US_ASCII));
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_BALLOT_VOTE;
	}
}

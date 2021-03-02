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

import ch.threema.client.BadMessageException;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class BallotVote {

	private final static int POS_CHOICE_ID = 0;
	private final static int POS_CHOICE_VALUE = 1;

	private int id;
	private int value;


	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}


	public static BallotVote parse(JSONArray o) throws BadMessageException {
		try {
			if(o == null) {
				throw new BadMessageException("TM036");
			}

			BallotVote ballotVote = new BallotVote();
			ballotVote.id = o.getInt(POS_CHOICE_ID);
			ballotVote.value = o.getInt(POS_CHOICE_VALUE);
			return ballotVote;
		}
		catch (JSONException e) {
			throw new BadMessageException("TM033");
		}
	}

	public JSONArray getJsonArray() throws BadMessageException {
		JSONArray o = new JSONArray();
		try {
			o.put(POS_CHOICE_ID, this.id);
			o.put(POS_CHOICE_VALUE, this.value);
		}
		catch (Exception e) {
			throw new BadMessageException("TM036");
		}
		return o;
	}

	public void write(ByteArrayOutputStream bos) throws Exception {
		bos.write(this.generateString().getBytes(StandardCharsets.US_ASCII));
	}

	public String generateString() throws BadMessageException {
		return this.getJsonArray().toString();
	}
}

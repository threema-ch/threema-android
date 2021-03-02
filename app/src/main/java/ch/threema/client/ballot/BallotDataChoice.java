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
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class BallotDataChoice {
	private final static String KEY_CHOICES_ID = "i";
	private final static String KEY_CHOICES_NAME = "n";
	private final static String KEY_CHOICES_ORDER = "o";
	private final static String KEY_RESULT = "r";

	private int id;
	private String name;
	private int order;
	private final int[] ballotDataChoiceResults;

	public BallotDataChoice(int resultSize) {
		this.ballotDataChoiceResults = new int[resultSize];
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}


	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}


	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public BallotDataChoice addResult(int pos, int value) {
		if (pos >= 0 && pos < this.ballotDataChoiceResults.length) {
			this.ballotDataChoiceResults[pos] = value;
		}

		return this;
	}

	public Integer getResult(int pos) {
		if (pos >= 0 && pos < this.ballotDataChoiceResults.length) {
			return this.ballotDataChoiceResults[pos];
		}
		return null;
	}

	public static BallotDataChoice parse(String jsonObjectString) throws BadMessageException {
		try {
			JSONObject o = new JSONObject(jsonObjectString);
			return parse(o);
		}
		catch (JSONException e) {
			throw new BadMessageException("TM033 invalid JSON (" + e.getMessage() + ")");
		}
	}

	public static BallotDataChoice parse(JSONObject o) throws BadMessageException {
		try {
			if(o == null) {
				throw new BadMessageException("TM033");
			}

			final JSONArray resultArray;
			if(o.has(KEY_RESULT)) {
				resultArray = o.getJSONArray(KEY_RESULT);
			}
			else {
				resultArray = null;
			}

			BallotDataChoice ballotDataChoice = new BallotDataChoice(resultArray != null ? resultArray.length() : 0);
			ballotDataChoice.setId(o.getInt(KEY_CHOICES_ID));
			ballotDataChoice.setName(o.getString(KEY_CHOICES_NAME));
			ballotDataChoice.setOrder(o.getInt(KEY_CHOICES_ORDER));

			if(resultArray != null) {
				for(int n = 0; n < resultArray.length(); n++) {
					ballotDataChoice.addResult(n, resultArray.getInt(n));
				}
			}

			return ballotDataChoice;
		}
		catch (JSONException e) {
			throw new BadMessageException("TM033");
		}
	}

	public JSONObject getJsonObject() throws BadMessageException {
		JSONObject o = new JSONObject();
		try {
			o.put(KEY_CHOICES_ID, this.getId());
			o.put(KEY_CHOICES_NAME, this.getName());
			o.put(KEY_CHOICES_ORDER, this.getOrder());

			JSONArray resultArray = new JSONArray();
			for(Integer r: this.ballotDataChoiceResults) {
				resultArray.put(r);
			}
			o.put(KEY_RESULT, resultArray);
		}
		catch (Exception e) {
			throw new BadMessageException("TM033");
		}
		return o;
	}
	public void write(ByteArrayOutputStream bos) throws Exception {
		bos.write(this.generateString().getBytes(StandardCharsets.US_ASCII));
	}


	public String generateString() throws BadMessageException {
		return this.getJsonObject().toString();
	}
}

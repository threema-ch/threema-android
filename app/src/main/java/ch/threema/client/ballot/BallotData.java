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
import java.util.ArrayList;
import java.util.List;

public class BallotData {
	private final static String KEY_DESCRIPTION = "d";
	private final static String KEY_STATE = "s";
	private final static String KEY_ASSESSMENT_TYPE = "a";
	private final static String KEY_TYPE = "t";
	private final static String KEY_CHOICE_TYPE = "o";
	private final static String KEY_CHOICES = "c";
	private final static String KEY_PARTICIPANTS = "p";

	public enum State {
		OPEN(0), CLOSED(1);

		private final int value;

		State(int value) {
			this.value = value;
		}

		static State fromId(int id) {
			for (State f : values()) {
				if (f.value == id) return f;
			}
			throw new IllegalArgumentException();
		}

	}

	public enum AssessmentType {
		SINGLE(0), MULTIPLE(1);

		private final int value;

		AssessmentType(int value) {
			this.value = value;
		}

		static AssessmentType fromId(int id) {
			for (AssessmentType f : values()) {
				if (f.value == id) return f;
			}
			throw new IllegalArgumentException();
		}
	}

	public enum Type {
		RESULT_ON_CLOSE(0), INTERMEDIATE(1);

		private final int value;

		Type(int value) {
			this.value = value;
		}

		static Type fromId(int id) {
			for (Type f : values()) {
				if (f.value == id) return f;
			}
			throw new IllegalArgumentException();
		}
	}

	public enum ChoiceType {
		TEXT(0);

		private final int value;

		ChoiceType(int value) {
			this.value = value;
		}

		static ChoiceType fromId(int id) {
			for (ChoiceType f : values()) {
				if (f.value == id) return f;
			}
			throw new IllegalArgumentException();
		}
	}

	private String description;
	private State state;
	private AssessmentType assessmentType;
	private Type type;
	//default choice type text
	private ChoiceType choiceType = ChoiceType.TEXT;

	private final List<BallotDataChoice> choiceList = new ArrayList<>();
	private final List<String> participants = new ArrayList<>();

	public BallotData setDescription(String description) {
		this.description = description;
		return this;
	}

	public String getDescription() {
		return this.description;
	}

	public List<BallotDataChoice> getChoiceList() {
		return this.choiceList;
	}
	public BallotData setState(State state) {
		this.state = state;
		return this;
	}

	public State getState() {
		return this.state;
	}

	public BallotData setAssessmentType(AssessmentType assessmentType) {
		this.assessmentType = assessmentType;
		return this;
	}

	public AssessmentType getAssessmentType() {
		return this.assessmentType;
	}

	public BallotData setType(Type type) {
		this.type = type;
		return this;
	}

	public Type getType() {
		return this.type;
	}

	public ChoiceType getChoiceType() {
		return this.choiceType;
	}

	public BallotData setChoiceType(ChoiceType choiceType) {
		this.choiceType = choiceType;
		return this;
	}

	/**
	 *
	 * @param identity
	 * @return
	 */
	public int addParticipant(String identity) {
		for(int pos = 0; pos < this.participants.size(); pos++) {
			if(identity.equals(this.participants.get(pos))) {
				return pos;
			}
		}

		this.participants.add(identity);
		return this.participants.size()-1;
	}

	public List<String> getParticipants() {
		return this.participants;
	}

	public static BallotData parse(String jsonObjectString) throws BadMessageException {
		try {
			JSONObject o = new JSONObject(jsonObjectString);

			BallotData ballotData = new BallotData();
			ballotData.description = o.getString(KEY_DESCRIPTION);
			try {
				ballotData.state = State.fromId(o.getInt(KEY_STATE));
			}
			catch (IllegalArgumentException e) {
				throw new BadMessageException("TM030");
			}

			try {
				ballotData.assessmentType = AssessmentType.fromId(o.getInt(KEY_ASSESSMENT_TYPE));
			}
			catch (IllegalArgumentException e) {
				throw new BadMessageException("TM031");
			}

			try {
				ballotData.type = Type.fromId(o.getInt(KEY_TYPE));
			}
			catch (IllegalArgumentException e) {
				throw new BadMessageException("TM032");
			}
			try {
				ballotData.choiceType = ChoiceType.fromId(o.getInt(KEY_CHOICE_TYPE));
			}
			catch (IllegalArgumentException e) {
				throw new BadMessageException("TM034");
			}

			JSONArray choices = o.getJSONArray(KEY_CHOICES);
			for(int n = 0; n < choices.length(); n++) {
				ballotData.getChoiceList().add(BallotDataChoice.parse(choices.getJSONObject(n)));
			}

			if(o.has(KEY_PARTICIPANTS)) {
				JSONArray participants = o.getJSONArray(KEY_PARTICIPANTS);
				for(int n = 0; n < participants.length(); n++) {
					ballotData.participants.add(participants.getString(n));
				}
			}

			return ballotData;
		}
		catch (JSONException e) {
			throw new BadMessageException("TM029");
		}

	}

	public void write(ByteArrayOutputStream bos) throws Exception {
		bos.write(this.generateString().getBytes(StandardCharsets.UTF_8));
	}

	public String generateString() throws BadMessageException {
		JSONObject o = new JSONObject();
		try {
			o.put(KEY_DESCRIPTION, this.description);
			o.put(KEY_STATE, this.state.value);
			o.put(KEY_ASSESSMENT_TYPE, this.assessmentType.value);
			o.put(KEY_TYPE, this.type.value);
			o.put(KEY_CHOICE_TYPE, this.choiceType.value);

			JSONArray a = new JSONArray();
			for (BallotDataChoice c : this.getChoiceList()) {
				a.put(c.getJsonObject());
			}
			o.put(KEY_CHOICES, a);

			JSONArray p = new JSONArray();
			for(String i: this.participants) {
				p.put(i);
			}
			o.put(KEY_PARTICIPANTS, p);
		}
		catch (Exception e) {
			throw new BadMessageException("TM033");
		}

		return o.toString();
	}
}

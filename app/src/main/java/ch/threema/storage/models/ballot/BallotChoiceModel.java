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

package ch.threema.storage.models.ballot;


import java.util.Date;

public class BallotChoiceModel {
	public static final String TABLE = "ballot_choice";
	public static final String COLUMN_ID = "id";
	public static final String COLUMN_BALLOT_ID = "ballotId";
	public static final String COLUMN_API_CHOICE_ID = "apiBallotChoiceId";
	public static final String COLUMN_TYPE = "type";
	public static final String COLUMN_NAME = "name";
	public static final String COLUMN_VOTE_COUNT = "voteCount";
	public static final String COLUMN_ORDER = "order";
	public static final String COLUMN_CREATED_AT= "createdAt";
	public static final String COLUMN_MODIFIED_AT= "modifiedAt";


	public enum Type {
		Text
	};

	private int id;
	private int ballotId;
	private int apiBallotChoiceId;
	private Type type;
	private String name;
	private int voteCount;
	private int order;
	private Date createdAt;
	private Date modifiedAt;


	public int getId() {
		return id;
	}
	public BallotChoiceModel setId(int id) {
		this.id = id;
		return this;
	}

	public int getBallotId() {
		return ballotId;
	}

	public BallotChoiceModel setBallotId(int ballotId) {
		this.ballotId = ballotId;
		return this;
	}

	public int getApiBallotChoiceId() {
		return apiBallotChoiceId;
	}

	public BallotChoiceModel setApiBallotChoiceId(int apiBallotChoiceId) {
		this.apiBallotChoiceId = apiBallotChoiceId;
		return this;
	}

	public Type getType() {
		return type;
	}

	public BallotChoiceModel setType(Type type) {
		this.type = type;
		return this;
	}

	public String getName() {
		return name;
	}

	public BallotChoiceModel setName(String name) {
		this.name = name;
		return this;
	}

	public int getVoteCount() {
		return voteCount;
	}

	public BallotChoiceModel setVoteCount(int voteCount) {
		this.voteCount = voteCount;
		return this;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public BallotChoiceModel setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	public Date getModifiedAt() {
		return modifiedAt;
	}

	public BallotChoiceModel setModifiedAt(Date modifiedAt) {
		this.modifiedAt = modifiedAt;
		return this;
	}

	public int getOrder() {
		return order;
	}

	public BallotChoiceModel setOrder(int order) {
		this.order = order;
		return this;
	}

}

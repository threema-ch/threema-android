/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

public class GroupBallotModel implements LinkBallotModel {
	public static final String TABLE = "group_ballot";
	public static final String COLUMN_ID = "id";
	public static final String COLUMN_GROUP_ID = "groupId";
	public static final String COLUMN_BALLOT_ID = "ballotId";

	private int id;
	private int groupId;
	private int ballotId;

	public int getId() {
		return this.id;
	}

	public GroupBallotModel setId(int id) {
		this.id = id;
		return this;
	}

	public int getGroupId() {
		return groupId;
	}

	public GroupBallotModel setGroupId(int groupId) {
		this.groupId = groupId;
		return this;
	}

	public int getBallotId() {
		return ballotId;
	}

	@Override
	public Type getType() {
		return Type.GROUP;
	}

	public GroupBallotModel setBallotId(int ballotId) {
		this.ballotId = ballotId;
		return this;
	}
}

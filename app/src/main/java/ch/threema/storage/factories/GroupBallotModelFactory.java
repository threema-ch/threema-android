/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;

import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ballot.GroupBallotModel;

public class GroupBallotModelFactory extends ModelFactory {

	public GroupBallotModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, GroupBallotModel.TABLE);
	}

	public GroupBallotModel getByGroupIdAndBallotId(int groupId, int ballotId) {
		return getFirst(
				GroupBallotModel.COLUMN_GROUP_ID + "=? "
						+ "AND " + GroupBallotModel.COLUMN_BALLOT_ID + "=?",
				new String[]{
						String.valueOf(groupId),
						String.valueOf(ballotId)
				});
	}


	public GroupBallotModel getByBallotId(int ballotModelId) {
		return getFirst(
				GroupBallotModel.COLUMN_BALLOT_ID + "=?",
				new String[]{
						String.valueOf(ballotModelId)
				});
	}

	private GroupBallotModel convert(Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final GroupBallotModel c = new GroupBallotModel();

			//convert default
			new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
				@Override
				public boolean next(CursorHelper cursorHelper) {
					c
							.setId(cursorHelper.getInt(GroupBallotModel.COLUMN_ID))
							.setBallotId(cursorHelper.getInt(GroupBallotModel.COLUMN_BALLOT_ID))
							.setGroupId(cursorHelper.getInt(GroupBallotModel.COLUMN_GROUP_ID));
					return false;
				}
			});

			return c;
		}

		return null;
	}

	private ContentValues buildContentValues(GroupBallotModel groupBallotModel) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(GroupBallotModel.COLUMN_GROUP_ID, groupBallotModel.getGroupId());
		contentValues.put(GroupBallotModel.COLUMN_BALLOT_ID, groupBallotModel.getBallotId());

		return contentValues;
	}

	public boolean create(GroupBallotModel groupBallotModel) {
		ContentValues contentValues = buildContentValues(groupBallotModel);
		long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
		if (newId > 0) {
			groupBallotModel.setId((int) newId);
			return true;
		}
		return false;
	}

	public int deleteByBallotId(int ballotId) {
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				GroupBallotModel.COLUMN_BALLOT_ID + "=?",
				new String[]{
						String.valueOf(ballotId)
				});
	}

	private GroupBallotModel getFirst(String selection, String[] selectionArgs) {
		Cursor cursor = this.databaseService.getReadableDatabase().query (
				this.getTableName(),
				null,
				selection,
				selectionArgs,
				null,
				null,
				null
		);

		if(cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					return convert(cursor);
				}
			}
			finally {
				cursor.close();
			}
		}

		return null;
	}

	@Override
	public String[] getStatements() {
		return new String[] {
				"CREATE TABLE `group_ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `groupId` INTEGER NOT NULL , `ballotId` INTEGER NOT NULL )",
				"CREATE UNIQUE INDEX `groupBallotId` ON `group_ballot` ( `groupId`, `ballotId` )"
		};
	}
}

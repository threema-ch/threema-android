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
import ch.threema.storage.models.GroupRequestSyncLogModel;

public class GroupRequestSyncLogModelFactory extends ModelFactory {
	public GroupRequestSyncLogModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, GroupRequestSyncLogModel.TABLE);
	}

	public GroupRequestSyncLogModel get(String apiGroupId, String groupCreator) {
		return getFirst(
				GroupRequestSyncLogModel.COLUMN_API_GROUP_ID + "=?"
				+ " AND " + GroupRequestSyncLogModel.COLUMN_CREATOR_IDENTITY + "=?",
				new String[]{
						apiGroupId,
						groupCreator
				});
	}
	public boolean createOrUpdate(GroupRequestSyncLogModel groupRequestSyncLogModel) {
		boolean insert = true;
		if(groupRequestSyncLogModel.getId() > 0) {
			Cursor cursor = this.databaseService.getReadableDatabase().query(
					this.getTableName(),
					null,
					GroupRequestSyncLogModel.COLUMN_ID + "=?",
					new String[]{
							String.valueOf(groupRequestSyncLogModel.getId())
					},
					null,
					null,
					null
			);

			if (cursor != null) {
				try {
					insert = !cursor.moveToNext();
				} finally {
					cursor.close();
				}
			}
		}

		if(insert) {
			return create(groupRequestSyncLogModel);
		}
		else {
			return update(groupRequestSyncLogModel);
		}
	}

	public boolean create(GroupRequestSyncLogModel groupRequestSyncLogModel) {
		ContentValues contentValues = buildValues(groupRequestSyncLogModel);
		long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
		if (newId > 0) {
			groupRequestSyncLogModel.setId((int) newId);
			return true;
		}
		return false;
	}

	public boolean update(GroupRequestSyncLogModel groupRequestSyncLogModel) {
		ContentValues contentValues = buildValues(groupRequestSyncLogModel);
		this.databaseService.getWritableDatabase().update(this.getTableName(),
				contentValues,
				GroupRequestSyncLogModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(groupRequestSyncLogModel.getId())
				});
		return true;
	}

	private ContentValues buildValues(GroupRequestSyncLogModel groupRequestSyncLogModel) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(GroupRequestSyncLogModel.COLUMN_API_GROUP_ID, groupRequestSyncLogModel.getApiGroupId());
		contentValues.put(GroupRequestSyncLogModel.COLUMN_CREATOR_IDENTITY, groupRequestSyncLogModel.getCreatorIdentity());
		contentValues.put(GroupRequestSyncLogModel.COLUMN_LAST_REQUEST, groupRequestSyncLogModel.getLastRequest() != null
				? CursorHelper.dateAsStringFormat.get().format(groupRequestSyncLogModel.getLastRequest()) :
				null);
		return contentValues;
	}

	private GroupRequestSyncLogModel getFirst(String selection, String[] selectionArgs) {
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

	private GroupRequestSyncLogModel convert(Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final GroupRequestSyncLogModel c = new GroupRequestSyncLogModel();

			//convert default
			new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
				@Override
				public boolean next(CursorHelper cursorHelper) {
					c
							.setId(cursorHelper.getInt(GroupRequestSyncLogModel.COLUMN_ID))
							.setAPIGroupId(
									cursorHelper.getString(GroupRequestSyncLogModel.COLUMN_API_GROUP_ID),
									cursorHelper.getString(GroupRequestSyncLogModel.COLUMN_CREATOR_IDENTITY))
							.setLastRequest(cursorHelper.getDateByString(GroupRequestSyncLogModel.COLUMN_LAST_REQUEST))
					//count not in database, TODO add field to db!
							//.setCount(cursorFactory.getBoolean(GroupModel.COLUMN_DELETED))
					;

					return false;
				}
			});

			return c;
		}

		return null;
	}

	@Override
	public String[] getStatements() {
		return new String[] {
				"CREATE TABLE `m_group_request_sync_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `apiGroupId` VARCHAR , `creatorIdentity` VARCHAR , `lastRequest` VARCHAR )",
				"CREATE UNIQUE INDEX `apiGroupIdAndCreatorGroupRequestSyncLogModel` ON `m_group_request_sync_log` ( `apiGroupId`, `creatorIdentity` );"
		};
	}
}

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
import ch.threema.storage.models.OutgoingGroupSyncRequestLogModel;

public class OutgoingGroupSyncRequestLogModelFactory extends ModelFactory {
	public OutgoingGroupSyncRequestLogModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, OutgoingGroupSyncRequestLogModel.TABLE);
	}

	public OutgoingGroupSyncRequestLogModel get(String apiGroupId, String groupCreator) {
		return getFirst(
				OutgoingGroupSyncRequestLogModel.COLUMN_API_GROUP_ID + "=?"
				+ " AND " + OutgoingGroupSyncRequestLogModel.COLUMN_CREATOR_IDENTITY + "=?",
				new String[]{
						apiGroupId,
						groupCreator
				});
	}
	public boolean createOrUpdate(OutgoingGroupSyncRequestLogModel outgoingGroupSyncRequestLogModel) {
		boolean insert = true;
		if(outgoingGroupSyncRequestLogModel.getId() > 0) {
			Cursor cursor = this.databaseService.getReadableDatabase().query(
					this.getTableName(),
					null,
					OutgoingGroupSyncRequestLogModel.COLUMN_ID + "=?",
					new String[]{
							String.valueOf(outgoingGroupSyncRequestLogModel.getId())
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
			return create(outgoingGroupSyncRequestLogModel);
		}
		else {
			return update(outgoingGroupSyncRequestLogModel);
		}
	}

	public boolean create(OutgoingGroupSyncRequestLogModel outgoingGroupSyncRequestLogModel) {
		ContentValues contentValues = buildValues(outgoingGroupSyncRequestLogModel);
		long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
		if (newId > 0) {
			outgoingGroupSyncRequestLogModel.setId((int) newId);
			return true;
		}
		return false;
	}

	public boolean update(OutgoingGroupSyncRequestLogModel outgoingGroupSyncRequestLogModel) {
		ContentValues contentValues = buildValues(outgoingGroupSyncRequestLogModel);
		this.databaseService.getWritableDatabase().update(this.getTableName(),
				contentValues,
				OutgoingGroupSyncRequestLogModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(outgoingGroupSyncRequestLogModel.getId())
				});
		return true;
	}

	private ContentValues buildValues(OutgoingGroupSyncRequestLogModel outgoingGroupSyncRequestLogModel) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_API_GROUP_ID, outgoingGroupSyncRequestLogModel.getApiGroupId());
		contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_CREATOR_IDENTITY, outgoingGroupSyncRequestLogModel.getCreatorIdentity());
		contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_LAST_REQUEST, outgoingGroupSyncRequestLogModel.getLastRequest() != null
				? CursorHelper.dateAsStringFormat.get().format(outgoingGroupSyncRequestLogModel.getLastRequest()) :
				null);
		return contentValues;
	}

	private OutgoingGroupSyncRequestLogModel getFirst(String selection, String[] selectionArgs) {
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

	private OutgoingGroupSyncRequestLogModel convert(Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final OutgoingGroupSyncRequestLogModel c = new OutgoingGroupSyncRequestLogModel();

			//convert default
			new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
				@Override
				public boolean next(CursorHelper cursorHelper) {
					c
							.setId(cursorHelper.getInt(OutgoingGroupSyncRequestLogModel.COLUMN_ID))
							.setAPIGroupId(
									cursorHelper.getString(OutgoingGroupSyncRequestLogModel.COLUMN_API_GROUP_ID),
									cursorHelper.getString(OutgoingGroupSyncRequestLogModel.COLUMN_CREATOR_IDENTITY))
							.setLastRequest(cursorHelper.getDateByString(OutgoingGroupSyncRequestLogModel.COLUMN_LAST_REQUEST))
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

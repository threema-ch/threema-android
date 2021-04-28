/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

import net.sqlcipher.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.models.GroupMemberModel;

public class GroupMemberModelFactory extends ModelFactory {

	public GroupMemberModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, GroupMemberModel.TABLE);
	}

	public GroupMemberModel getByGroupIdAndIdentity(int groupId, String identity) {
		return getFirst(
				GroupMemberModel.COLUMN_GROUP_ID + "=? "
						+ " AND " + GroupMemberModel.COLUMN_IDENTITY + "=?",
				new String[]{
						String.valueOf(groupId),
						identity
				});
	}

	public List<GroupMemberModel> getByGroupId(int groupId) {
		return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
				null,
				GroupMemberModel.COLUMN_GROUP_ID + "=?",
				new String[]{
						String.valueOf(groupId)
				},
				null,
				null,
				null));
	}

	public long countMembers(int groupId) {
		return DatabaseUtil.count(this.databaseService.getReadableDatabase().rawQuery(
			"SELECT COUNT(*) FROM " + this.getTableName()
				+ " WHERE " + GroupMemberModel.COLUMN_GROUP_ID + "=?",
			new String[]{
				String.valueOf(groupId)
			}
		));
	}

	private GroupMemberModel convert(Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final GroupMemberModel groupMemberModel = new GroupMemberModel();
			new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
				@Override
				public boolean next(CursorHelper cursorHelper) {
					groupMemberModel
							.setId(cursorHelper.getInt(GroupMemberModel.COLUMN_ID))
							.setGroupId(cursorHelper.getInt(GroupMemberModel.COLUMN_GROUP_ID))
							.setIdentity(cursorHelper.getString(GroupMemberModel.COLUMN_IDENTITY))
							.setActive(cursorHelper.getBoolean(GroupMemberModel.COLUMN_IS_ACTIVE));
					return false;
				}
			});

			return groupMemberModel;
		}

		return null;
	}

	public List<GroupMemberModel> convertList(Cursor c) {
		List<GroupMemberModel> result = new ArrayList<>();
		if(c != null) {
			try {
				while (c.moveToNext()) {
					result.add(convert(c));
				}
			}
			finally {
				c.close();
			}
		}
		return result;
	}

	public boolean createOrUpdate(GroupMemberModel groupMemberModel) {
		boolean insert = true;
		if(groupMemberModel.getId() > 0) {
			Cursor cursor = this.databaseService.getReadableDatabase().query(
					this.getTableName(),
					null,
					GroupMemberModel.COLUMN_ID + "=?",
					new String[]{
							String.valueOf(groupMemberModel.getId())
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
			return create(groupMemberModel);
		}
		else {
			return update(groupMemberModel);
		}
	}

	private ContentValues buildContentValues(GroupMemberModel groupMemberModel) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(GroupMemberModel.COLUMN_GROUP_ID, groupMemberModel.getGroupId());
		contentValues.put(GroupMemberModel.COLUMN_IDENTITY, groupMemberModel.getIdentity());
		contentValues.put(GroupMemberModel.COLUMN_IS_ACTIVE, groupMemberModel.isActive());
		return contentValues;
	}

	public boolean create(GroupMemberModel groupMemberModel) {
		ContentValues contentValues = buildContentValues(groupMemberModel);
		long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
		if (newId > 0) {
			groupMemberModel.setId((int) newId);
			return true;
		}
		return false;
	}

	public boolean update(GroupMemberModel groupMemberModel) {
		ContentValues contentValues = buildContentValues(groupMemberModel);
		this.databaseService.getWritableDatabase().update(this.getTableName(),
				contentValues,
				GroupMemberModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(groupMemberModel.getId())
				});
		return true;
	}

	private GroupMemberModel getFirst(String selection, String[] selectionArgs) {
		Cursor cursor = this.databaseService.getReadableDatabase().query(
				this.getTableName(),
				null,
				selection,
				selectionArgs,
				null,
				null,
				null
		);

		if(cursor != null ) {
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

	public Map<String, Integer> getColors(int groupId) {
		//TODO use const instead fix table names!
		Cursor c = this.databaseService.getReadableDatabase().rawQuery("SELECT c.identity, c.color " +
				"FROM group_member gm " +
				"INNER JOIN contacts c " +
				"	ON c.identity = gm.identity " +
				"WHERE gm.groupId = ? AND LENGTH(c.identity) > 0 AND LENGTH(c.color) > 0", new String[]{
				String.valueOf(groupId)
		});
		Map<String, Integer>  colors = new HashMap<>();
		if(c != null) {
			try {
				while (c.moveToNext()) {
					colors.put(c.getString(0), c.getInt(1));
				}
			}
			finally {
				c.close();
			}
		}

		return colors;
	}

	public List<Integer> getGroupIdsByIdentity(String identity) {
		Cursor c = this.databaseService.getReadableDatabase().query(
				this.getTableName(),
				new String[]{
						GroupMemberModel.COLUMN_GROUP_ID
				},
				GroupMemberModel.COLUMN_IDENTITY + "=?",
				new String[]{
						identity
				},
				null, null, null);
		List<Integer> result = new ArrayList<>();
		if(c != null) {
			try {
				while(c.moveToNext()) {
					result.add(c.getInt(0));
				}
			} finally {
				c.close();
			}
		}

		return result;
	}

	public int deleteByGroupId(int groupId) {
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				GroupMemberModel.COLUMN_GROUP_ID + "=?",
				new String[] {
						String.valueOf(groupId)
				});
	}

	public int delete(List<GroupMemberModel> modelsToRemove) {
		String[] args = new String[modelsToRemove.size()];
		for(int n = 0; n < modelsToRemove.size(); n++) {
			args[n] = String.valueOf(modelsToRemove.get(n).getId());
		}
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				GroupMemberModel.COLUMN_ID + " IN (" + DatabaseUtil.makePlaceholders(args.length) + ")",
				args);
	}

	public int deleteByGroupIdAndIdentity(int groupId, String identity) {
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				GroupMemberModel.COLUMN_GROUP_ID + "=?"
				+ " AND " + GroupMemberModel.COLUMN_IDENTITY + "=?",
				new String[] {
						String.valueOf(groupId),
						identity
				});
	}

	@Override
	public String[] getStatements() {
		return new String[]{
				"CREATE TABLE `group_member` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `identity` VARCHAR , `groupId` INTEGER , `isActive` SMALLINT NOT NULL )"
		};
	}
}

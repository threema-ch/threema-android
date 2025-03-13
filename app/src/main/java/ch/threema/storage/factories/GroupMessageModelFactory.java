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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.JsonUtil;
import ch.threema.domain.models.MessageId;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;

import static ch.threema.storage.models.data.DisplayTag.DISPLAY_TAG_STARRED;

public class GroupMessageModelFactory extends AbstractMessageModelFactory {
	public GroupMessageModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, GroupMessageModel.TABLE);
	}

	public List<GroupMessageModel> getAll() {
		return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
				null,
				null,
				null,
				null,
				null,
				null));
	}

	public GroupMessageModel getByApiMessageIdAndIdentity(MessageId apiMessageId, String identity) {
		return getFirst(
				GroupMessageModel.COLUMN_API_MESSAGE_ID + "=?" +
						" AND " + GroupMessageModel.COLUMN_IDENTITY + "=?",
				new String[]{
						apiMessageId.toString(),
						identity
				});
	}

	public GroupMessageModel getByApiMessageIdAndGroupId(@NonNull MessageId apiMessageId, int groupId) {
		return getFirst(
			GroupMessageModel.COLUMN_API_MESSAGE_ID + "=?" +
				" AND " + GroupMessageModel.COLUMN_GROUP_ID + "=?",
			new String[]{
				apiMessageId.toString(),
				String.valueOf(groupId),
			});
	}

	public GroupMessageModel getById(int id) {
		return getFirst(
				GroupMessageModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(id)
				});
	}

	public GroupMessageModel getByUid(String uid) {
		return getFirst(
				GroupMessageModel.COLUMN_UID + "=?",
				new String[]{
						uid
				});
	}

	public List<GroupMessageModel> getAllRejectedMessagesInGroup(@NonNull GroupModel group) {
		return convertList(
			this.databaseService.getReadableDatabase().query(
				getTableName(),
				null,
				GroupMessageModel.COLUMN_GROUP_ID + "=? AND " + AbstractMessageModel.COLUMN_STATE + "=?",
				new String[]{String.valueOf(group.getId()), MessageState.FS_KEY_MISMATCH.toString()},
				null,
				null,
				null
			)
		);
	}

	public List<AbstractMessageModel> getMessagesByText(@Nullable String text, boolean includeArchived, boolean starredOnly, boolean sortAscending) {
		String displayClause, sortClause;
		if (starredOnly) {
			displayClause = " AND (displayTags & " + DISPLAY_TAG_STARRED + ") > 0 ";
		} else {
			displayClause = "";
		}

		if (sortAscending) {
			sortClause = " ASC ";
		} else {
			sortClause = " DESC ";
		}

		if (includeArchived) {
			if (text == null) {
				return convertAbstractList(this.databaseService.getReadableDatabase().rawQuery(
					"SELECT * FROM " + GroupMessageModel.TABLE +
						" WHERE isStatusMessage = 0" +
						displayClause +
						" ORDER BY createdAtUtc" + sortClause +
						"LIMIT 200",
					new String[]{}));
			}

			return convertAbstractList(this.databaseService.getReadableDatabase().rawQuery(
				"SELECT * FROM " + GroupMessageModel.TABLE +
					" WHERE ( ( body LIKE ? " +
					" AND type IN (" +
					MessageType.TEXT.ordinal() + "," +
					MessageType.LOCATION.ordinal() + "," +
					MessageType.BALLOT.ordinal() + ") )" +
					" OR ( caption LIKE ? " +
					" AND type IN (" +
					MessageType.IMAGE.ordinal() + "," +
					MessageType.FILE.ordinal() + ") ) )" +
					" AND isStatusMessage = 0" +
					displayClause +
					" ORDER BY createdAtUtc" + sortClause +
					"LIMIT 200",
				new String[]{
					"%" + text + "%",
					"%" + text + "%"
				}));
		} else {
			if (text == null) {
				return convertAbstractList(this.databaseService.getReadableDatabase().rawQuery(
					"SELECT * FROM " + GroupMessageModel.TABLE + " m" +
						" INNER JOIN " + GroupModel.TABLE + " g ON g.id = m.groupId" +
						" WHERE g.isArchived = 0" +
						" AND m.isStatusMessage = 0" +
						displayClause +
						" ORDER BY m.createdAtUtc" + sortClause +
						"LIMIT 200",
					new String[]{}));
			}

			return convertAbstractList(this.databaseService.getReadableDatabase().rawQuery(
				"SELECT * FROM " + GroupMessageModel.TABLE + " m" +
					" INNER JOIN " + GroupModel.TABLE + " g ON g.id = m.groupId" +
					" WHERE g.isArchived = 0" +
					" AND ( ( m.body LIKE ? " +
					" AND m.type IN (" +
					MessageType.TEXT.ordinal() + "," +
					MessageType.LOCATION.ordinal() + "," +
					MessageType.BALLOT.ordinal() + ") )" +
					" OR ( m.caption LIKE ? " +
					" AND m.type IN (" +
					MessageType.IMAGE.ordinal() + "," +
					MessageType.FILE.ordinal() + ") ) )" +
					" AND m.isStatusMessage = 0" +
					displayClause +
					" ORDER BY m.createdAtUtc" + sortClause +
					"LIMIT 200",
				new String[]{
					"%" + text + "%",
					"%" + text + "%"
				}));
		}
	}

    /**
     * Convert a cursor's rows to a list of {@link AbstractMessageModel}s.
     * Note that the cursor will be closed after conversion.
     */
	private List<AbstractMessageModel> convertAbstractList(Cursor cursor) {
		List<AbstractMessageModel> result = new ArrayList<>();
		if(cursor != null) {
            try (cursor) {
                while (cursor.moveToNext()) {
                    result.add(convert(cursor));
                }
            }
		}
		return result;
	}

    /**
     * Convert a cursor's rows to a list of {@link GroupMessageModel}s.
     * Note that the cursor will be closed after conversion.
     */
	private List<GroupMessageModel> convertList(Cursor cursor) {
		List<GroupMessageModel> result = new ArrayList<>();
		if(cursor != null) {
            try (cursor) {
                while (cursor.moveToNext()) {
                    result.add(convert(cursor));
                }
            }
		}
		return result;
	}

	private GroupMessageModel convert(Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final GroupMessageModel groupMessageModel = new GroupMessageModel();

			//convert default
			super.convert(groupMessageModel, new CursorHelper(cursor, columnIndexCache).current((CursorHelper.Callback) cursorHelper -> {
                int groupId = Objects.requireNonNull(cursorHelper.getInt(GroupMessageModel.COLUMN_GROUP_ID));
                groupMessageModel.setGroupId(groupId);
                String messageStates = cursorHelper.getString(GroupMessageModel.COLUMN_GROUP_MESSAGE_STATES);
                if (messageStates != null) {
                    try {
                        Map<String, Object> messageStatesMap = JsonUtil.convertObject(messageStates);
                        groupMessageModel.setGroupMessageStates(messageStatesMap);
                    } catch (JSONException ignored) {
                        // map may not be available or empty
                        groupMessageModel.setGroupMessageStates(null);
                    }
                }
                return false;
            }));

			return groupMessageModel;
		}

		return null;
	}

	public long countMessages(int groupId) {
		return DatabaseUtil.count(this.databaseService.getReadableDatabase().rawQuery(
			"SELECT COUNT(*) FROM " + this.getTableName()
				+ " WHERE " + GroupMessageModel.COLUMN_GROUP_ID + "=?",
			new String[]{
				String.valueOf(groupId)
			}
		));
	}

	public long countUnreadMessages(int groupId) {
		return DatabaseUtil.count(this.databaseService.getReadableDatabase().rawQuery(
				"SELECT COUNT(*) FROM " + this.getTableName()
						+ " WHERE " + GroupMessageModel.COLUMN_GROUP_ID + "=?"
						+ " AND " + GroupMessageModel.COLUMN_OUTBOX + "=0"
						+ " AND " + GroupMessageModel.COLUMN_IS_SAVED + "=1"
						+ " AND " + GroupMessageModel.COLUMN_IS_READ + "=0"
						+ " AND " + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + "=0",
				new String[]{
						String.valueOf(groupId)
				}
		));
	}

	public List<GroupMessageModel> getUnreadMessages(int groupId) {
		return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
			null,
			GroupMessageModel.COLUMN_GROUP_ID + "=?"
				+ " AND " + GroupMessageModel.COLUMN_OUTBOX + "=0"
				+ " AND " + GroupMessageModel.COLUMN_IS_SAVED + "=1"
				+ " AND " + GroupMessageModel.COLUMN_IS_READ + "=0"
				+ " AND " + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + "=0",
			new String[]{
				String.valueOf(groupId)
			},
			null,
			null,
			null));
	}

	public long countByTypes(MessageType[] messageTypes) {
		String[] args = new String[messageTypes.length];
		for(int n = 0; n < messageTypes.length; n++) {
			args[n] = String.valueOf(messageTypes[n].ordinal());
		}

		Cursor c = this.databaseService.getReadableDatabase().rawQuery(
				"SELECT COUNT(*) FROM " + this.getTableName()
						+ " WHERE " + GroupMessageModel.COLUMN_TYPE + " IN (" + DatabaseUtil.makePlaceholders(args.length) + ")",
				args
		);
		return DatabaseUtil.count(c);
	}

	public boolean createOrUpdate(GroupMessageModel groupMessageModel) {
		boolean insert = true;
		if(groupMessageModel.getId() > 0) {
			Cursor cursor = this.databaseService.getReadableDatabase().query(
					this.getTableName(),
					null,
					GroupMessageModel.COLUMN_ID + "=?",
					new String[]{
							String.valueOf(groupMessageModel.getId())
					},
					null,
					null,
					null
			);

			if (cursor != null) {
				try (cursor) {
					insert = !cursor.moveToNext();
				}
			}
		}

		if(insert) {
			return create(groupMessageModel);
		}
		else {
			return update(groupMessageModel);
		}
	}

	public boolean create(GroupMessageModel groupMessageModel) {
		ContentValues contentValues = this.buildContentValues(groupMessageModel);
		contentValues.put(GroupMessageModel.COLUMN_GROUP_ID, groupMessageModel.getGroupId());
		addGroupMessageStates(contentValues, groupMessageModel);
		long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
		if (newId > 0) {
			groupMessageModel.setId((int) newId);
			return true;
		}
		return false;
	}

	public boolean update(GroupMessageModel groupMessageModel) {
		ContentValues contentValues = this.buildContentValues(groupMessageModel);
		addGroupMessageStates(contentValues, groupMessageModel);
		this.databaseService.getWritableDatabase().update(this.getTableName(),
				contentValues,
				GroupMessageModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(groupMessageModel.getId()),
				});
		return true;
	}

	public List<GroupMessageModel> find(int groupId, MessageService.MessageFilter filter) {
		QueryBuilder queryBuilder = new QueryBuilder();

		//sort by id!
		String orderBy = GroupMessageModel.COLUMN_ID + " DESC";
		List<String> placeholders = new ArrayList<>();

		queryBuilder.appendWhere(GroupMessageModel.COLUMN_GROUP_ID + "=?");
		placeholders.add(String.valueOf(groupId));

		//default filters
		this.appendFilter(queryBuilder, filter, placeholders);

		queryBuilder.setTables(this.getTableName());
		List<GroupMessageModel> messageModels = convertList(queryBuilder.query(
				this.databaseService.getReadableDatabase(),
				null,
				null,
				placeholders.toArray(new String[0]),
				null,
				null,
				orderBy,
				this.limitFilter(filter)));

		this.postFilter(messageModels, filter);

		return messageModels;
	}

	public List<GroupMessageModel> getByGroupIdUnsorted(int groupId) {
		return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
				null,
				GroupMessageModel.COLUMN_GROUP_ID + "=?",
				new String[]{
						String.valueOf(groupId)
				},
				null,
				null,
				null));
	}

	public int delete(GroupMessageModel groupMessageModel) {
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				GroupMessageModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(groupMessageModel.getId())
				});
	}

	public int deleteByGroupId(int groupId) {
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				GroupMessageModel.COLUMN_GROUP_ID + "=?",
				new String[]{
						String.valueOf(groupId)
				});
	}

	private GroupMessageModel getFirst(String selection, String[] selectionArgs) {
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
            try (cursor) {
                if (cursor.moveToFirst()) {
                    return convert(cursor);
                }
            }
		}

		return null;
	}

	private void addGroupMessageStates(@NonNull ContentValues contentValues, @NonNull GroupMessageModel groupMessageModel) {
		String groupMessageStates = null;
		if (groupMessageModel.getGroupMessageStates() != null) {
			groupMessageStates = new JSONObject(groupMessageModel.getGroupMessageStates()).toString();
		}

		contentValues.put(GroupMessageModel.COLUMN_GROUP_MESSAGE_STATES, groupMessageStates);
	}

	@Override
	public String[] getStatements() {
		return new String[] {
				"CREATE TABLE `" + GroupMessageModel.TABLE + "`" +
						"(" +
						"`" + GroupMessageModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT , " +
						"`" + GroupMessageModel.COLUMN_UID + "` VARCHAR , " +
						"`" + GroupMessageModel.COLUMN_API_MESSAGE_ID + "` VARCHAR , " +
						"`" + GroupMessageModel.COLUMN_GROUP_ID + "` INTEGER NOT NULL , " +

						//TODO: remove field
						"`" + GroupMessageModel.COLUMN_IDENTITY + "` VARCHAR , " +
						//TODO: change to TINYINT
						"`" + GroupMessageModel.COLUMN_OUTBOX + "` SMALLINT , " +
						"`" + GroupMessageModel.COLUMN_TYPE + "` INTEGER ," +
						"`" + GroupMessageModel.COLUMN_CORRELATION_ID + "` VARCHAR ," +
						"`" + GroupMessageModel.COLUMN_BODY + "` VARCHAR ," +
						"`" + GroupMessageModel.COLUMN_CAPTION + "` VARCHAR ," +
						//TODO: change to TINYINT
						"`" + GroupMessageModel.COLUMN_IS_READ + "` SMALLINT ," +
						//TODO: change to TINYINT
						"`" + GroupMessageModel.COLUMN_IS_SAVED + "` SMALLINT ," +
						"`" + GroupMessageModel.COLUMN_IS_QUEUED + "` TINYINT ," +
						"`" + GroupMessageModel.COLUMN_STATE + "` VARCHAR , " +
						"`" + GroupMessageModel.COLUMN_POSTED_AT + "` BIGINT , " +
						"`" + GroupMessageModel.COLUMN_CREATED_AT + "` BIGINT , " +
						"`" + GroupMessageModel.COLUMN_MODIFIED_AT + "` BIGINT , " +
						//TODO: change to TINYINT
						"`" + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE +"` SMALLINT ," +
						"`" + GroupMessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID +"` VARCHAR ," +
						"`" + GroupMessageModel.COLUMN_MESSAGE_CONTENTS_TYPE +"` TINYINT ," +
						"`" + GroupMessageModel.COLUMN_MESSAGE_FLAGS +"` INT ," +
						"`" + GroupMessageModel.COLUMN_DELIVERED_AT +"` DATETIME ," +
						"`" + GroupMessageModel.COLUMN_READ_AT +"` DATETIME ," +
						"`" + GroupMessageModel.COLUMN_FORWARD_SECURITY_MODE +"` TINYINT DEFAULT 0 ," +
						"`" + GroupMessageModel.COLUMN_GROUP_MESSAGE_STATES +"` VARCHAR ," +
						"`" + GroupMessageModel.COLUMN_DISPLAY_TAGS +"` TINYINT DEFAULT 0 ," +
						"`" + GroupMessageModel.COLUMN_EDITED_AT +"` DATETIME ," +
						"`" + GroupMessageModel.COLUMN_DELETED_AT +"` DATETIME );",

				//indices
				"CREATE INDEX `group_message_uid_idx` ON `" + GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_UID + "` )",
				"CREATE INDEX `m_group_message_outbox_idx` ON `" + GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_OUTBOX + "` );",
				"CREATE INDEX `m_group_message_identity_idx` ON `"+ GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_IDENTITY + "` );",
				"CREATE INDEX `m_group_message_groupId_idx` ON `"+ GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_GROUP_ID + "` );",
				"CREATE INDEX `groupMessageApiMessageIdIdx` ON `"+ GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_API_MESSAGE_ID + "` );",
				"CREATE INDEX `groupMessageCorrelationIdIdx` ON `"+ GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_CORRELATION_ID + "` );",
				"CREATE INDEX `group_message_state_idx` ON `" + GroupMessageModel.TABLE
					+ "`(`"  + AbstractMessageModel.COLUMN_TYPE
					+ "`, `" + AbstractMessageModel.COLUMN_STATE
					+ "`, `" + AbstractMessageModel.COLUMN_OUTBOX
					+ "`)",
		};
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.GroupCallModel;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GroupCallModelFactory extends ModelFactory {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupCallModelFactory");

	public GroupCallModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, GroupCallModel.TABLE);
	}

	@Override
	public String[] getStatements() {
		return new String[]{
			"CREATE TABLE `" + getTableName() + "` (" +
				"`" + GroupCallModel.COLUMN_CALL_ID + "` TEXT PRIMARY KEY NOT NULL, " +
				"`" + GroupCallModel.COLUMN_GROUP_ID + "` INTEGER NOT NULL, " +
				"`" + GroupCallModel.COLUMN_SFU_BASE_URL + "` TEXT NOT NULL, " +
				"`" + GroupCallModel.COLUMN_GCK + "` TEXT NOT NULL, " +
				"`" + GroupCallModel.COLUMN_PROTOCOL_VERSION + "` INTEGER DEFAULT 0," +
				"`" + GroupCallModel.COLUMN_STARTED_AT + "` BIGINT NOT NULL)"
		};
	}

	@NonNull
	public List<GroupCallModel> getAll() {
		String[] columns = new String[] {
			GroupCallModel.COLUMN_CALL_ID,
			GroupCallModel.COLUMN_GROUP_ID,
			GroupCallModel.COLUMN_SFU_BASE_URL,
			GroupCallModel.COLUMN_GCK,
			GroupCallModel.COLUMN_PROTOCOL_VERSION,
			GroupCallModel.COLUMN_STARTED_AT
		};
		Cursor cursor = databaseService.getReadableDatabase()
			.query(getTableName(), columns, null, null, null, null, null);
		try (cursor) {
			List<GroupCallModel> calls = convertList(cursor);
			logger.debug("Get {} calls from database", calls.size());
			return calls;
		} catch (SQLiteException e) {
			logger.error("Error while getting group call models", e);
			return Collections.emptyList();
		}
	}

	public void createOrUpdate(GroupCallModel call) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(GroupCallModel.COLUMN_CALL_ID, call.getCallId());
		contentValues.put(GroupCallModel.COLUMN_GROUP_ID, call.getGroupId());
		contentValues.put(GroupCallModel.COLUMN_SFU_BASE_URL, call.getSfuBaseUrl());
		contentValues.put(GroupCallModel.COLUMN_GCK, call.getGck());
		contentValues.put(GroupCallModel.COLUMN_PROTOCOL_VERSION, call.getProtocolVersion());
		contentValues.put(GroupCallModel.COLUMN_STARTED_AT, call.getStartedAt());

		try {
			long id = databaseService.getWritableDatabase()
				.insertWithOnConflict(getTableName(), null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
			logger.debug("Insert or update call: {}", id);
		} catch (SQLiteException e) {
			logger.error("Could not create or update call", e);
		}
	}

	public void delete(GroupCallModel call) {
		try {
			int count = databaseService.getWritableDatabase().delete(
				getTableName(),
				GroupCallModel.COLUMN_CALL_ID + "=?",
				new String[] {call.getCallId() }
			);
			logger.debug("Delete call with id {}: {}", call.getCallId(), count);
		} catch (SQLiteException e) {
			logger.error("Could not delete call", e);
		}
	}

	@NonNull
	private List<GroupCallModel> convertList(Cursor cursor) {
		List<GroupCallModel> calls = new ArrayList<>();
		if (cursor != null) {
			while (cursor.moveToNext()) {
				GroupCallModel call = convert(cursor);
				if (call != null) {
					calls.add(call);
				}
			}
		}
		return calls;
	}

	@Nullable
	private GroupCallModel convert(@NonNull Cursor cursor) {
		CursorHelper.CallbackInstance<GroupCallModel> converter = cursorHelper -> {
			Integer protocolVersion = cursorHelper.getInt(GroupCallModel.COLUMN_PROTOCOL_VERSION);
			String callId = cursorHelper.getString(GroupCallModel.COLUMN_CALL_ID);
			Integer groupId = cursorHelper.getInt(GroupCallModel.COLUMN_GROUP_ID);
			String baseUrl = cursorHelper.getString(GroupCallModel.COLUMN_SFU_BASE_URL);
			String gck = cursorHelper.getString(GroupCallModel.COLUMN_GCK);
			Long startedAt = cursorHelper.getLong(GroupCallModel.COLUMN_STARTED_AT);
			return protocolVersion == null || groupId == null || baseUrl == null || callId == null || gck == null
				? null
				: new GroupCallModel(protocolVersion, callId, groupId, baseUrl, gck, startedAt);
		};
		return new CursorHelper(cursor, columnIndexCache).current(converter);
	}
}

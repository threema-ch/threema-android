/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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
import android.database.SQLException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import ch.threema.base.Result;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.group.GroupInviteModel;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;
import java8.util.Optional;

public class IncomingGroupJoinRequestModelFactory extends ModelFactory {

	public IncomingGroupJoinRequestModelFactory(DatabaseServiceNew databaseServiceNew) {
		super(databaseServiceNew, IncomingGroupJoinRequestModel.TABLE);
	}

	@Override
	public String[] getStatements() {
		final String createTableStatement = "CREATE TABLE IF NOT EXISTS`" + IncomingGroupJoinRequestModel.TABLE + "` ( " +
			"`" + IncomingGroupJoinRequestModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"`" + IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE + "` INTEGER, " +
			"`" + IncomingGroupJoinRequestModel.COLUMN_MESSAGE + "` TEXT, " +
			"`" + IncomingGroupJoinRequestModel.COLUMN_REQUESTING_IDENTITY + "` VARCHAR, " +
			"`" + IncomingGroupJoinRequestModel.COLUMN_REQUEST_TIME + "` DATETIME, " +
			"`" + IncomingGroupJoinRequestModel.COLUMN_RESPONSE_STATUS + "` VARCHAR " +
			")";

		return new String[] {
			createTableStatement,
		};
	}

	public Result<IncomingGroupJoinRequestModel, Exception> insert(IncomingGroupJoinRequestModel model) {
		final ContentValues contentValues = incomingGroupJoinRequestModelToContentValues(model);
		final long newId;
		try {
			newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
			model.setId((int) newId);
		} catch (SQLException e) {
			return Result.failure(e);
		}

		if (newId <= 0) {
			return Result.failure(new Exception(NO_RECORD_MSG + newId));
		}
		return Result.success(model);
	}

	public void updateStatus(
		@NonNull IncomingGroupJoinRequestModel model,
		@NonNull IncomingGroupJoinRequestModel.ResponseStatus status
	) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(IncomingGroupJoinRequestModel.COLUMN_RESPONSE_STATUS, status.name());
		this.update(model.getId(), contentValues);
	}

	public void updateRequestMessage(
		@NonNull IncomingGroupJoinRequestModel model,
		@NonNull GroupJoinRequestMessage message
	) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(IncomingGroupJoinRequestModel.COLUMN_MESSAGE, message.getData().getMessage());
		contentValues.put(IncomingGroupJoinRequestModel.COLUMN_REQUEST_TIME, message.getDate().getTime());
		this.update(model.getId(), contentValues);
	}

	/**
	 * Update a (single) record. Throws a {@link SQLException} if no record matched the query.
	 *
	 * @param id of the record that should be changed
	 * @param values that should be changed
	 */
	public void update(int id, final @NonNull ContentValues values) throws SQLException {
		int rowsAffected = this.databaseService.getWritableDatabase().update(
			this.getTableName(),
			values,
			IncomingGroupJoinRequestModel.COLUMN_ID + "=?",
			new String[]{
				String.valueOf(id)
			}
		);

		if (rowsAffected != 1) {
			throw new SQLException(NO_RECORD_MSG + id);
		}
	}

	public void delete(IncomingGroupJoinRequestModel model) throws SQLException {
		int rowsAffected = this.databaseService.getWritableDatabase().delete(this.getTableName(),
			IncomingGroupJoinRequestModel.COLUMN_ID + "=?",
			new String[]{
				String.valueOf(model.getId())
			});

		if (rowsAffected != 1) {
			throw new SQLException(NO_RECORD_MSG + model.getId());
		}
	}

	public void deleteAllForGroupInvite(int groupInviteId) {
		this.databaseService.getWritableDatabase().delete(this.getTableName(),
			IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE + "=?",
			new String[]{
				String.valueOf(groupInviteId)
			});
	}

	public @NonNull List<IncomingGroupJoinRequestModel> getAll() {
		return this.getCursorResultModelList(
			null,
			null
		);
	}

	public @NonNull List<IncomingGroupJoinRequestModel> getAllOpenRequests() {
		final String selection = IncomingGroupJoinRequestModel.COLUMN_RESPONSE_STATUS + " =?";
		final String[] selectionArgs = new String[] {
			IncomingGroupJoinRequestModel.ResponseStatus.OPEN.name()
		};
		return this.getCursorResultModelList(
			selection,
			selectionArgs
		);
	}

	public @NonNull Optional<IncomingGroupJoinRequestModel> getById(int id) {
		final String selection = IncomingGroupJoinRequestModel.COLUMN_ID + " =?";
		final String[] selectionArgs = new String[] {
			String.valueOf(id),
		};
		return this.getFirstCursorResultModel(
			selection,
			selectionArgs
		);
	}

	public @NonNull List<IncomingGroupJoinRequestModel> getByGroupInvite(int groupInviteId) {
		final String selection = IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE + " =?";
		final String[] selectionArgs = new String[] {
			String.valueOf(groupInviteId),
		};

		return this.getCursorResultModelList(selection, selectionArgs);
	}

	public @NonNull List<IncomingGroupJoinRequestModel> getSingleMostRecentOpenRequestsPerUserForGroup(GroupId groupId) {
		String selection = "SELECT * FROM " + this.getTableName() + " b INNER JOIN " + GroupInviteModel.TABLE + " l"
			+ " ON b." + IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE + " = l." + GroupInviteModel.COLUMN_ID
			+ " WHERE l." + GroupInviteModel.COLUMN_GROUP_ID + " =?"
			+ " AND b." + IncomingGroupJoinRequestModel.COLUMN_RESPONSE_STATUS + " =?"
			+ " AND b." + IncomingGroupJoinRequestModel.COLUMN_ID + " IN" // nested query for the entry id of the last recent request per ID
			+ " (SELECT b2." + IncomingGroupJoinRequestModel.COLUMN_ID + " FROM " + this.getTableName() + " b2"
			+ " WHERE b2." + IncomingGroupJoinRequestModel.COLUMN_REQUESTING_IDENTITY + " =b."  + IncomingGroupJoinRequestModel.COLUMN_REQUESTING_IDENTITY
			+ " ORDER BY b2." + IncomingGroupJoinRequestModel.COLUMN_REQUEST_TIME + " DESC"
			+ " LIMIT 1)";

		final String[] selectionArgs = new String[] {
			groupId.toString(),
			IncomingGroupJoinRequestModel.ResponseStatus.OPEN.name()
		};

		Cursor cursor = this.databaseService.getReadableDatabase().rawQuery(selection, selectionArgs);
		return getAllModelsFromCursor(cursor);
	}

	public @NonNull List<IncomingGroupJoinRequestModel> getAllRequestsForGroup(GroupId groupId) {
		String selection = "SELECT * FROM " + this.getTableName() + " b INNER JOIN " + GroupInviteModel.TABLE + " l"
			+ " ON b." + IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE + " = l." + GroupInviteModel.COLUMN_ID
			+ " WHERE l." + GroupInviteModel.COLUMN_GROUP_ID + " =?";

		final String[] selectionArgs = new String[] {
			groupId.toString()
		};

		Cursor cursor = this.getReadableDatabase().rawQuery(selection, selectionArgs);
		return getAllModelsFromCursor(cursor);
	}

	public @NonNull List<IncomingGroupJoinRequestModel> getAllOpenRequestsForGroup(GroupId groupId) {
		String selection = "SELECT * FROM " + this.getTableName() + " b INNER JOIN " + GroupInviteModel.TABLE + " l"
			+ " ON b." + IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE + " = l." + GroupInviteModel.COLUMN_ID
			+ " WHERE l." + GroupInviteModel.COLUMN_GROUP_ID + " =?"
			+ " AND b." + IncomingGroupJoinRequestModel.COLUMN_RESPONSE_STATUS + " =?";

		final String[] selectionArgs = new String[] {
			groupId.toString(),
			IncomingGroupJoinRequestModel.ResponseStatus.OPEN.name()
		};

		Cursor cursor = this.getReadableDatabase().rawQuery(selection, selectionArgs);
		return getAllModelsFromCursor(cursor);
	}

	public @NonNull Optional<IncomingGroupJoinRequestModel> getRequestByGroupInviteAndIdentity(int groupInviteId, String identity) {
		final String selection = IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE + " =?"
			+ " AND " + IncomingGroupJoinRequestModel.COLUMN_REQUESTING_IDENTITY + " =?";

		final String[] selectionArgs = new String[] {
			String.valueOf(groupInviteId),
			identity,
		};
		return this.getFirstCursorResultModel(selection, selectionArgs);
	}

	public @NonNull List<IncomingGroupJoinRequestModel> getAllOpenRequestsByGroupIdAndIdentity(
		GroupId groupId,
		@NonNull String identity)
	{
		String selection = "SELECT *" + " FROM " + this.getTableName() + " b INNER JOIN " + GroupInviteModel.TABLE + " l"
			+ " ON b." + IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE + " = l." + GroupInviteModel.COLUMN_ID
			+ " WHERE l." + GroupInviteModel.COLUMN_GROUP_ID + " =?"
			+ " AND b." + IncomingGroupJoinRequestModel.COLUMN_REQUESTING_IDENTITY + " =?"
			+ " AND b." + IncomingGroupJoinRequestModel.COLUMN_RESPONSE_STATUS + " =?";

		final String[] selectionArgs = new String[] {
			groupId.toString(),
			identity,
			IncomingGroupJoinRequestModel.ResponseStatus.OPEN.name()
		};

		Cursor cursor = this.getReadableDatabase().rawQuery(selection, selectionArgs);
		return getAllModelsFromCursor(cursor);
	}

	private @NonNull Optional<IncomingGroupJoinRequestModel> getFirstCursorResultModel(
		@NonNull String selection,
		@NonNull String[] selectionArgs
	) {
		final Cursor cursor = this.databaseService.getReadableDatabase().query(
			this.getTableName(),
			null,
			selection,
			selectionArgs,
			null,
			null,
			null
		);

		try (final CursorHelper cursorHelper = new CursorHelper(cursor, this.getColumnIndexCache())) {
			final Iterator<IncomingGroupJoinRequestModel> modelIterator =
				cursorHelper.modelIterator(IncomingGroupJoinRequestModelFactory::cursorHelperToIncomingGroupJoinRequestModel);
			if (modelIterator.hasNext()) {
				return Optional.of(modelIterator.next());
			}
		}

		return Optional.empty();
	}

	private @NonNull List<IncomingGroupJoinRequestModel> getCursorResultModelList(
		@Nullable String selection,
		@Nullable String[] selectionArgs
	) {
		final Cursor cursor = this.databaseService.getReadableDatabase().query(this.getTableName(),
			null,
			selection,
			selectionArgs,
			null,
			null,
			null);

		return getAllModelsFromCursor(cursor);
	}

	private List<IncomingGroupJoinRequestModel> getAllModelsFromCursor(Cursor cursor) {
		final CursorHelper cursorHelper = new CursorHelper(cursor, this.getColumnIndexCache());
		final Iterator<IncomingGroupJoinRequestModel> modelIterator =
			cursorHelper.modelIterator(IncomingGroupJoinRequestModelFactory::cursorHelperToIncomingGroupJoinRequestModel);
		final List<IncomingGroupJoinRequestModel> groupInvites = new ArrayList<>(cursor.getCount());

		while (modelIterator.hasNext()) {
			groupInvites.add(modelIterator.next());
		}
		return groupInvites;
	}

	private static ContentValues incomingGroupJoinRequestModelToContentValues(IncomingGroupJoinRequestModel groupJoinRequestModel) {
		final ContentValues contentValues = new ContentValues();
		if (groupJoinRequestModel.getId() >= 0) {
			contentValues.put(IncomingGroupJoinRequestModel.COLUMN_ID, groupJoinRequestModel.getId());
		}
		contentValues.put(IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE, groupJoinRequestModel.getGroupInviteId());
		contentValues.put(IncomingGroupJoinRequestModel.COLUMN_MESSAGE, groupJoinRequestModel.getMessage());
		contentValues.put(IncomingGroupJoinRequestModel.COLUMN_REQUESTING_IDENTITY, groupJoinRequestModel.getRequestingIdentity());
		contentValues.put(IncomingGroupJoinRequestModel.COLUMN_REQUEST_TIME, groupJoinRequestModel.getRequestTime().getTime());
		contentValues.put(IncomingGroupJoinRequestModel.COLUMN_RESPONSE_STATUS, groupJoinRequestModel.getResponseStatus().name());

		return contentValues;
	}

	private static IncomingGroupJoinRequestModel cursorHelperToIncomingGroupJoinRequestModel(CursorHelper cursorHelper) {
		return new IncomingGroupJoinRequestModel(
			Objects.requireNonNull(cursorHelper.getInt(IncomingGroupJoinRequestModel.COLUMN_ID)),
			Objects.requireNonNull(cursorHelper.getInt(IncomingGroupJoinRequestModel.COLUMN_GROUP_INVITE)),
			Objects.requireNonNull(cursorHelper.getString(IncomingGroupJoinRequestModel.COLUMN_MESSAGE)),
			Objects.requireNonNull(cursorHelper.getString(IncomingGroupJoinRequestModel.COLUMN_REQUESTING_IDENTITY)),
			Objects.requireNonNull(cursorHelper.getDate(IncomingGroupJoinRequestModel.COLUMN_REQUEST_TIME)),
			IncomingGroupJoinRequestModel.ResponseStatus.valueOf(
				cursorHelper.getString(IncomingGroupJoinRequestModel.COLUMN_RESPONSE_STATUS)
			)
		);
	}
}

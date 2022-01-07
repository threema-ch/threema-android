/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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
import net.sqlcipher.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.Result;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.group.GroupInviteModel;
import java8.util.Optional;

public class GroupInviteModelFactory extends ModelFactory {
	private static final Logger logger = LoggerFactory.getLogger(GroupInviteModelFactory.class);

	public GroupInviteModelFactory(DatabaseServiceNew databaseServiceNew) {
		super(databaseServiceNew, GroupInviteModel.TABLE);
	}

	@Override
	public @NonNull String[] getStatements() {
		final String createTableStatement = "CREATE TABLE `" + GroupInviteModel.TABLE + "` ( " +
			"`" + GroupInviteModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT, " +
		    "`" + GroupInviteModel.COLUMN_GROUP_ID + "` INTEGER, " +
			"`" + GroupInviteModel.COLUMN_DEFAULT_FLAG + "` BOOLEAN, " +
		    "`" + GroupInviteModel.COLUMN_TOKEN + "` VARCHAR, " +
			"`" + GroupInviteModel.COLUMN_INVITE_NAME + "` TEXT, " +
		    "`" + GroupInviteModel.COLUMN_ORIGINAL_GROUP_NAME + "` TEXT, " +
		    "`" + GroupInviteModel.COLUMN_MANUAL_CONFIRMATION + "` BOOLEAN, " +
		    "`" + GroupInviteModel.COLUMN_EXPIRATION_DATE + "` DATETIME NULL, " +
			"`" + GroupInviteModel.COLUMN_IS_INVALIDATED + "` BOOLEAN FALSE " +
			")";

		final String indexGroupIdStatement = "CREATE INDEX `" +
			GroupInviteModel.TABLE + "_" + GroupInviteModel.COLUMN_GROUP_ID + "_idx` ON "
			+ GroupInviteModel.TABLE + " ( `" + GroupInviteModel.COLUMN_GROUP_ID + "` )";

		final String tokenUniqueIndex = "CREATE UNIQUE INDEX `" +
			GroupInviteModel.TABLE + "_" + GroupInviteModel.COLUMN_TOKEN + "_idx` ON "
			+ GroupInviteModel.TABLE + " ( `" + GroupInviteModel.COLUMN_TOKEN + "` )";

		return new String[] {
			createTableStatement,
			indexGroupIdStatement,
			tokenUniqueIndex,
		};
	}

	public Result<GroupInviteModel, Exception> insert(GroupInviteModel groupInviteModel) {
		ContentValues contentValues = buildContentValues(groupInviteModel);
		try {
			long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);

			if(newId < 0) {
				return Result.failure(new IOException("Database returned invalid id for new record: " + newId));
			}
			groupInviteModel.setId((int) newId);
		} catch (SQLException e) {
			return Result.failure(e);
		}

		return Result.success(groupInviteModel);
	}

	public boolean update(GroupInviteModel groupInviteModel) throws SQLException {
		ContentValues contentValues = buildContentValues(groupInviteModel);
		int rowsAffected = this.databaseService.getWritableDatabase().update(this.getTableName(),
			contentValues,
			GroupInviteModel.COLUMN_ID + "=?",
			new String[]{
				String.valueOf(groupInviteModel.getId())
			});

		logger.debug("rowsAffected {}", rowsAffected);
		if (rowsAffected != 1) {
			throw new SQLException(NO_RECORD_MSG + groupInviteModel.getId());
		}
		return true;
	}

	private void update(int id, final @NonNull ContentValues values) throws SQLException {
		int rowsAffected = this.databaseService.getWritableDatabase().update(
			this.getTableName(),
			values,
			GroupInviteModel.COLUMN_ID + "=?",
			new String[]{
				String.valueOf(id)
			}
		);

		if (rowsAffected != 1) {
			throw new SQLException(NO_RECORD_MSG + id);
		}
	}

	/**
	 * Marks a group invite as deleted with a flag but retains the db entry to check the token validity
	 * @param groupInviteModel GroupInviteModel to be marked as deleted
	 */
	public void delete(GroupInviteModel groupInviteModel) throws SQLException {
		ContentValues contentValues = new ContentValues();
		contentValues.put(GroupInviteModel.COLUMN_IS_INVALIDATED, true);
		this.update(groupInviteModel.getId(), contentValues);
	}

	public @NonNull List<GroupInviteModel> getAllActive() {
		final String selection = GroupInviteModel.COLUMN_IS_INVALIDATED + "=?";
		final String[] selectionArgs = new String[]{ String.valueOf(0) };
		return this.getCursorResultModelList(
			selection,
			selectionArgs
		);
	}

	/**
	 * Return the number of all active group links that have been created individually apart from the default link
	 */
	public @NonNull List<GroupInviteModel> getAllActiveCustom() {
		final String selection = GroupInviteModel.COLUMN_IS_INVALIDATED + "=?"
			+ " AND " + GroupInviteModel.COLUMN_DEFAULT_FLAG + "=?";
		final String[] selectionArgs = new String[]{ String.valueOf(0), String.valueOf(0) };
		return this.getCursorResultModelList(
			selection,
			selectionArgs
		);
	}

	public @NonNull Optional<GroupInviteModel> getById(int id) {
		final String selection = GroupInviteModel.COLUMN_ID + " =?";
		final String[] selectionArgs = new String[] {
			String.valueOf(id),
		};
		return this.getFirstCursorResultModel(
			selection,
			selectionArgs
		);
	}

	public @NonNull Optional<GroupInviteModel> getByToken(@NonNull String token) {
		return this.getFirstCursorResultModel(
			GroupInviteModel.COLUMN_TOKEN + "=?",
			new String[]{ token }
		);
	}

	public @NonNull List<GroupInviteModel> getByGroupId(int groupId) {
		final String selection = GroupInviteModel.COLUMN_GROUP_ID + "=?"
			+ " AND " + GroupInviteModel.COLUMN_IS_INVALIDATED + " =?";

		final String[] selectionArgs = new String[] {
			String.valueOf(groupId),
			String.valueOf(0),
		};

		return this.getCursorResultModelList(
			selection,
			selectionArgs
		);
	}

	public @NonNull Optional<GroupInviteModel>  getDefaultByGroupId(int groupId) {
		final Cursor cursor = this.databaseService.getReadableDatabase().rawQuery(
			"SELECT * FROM "+ getTableName() +
				" WHERE " + GroupInviteModel.COLUMN_DEFAULT_FLAG + " =" + 1 +
				" AND " + GroupInviteModel.COLUMN_GROUP_ID + " =" + groupId +
				" ORDER BY " + GroupInviteModel.COLUMN_ID + " DESC LIMIT 1", null);

		return returnFirstModelFromCursor(cursor);
	}

	private @NonNull Optional<GroupInviteModel> getFirstCursorResultModel(
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

		return returnFirstModelFromCursor(cursor);
	}

	private @NonNull Optional<GroupInviteModel> returnFirstModelFromCursor(Cursor cursor) {
		try (final CursorHelper cursorHelper = new CursorHelper(cursor, this.getColumnIndexCache())) {
			final Iterator<GroupInviteModel> modelIterator =
				cursorHelper.modelIterator(GroupInviteModelFactory::cursorHelperToGroupInviteModel);

			if (modelIterator.hasNext()) {
				return Optional.of(modelIterator.next());
			}
		}

		return Optional.empty();
	}

	private @NonNull List<GroupInviteModel> getCursorResultModelList(
		@Nullable String selection,
		@Nullable String[] selectionArgs
	) {
		final Cursor cursor = this.databaseService.getReadableDatabase().query(
			this.getTableName(),
			null,
			selection,
			selectionArgs,
			null,
			null,
			null);

		final CursorHelper cursorHelper = new CursorHelper(cursor, this.getColumnIndexCache());
		final Iterator<GroupInviteModel> modelIterator =
			cursorHelper.modelIterator(GroupInviteModelFactory::cursorHelperToGroupInviteModel);
		final List<GroupInviteModel> groupInvites = new ArrayList<>(cursor.getCount());

		while (modelIterator.hasNext()) {
			groupInvites.add(modelIterator.next());
		}
		return groupInvites;
	}

	private static GroupInviteModel cursorHelperToGroupInviteModel(CursorHelper cursorHelper) {
		try {
			return new GroupInviteModel.Builder()
				.withId(Objects.requireNonNull(cursorHelper.getInt(GroupInviteModel.COLUMN_ID)))
				.withGroupId(Objects.requireNonNull(cursorHelper.getInt(GroupInviteModel.COLUMN_GROUP_ID)))
				.withToken(GroupInviteToken.fromHexString(Objects.requireNonNull(cursorHelper.getString(GroupInviteModel.COLUMN_TOKEN))))
				.withGroupName(Objects.requireNonNull(cursorHelper.getString(GroupInviteModel.COLUMN_ORIGINAL_GROUP_NAME)))
				.withInviteName(Objects.requireNonNull(cursorHelper.getString(GroupInviteModel.COLUMN_INVITE_NAME)))
				.withManualConfirmation(cursorHelper.getBoolean(GroupInviteModel.COLUMN_MANUAL_CONFIRMATION))
				.withExpirationDate(cursorHelper.getDateByString(GroupInviteModel.COLUMN_EXPIRATION_DATE))
				.setIsInvalidated(cursorHelper.getBoolean(GroupInviteModel.COLUMN_IS_INVALIDATED))
				.setIsDefault(cursorHelper.getBoolean(GroupInviteModel.COLUMN_DEFAULT_FLAG))
				.build();
		} catch (GroupInviteToken.InvalidGroupInviteTokenException | GroupInviteModel.MissingRequiredArgumentsException e) {
			throw new IllegalStateException("InvalidGroupInviteTokenException, could not convert GroupInviteModel from cursor" + e);
		}
	}

	private ContentValues buildContentValues(GroupInviteModel groupInviteModel) {
		ContentValues contentValues = new ContentValues();
		if (groupInviteModel.getId() >= 0) {
			contentValues.put(GroupInviteModel.COLUMN_ID, groupInviteModel.getId());
		}
		contentValues.put(GroupInviteModel.COLUMN_GROUP_ID, groupInviteModel.getGroupId());
		contentValues.put(GroupInviteModel.COLUMN_TOKEN, groupInviteModel.getToken().toString());
		contentValues.put(GroupInviteModel.COLUMN_INVITE_NAME, groupInviteModel.getInviteName());
		contentValues.put(GroupInviteModel.COLUMN_ORIGINAL_GROUP_NAME, groupInviteModel.getOriginalGroupName());
		contentValues.put(GroupInviteModel.COLUMN_MANUAL_CONFIRMATION, groupInviteModel.getManualConfirmation());
		contentValues.put(GroupInviteModel.COLUMN_EXPIRATION_DATE,
			groupInviteModel.getExpirationDate() != null ?
				CursorHelper.dateAsStringFormat.get().format(groupInviteModel.getExpirationDate()) : null);
		contentValues.put(GroupInviteModel.COLUMN_IS_INVALIDATED, groupInviteModel.isInvalidated()); // default false as this can only be called on available or new group invites
		contentValues.put(GroupInviteModel.COLUMN_DEFAULT_FLAG, groupInviteModel.isDefault())
;
		return contentValues;
	}
}

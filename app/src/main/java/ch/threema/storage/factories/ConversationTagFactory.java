/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.models.ConversationTagModel;

public class ConversationTagFactory extends ModelFactory {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ConversationTagFactory");

	public ConversationTagFactory(DatabaseServiceNew databaseService) {
		super(databaseService, ConversationTagModel.TABLE);
	}

	public List<ConversationTagModel> getAll() {
		try (Cursor cursor = this.databaseService.getReadableDatabase().query(this.getTableName(),
				null,
				null,
				null,
				null,
				null,
				null)) {

			return convertList(cursor);
		}
	}

	public ConversationTagModel getByConversationUidAndTag(String conversationUid, String tag) {
		return getFirst(
			ConversationTagModel.COLUMN_CONVERSATION_UID + "=? AND "
				+ ConversationTagModel.COLUMN_TAG + "=? ",
				new String[]{
					conversationUid,
					tag
				});
	}

	public long countByTag(String tag) {
		return DatabaseUtil.count(this.databaseService.getReadableDatabase().rawQuery(
			"SELECT COUNT(*) FROM " + this.getTableName()
				+ " WHERE " + ConversationTagModel.COLUMN_TAG + "=?",
			new String[]{
				tag
			}
		));
	}

	@NonNull
	public List<String> getAllConversationUidsByTag(@NonNull String tag) {
		try (Cursor cursor = databaseService.getReadableDatabase().query(
			SupportSQLiteQueryBuilder.builder(getTableName())
				.columns(new String[]{ConversationTagModel.COLUMN_CONVERSATION_UID})
				.selection(ConversationTagModel.COLUMN_TAG + " = ?", new String[]{tag})
				.create()
		)) {
			List<String> conversationUids = new ArrayList<>(cursor.getCount());
			int columnIndex =
					cursor.getColumnIndexOrThrow(ConversationTagModel.COLUMN_CONVERSATION_UID);
			while (cursor.moveToNext()) {
				conversationUids.add(cursor.getString(columnIndex));
			}
			return conversationUids;
		} catch (SQLException | IllegalArgumentException e) {
			logger.error("Could not get uids by tag '{}'", tag, e);
			return List.of();
		}
	}

	private List<ConversationTagModel> convertList(Cursor cursor) {
		List<ConversationTagModel> result = new ArrayList<>();
		if (cursor != null) {
			while (cursor.moveToNext()) {
				result.add(convert(cursor));
			}
		}
		return result;
	}

	private ConversationTagModel convert(Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final ConversationTagModel c = new ConversationTagModel();

			//convert default
			new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
				@Override
				public boolean next(CursorHelper cursorHelper) {
					c
							.setConversationUid(cursorHelper.getString(ConversationTagModel.COLUMN_CONVERSATION_UID))
							.setTag(cursorHelper.getString(ConversationTagModel.COLUMN_TAG))
							.setCreatedAt(cursorHelper.getDate(ConversationTagModel.COLUMN_CREATED_AT));

					return false;
				}
			});

			return c;
		}

		return null;
	}

	private ContentValues buildContentValues(ConversationTagModel model) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(ConversationTagModel.COLUMN_CONVERSATION_UID, model.getConversationUid());
		contentValues.put(ConversationTagModel.COLUMN_TAG, model.getTag());
		contentValues.put(ConversationTagModel.COLUMN_CREATED_AT, model.getCreatedAt() != null ? model.getCreatedAt().getTime() : null);
		return contentValues;
	}

	public void create(ConversationTagModel model) {
		logger.debug("create conversation tag " + model.getConversationUid() + " " + model.getTag());
		ContentValues contentValues = buildContentValues(model);
		this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
	}

	public void deleteByConversationUidAndTag(String conversationUid, String tag) {
		this.databaseService.getWritableDatabase().delete(this.getTableName(),
			ConversationTagModel.COLUMN_CONVERSATION_UID + "=? AND "
				+ ConversationTagModel.COLUMN_TAG + "=? ",
			new String[]{
				conversationUid,
				tag
			});
	}

	public void deleteByConversationUid(String conversationUid) {
		this.databaseService.getWritableDatabase().delete(this.getTableName(),
			ConversationTagModel.COLUMN_CONVERSATION_UID + "=?",
			new String[]{
				conversationUid
			});
	}

	private ConversationTagModel getFirst(String selection, String[] selectionArgs) {
		Cursor cursor = this.databaseService.getReadableDatabase().query (
				this.getTableName(),
				null,
				selection,
				selectionArgs,
				null,
				null,
				null
		);

		if (cursor != null) {
			try (cursor) {
				if (cursor.moveToFirst()) {
					return convert(cursor);
				}
			}
		}

		return null;
	}


	@Override
	public String[] getStatements() {
		return new String[] {
			"CREATE TABLE IF NOT EXISTS `" + ConversationTagModel.TABLE + "` (" +
				"`" + ConversationTagModel.COLUMN_CONVERSATION_UID + "` VARCHAR NOT NULL, " +
				"`" + ConversationTagModel.COLUMN_TAG + "` BLOB NULL," +
				"`" + ConversationTagModel.COLUMN_CREATED_AT + "` BIGINT, " +
				"PRIMARY KEY (`" + ConversationTagModel.COLUMN_CONVERSATION_UID + "`, `" + ConversationTagModel.COLUMN_TAG + "`) " +
				");",

			"CREATE UNIQUE INDEX IF NOT EXISTS `conversationTagKeyConversationTag` ON `" + ConversationTagModel.TABLE
				+ "` ( `" + ConversationTagModel.COLUMN_CONVERSATION_UID + "`, `" + ConversationTagModel.COLUMN_TAG + "` );",
			"CREATE INDEX IF NOT EXISTS `conversationTagConversation` ON `" + ConversationTagModel.TABLE + "` ( `" + ConversationTagModel.COLUMN_CONVERSATION_UID + "` );",
			"CREATE INDEX IF NOT EXISTS`conversationTagTag` ON `" + ConversationTagModel.TABLE + "` ( `" + ConversationTagModel.COLUMN_TAG + "` );"
		};
	}

}

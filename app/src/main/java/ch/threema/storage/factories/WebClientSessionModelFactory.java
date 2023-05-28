/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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
import java.util.Date;
import java.util.List;

import androidx.annotation.Nullable;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.Utils;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.WebClientSessionModel;

public class WebClientSessionModelFactory extends ModelFactory {
	public WebClientSessionModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, WebClientSessionModel.TABLE);
	}

	public List<WebClientSessionModel> getAll() {
		return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
				null,
				null,
				null,
				null,
				null,
				WebClientSessionModel.COLUMN_LAST_CONNECTION + " DESC"));
	}

	@Nullable
	public WebClientSessionModel getById(int id) {
		return getFirst(
				"" + WebClientSessionModel.COLUMN_ID + " =?",
				new String[]{
						String.valueOf(id)
				});
	}

	@Nullable
	public WebClientSessionModel getByKey(byte[] key) {
		return getFirst(
				"" + WebClientSessionModel.COLUMN_KEY + " =x'" + Utils.byteArrayToHexString(key) + "'",
				null);
	}

	@Nullable
	public WebClientSessionModel getByKey256(String key256) {
		return getFirst(
				"" + WebClientSessionModel.COLUMN_KEY256 + " =?",
				new String[] {
						key256
				});
	}

	public List<WebClientSessionModel> convert(QueryBuilder queryBuilder,
	                                         String[] args,
	                                         String orderBy) {
		queryBuilder.setTables(this.getTableName());
		return convertList(queryBuilder.query(
				this.databaseService.getReadableDatabase(),
				null,
				null,
				args,
				null,
				null,
				orderBy));
	}

	private List<WebClientSessionModel> convertList(Cursor c) {
		List<WebClientSessionModel> result = new ArrayList<>();
		if(c != null) {
			try {
				while (c.moveToNext()) {
					result.add(this.convert(c));
				}
			}
			finally {
				c.close();
			}
		}
		return result;
	}

	private WebClientSessionModel convert(final Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final WebClientSessionModel model = new WebClientSessionModel();

			new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
				@Override
				public boolean next(CursorHelper cursorFactory) {
					model
							.setId(cursorFactory.getInt(WebClientSessionModel.COLUMN_ID))
							.setKey(cursorFactory.getBlob(WebClientSessionModel.COLUMN_KEY))
							.setPrivateKey(cursorFactory.getBlob(WebClientSessionModel.COLUMN_PRIVATE_KEY))
							.setCreated(cursorFactory.getDate(WebClientSessionModel.COLUMN_CREATED))
							.setLastConnection(cursorFactory.getDate(WebClientSessionModel.COLUMN_LAST_CONNECTION))
							.setClientDescription(cursorFactory.getString(WebClientSessionModel.COLUMN_CLIENT_DESCRIPTION))
							.setPersistent(cursorFactory.getBoolean(WebClientSessionModel.COLUMN_IS_PERSISTENT))
							.setLabel(cursorFactory.getString(WebClientSessionModel.COLUMN_LABEL))
							.setKey256(cursorFactory.getString(WebClientSessionModel.COLUMN_KEY256))
							.setSelfHosted(cursorFactory.getBoolean(WebClientSessionModel.COLUMN_SELF_HOSTED))
							.setSaltyRtcHost(cursorFactory.getString(WebClientSessionModel.COLUMN_SALTY_RTC_HOST))
							.setSaltyRtcPort(cursorFactory.getInt(WebClientSessionModel.COLUMN_SALTY_RTC_PORT))
							.setServerKey(cursorFactory.getBlob(WebClientSessionModel.COLUMN_SERVER_KEY))
							.setPushToken(cursorFactory.getString(WebClientSessionModel.COLUMN_PUSH_TOKEN));

				String stateString = cursorFactory.getString(WebClientSessionModel.COLUMN_STATE);
				if (!TestUtil.empty(stateString)) {
					model.setState(WebClientSessionModel.State.valueOf(stateString));
				}
				return false;
				}
			});
			return model;
		}

		return null;
	}

	public boolean createOrUpdate(WebClientSessionModel model) {

		boolean insert = model.getId() <= 0;

		ContentValues contentValues = new ContentValues();

		// When creating a new session model, set the "created" date
		if (insert && model.getCreated() == null) {
			model.setCreated(new Date());
		}

		contentValues.put(WebClientSessionModel.COLUMN_CREATED, model.getCreated() != null ? model.getCreated().getTime() : null);
		contentValues.put(WebClientSessionModel.COLUMN_LAST_CONNECTION, model.getLastConnection() != null ? model.getLastConnection().getTime() : null);
		contentValues.put(WebClientSessionModel.COLUMN_CLIENT_DESCRIPTION, model.getClientDescription());
		contentValues.put(WebClientSessionModel.COLUMN_KEY, model.getKey());
		contentValues.put(WebClientSessionModel.COLUMN_PRIVATE_KEY, model.getPrivateKey());
		contentValues.put(WebClientSessionModel.COLUMN_STATE, model.getState() != null ? model.getState().toString() : null);
		contentValues.put(WebClientSessionModel.COLUMN_IS_PERSISTENT, model.isPersistent());
		contentValues.put(WebClientSessionModel.COLUMN_LABEL, model.getLabel());
		contentValues.put(WebClientSessionModel.COLUMN_KEY256, model.getKey256());
		contentValues.put(WebClientSessionModel.COLUMN_SELF_HOSTED, model.isSelfHosted());
		contentValues.put(WebClientSessionModel.COLUMN_PROTOCOL_VERSION, 0);
		contentValues.put(WebClientSessionModel.COLUMN_SALTY_RTC_HOST, model.getSaltyRtcHost());
		contentValues.put(WebClientSessionModel.COLUMN_SALTY_RTC_PORT, model.getSaltyRtcPort());
		contentValues.put(WebClientSessionModel.COLUMN_SERVER_KEY, model.getServerKey());
		contentValues.put(WebClientSessionModel.COLUMN_PUSH_TOKEN, model.getPushToken());

		if(insert) {
			//never update key field
			//just set on update
			long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
			if (newId > 0) {
				model.setId((int)newId);
				return true;
			}
		} else {
			this.databaseService.getWritableDatabase().update(this.getTableName(),
					contentValues,
					WebClientSessionModel.COLUMN_ID + " =?",
					new String[]{
							String.valueOf(model.getId())
					});
			return true;
		}

		return false;
	}

	public int delete(WebClientSessionModel model) {
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				WebClientSessionModel.COLUMN_ID + " =?",
				new String[]{
						String.valueOf(model.getId())
				});
	}

	@Override
	public String[] getStatements() {
		return new String[] {
				"CREATE TABLE `" + WebClientSessionModel.TABLE + "` (" +
						"`" + WebClientSessionModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT , " +
						"`" + WebClientSessionModel.COLUMN_KEY + "` BLOB NULL," +
						"`" + WebClientSessionModel.COLUMN_KEY256 + "` VARCHAR NULL," +
						"`" + WebClientSessionModel.COLUMN_PRIVATE_KEY + "` BLOB NULL," +
						"`" + WebClientSessionModel.COLUMN_CREATED + "` BIGINT NULL," +
						"`" + WebClientSessionModel.COLUMN_LAST_CONNECTION + "` BIGINT NULL," +
						"`" + WebClientSessionModel.COLUMN_CLIENT_DESCRIPTION + "` VARCHAR, " +
						"`" + WebClientSessionModel.COLUMN_STATE + "` VARCHAR NOT NULL, " +
						"`" + WebClientSessionModel.COLUMN_IS_PERSISTENT + "` TINYINT NOT NULL DEFAULT 0," +
						"`" + WebClientSessionModel.COLUMN_LABEL + "` VARCHAR NULL," +
						"`" + WebClientSessionModel.COLUMN_SELF_HOSTED + "` TINYINT NOT NULL DEFAULT 0," +
						"`" + WebClientSessionModel.COLUMN_PROTOCOL_VERSION + "` INT NOT NULL," +
						"`" + WebClientSessionModel.COLUMN_SALTY_RTC_HOST + "` VARCHAR NOT NULL," +
						"`" + WebClientSessionModel.COLUMN_SALTY_RTC_PORT + "` INT NOT NULL," +
						"`" + WebClientSessionModel.COLUMN_SERVER_KEY + "` BLOB NULL," +
						"`" + WebClientSessionModel.COLUMN_PUSH_TOKEN + "` VARCHAR(255) NULL" +
						");",

				"CREATE UNIQUE INDEX `webClientSessionKey` ON `" + WebClientSessionModel.TABLE + "` ( `" + WebClientSessionModel.COLUMN_KEY + "` );",
				"CREATE UNIQUE INDEX `webClientSessionKey256` ON `" + WebClientSessionModel.TABLE + "` ( `" + WebClientSessionModel.COLUMN_KEY256 + "` );"
		};
	}

	@Nullable
	private WebClientSessionModel getFirst(String selection, String[] selectionArgs) {
		final Cursor cursor = this.databaseService.getReadableDatabase().query (
				this.getTableName(),
				null,
				selection,
				selectionArgs,
				null,
				null,
				null
		);

		if (cursor != null) {
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
}

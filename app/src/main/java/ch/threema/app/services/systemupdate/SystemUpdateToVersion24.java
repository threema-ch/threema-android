/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.services.systemupdate;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.UpdateSystemService;

/**
 * this script update the created/modified and postedat field to a long field (utc)
 * create a new field (field + "Utc")
 * update from old field with a +h of current timezone
 *
 * to run this script again (for testing) use this code:
 *
 *
 try {
 byte[] key = ThreemaApplication.getMasterKey().getKey();
 SystemUpdateToVersion24 x = new SystemUpdateToVersion24(serviceManager.getDatabaseService().getWritableDatabase("x\"" + Utils.byteArrayToHexString(key) + "\""));
 x.runDirectly();
 } catch (MasterKeyLockedException e) {
 e.printStackTrace();
 }


 */
public class SystemUpdateToVersion24 implements UpdateSystemService.SystemUpdate {
	private final SQLiteDatabase sqLiteDatabase;
	private String sqlTimezone = "";

	public SystemUpdateToVersion24(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;

		TimeZone currentTimeZone = TimeZone.getDefault();
		if(currentTimeZone != null) {
			Date now = new Date();
			int timezone = currentTimeZone.getOffset(now.getTime());
			this.sqlTimezone =
					(timezone < 0 ? "+" : "-") + String.valueOf(Math.abs(timezone));
		}
	}

	@Override
	public boolean runDirectly() {
		this.renameMessageTable("message");
		this.renameMessageTable("m_group_message");
		this.renameMessageTable("distribution_list_message");
		return true;
	}

	private void renameMessageTable(String msgTable) {

		String[] messageTableColumnNames = sqLiteDatabase.rawQuery("SELECT * FROM " + msgTable + " LIMIT 0", null).getColumnNames();

		String[] fields = new String[]{
				"createdAt",
				"modifiedAt",
				"postedAt"
		};

		//fix NULL modified at field (backup restore bug)
		boolean hasModifiedField = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
			@Override
			public boolean apply(@NonNull String type) {
				return type.equals("modifiedAt");
			}
		}) != null;

		if(hasModifiedField) {
			String query = "UPDATE " + msgTable + " SET modifiedAt=createdAt WHERE modifiedAt = '' OR modifiedAt IS NULL;";
			this.sqLiteDatabase.execSQL(query);
		}
		for (final String field : fields) {
			String query = "";
			final String fieldName = field + "Utc";
			boolean hasField = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
				@Override
				public boolean apply(@NonNull String type) {
					return type.equals(fieldName);
				}
			}) != null;


			if (!hasField) {
				sqLiteDatabase.execSQL("ALTER TABLE " + msgTable + " ADD COLUMN " + fieldName + " LONG DEFAULT 0");
			}

			//ask again
			messageTableColumnNames = sqLiteDatabase.rawQuery("SELECT * FROM " + msgTable + " LIMIT 0", null).getColumnNames();

			boolean hasFieldOld = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
				@Override
				public boolean apply(@NonNull String type) {
					return type.equals(field);
				}
			}) != null;

			boolean hasFieldNew = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
				@Override
				public boolean apply(@NonNull String type) {
					return type.equals(fieldName);
				}
			}) != null;

			//only update table if old and new field exists (hack to fix exception in release 1.61
			if(hasFieldOld && hasFieldNew) {
				query += (query.length() > 0 ? ", " : "") +
						fieldName + "=((strftime('%s', DATETIME(" + field + "))*1000)" + this.sqlTimezone + ")" ;

				if (query != null && query.length() > 0) {
					query = "UPDATE " + msgTable + " SET " + query + " WHERE " + field + " != '';";
					this.sqLiteDatabase.execSQL(query);
				}
			}

		}


	}

	@Override
	public boolean runASync() {
		return true;
	}



	@Override
	public String getText() {
		return "version 24";
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.utils.LogUtil;

abstract class UpdateToVersion {
	private static final Logger logger = LoggerFactory.getLogger(UpdateToVersion.class);

	protected boolean fieldExist(SQLiteDatabase sqLiteDatabase, final String table, final String fieldName)
	{
		boolean success = false;

		if(sqLiteDatabase != null
				&& table != null && table.length() > 0
				&& fieldName != null && fieldName.length() > 0) {
			Cursor cursor = null;
			try {
				 cursor = sqLiteDatabase.rawQuery("SELECT * FROM "+table+" LIMIT 0", null);
				if (cursor != null) {
					String[] messageTableColumnNames = cursor.getColumnNames();
					cursor.close();

					success = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
						@Override
						public boolean apply(@NonNull String type) {
							return type.equals(fieldName);
						}
					}) != null;

				}
				}
			catch (Exception x) {
				logger.error("Exception", x);
				success = false;
			}
			finally {
				if(cursor != null) {
					//always close the cursor
					cursor.close();
				}
			}

		}
		return success;
	}
}


/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.app.mediaattacher.data;


import android.content.Context;

import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SupportFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import ch.threema.app.ThreemaApplication;
import ch.threema.localcrypto.MasterKeyLockedException;

@Database(
	entities = {LabeledMediaItemEntity.class, FailedMediaItemEntity.class},
	version = 2,
	exportSchema = false
)
@TypeConverters({ImageLabelListConverter.class})
public abstract class MediaItemsRoomDatabase extends RoomDatabase {
	private static final Logger logger = LoggerFactory.getLogger(MediaItemsRoomDatabase.class);

	public static final String DATABASE_NAME = "media_items.db";

	public abstract LabeledMediaItemsDAO mediaItemsDAO();
	public abstract FailedMediaItemsDAO failedMediaItemsDAO();

	private static volatile MediaItemsRoomDatabase db;

	static final Migration MIGRATION_1_2 = new Migration(1, 2) {
		@Override
		public void migrate(SupportSQLiteDatabase database) {
			database.execSQL("CREATE TABLE `failed_media_items` (`id` INTEGER NOT NULL, "
				+ "`timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))");
		}
	};

	public static MediaItemsRoomDatabase getDatabase(final Context context) throws MasterKeyLockedException, SQLiteException {
		if (db == null) {
			synchronized (MediaItemsRoomDatabase.class) {
				if (db == null) {
					logger.info("Creating database");
					SupportFactory factory;
					try {
						factory = new SupportFactory(ThreemaApplication.getMasterKey().getKey(), null, false);
					} catch (MasterKeyLockedException e) {
						throw new MasterKeyLockedException("Masterkey locked, cannot get database");
					}
					db = Room
						.databaseBuilder(context.getApplicationContext(), MediaItemsRoomDatabase.class, DATABASE_NAME)
						.addMigrations(MIGRATION_1_2)
						.openHelperFactory(factory)
						.build();
				}
			}
		}
		return db;
	}

	/**
	 * Close and destroy the database instance.
	 */
	public static void destroyInstance() {
		if (db != null) {
			synchronized (MediaItemsRoomDatabase.class) {
				if (db != null) {
					if (db.isOpen()) {
						logger.info("Closing database");
						db.close();
					}
					db = null;
				}
			}
		}
	}
}

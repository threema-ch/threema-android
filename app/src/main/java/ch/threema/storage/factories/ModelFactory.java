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

import net.zetetic.database.DatabaseUtils;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.slf4j.Logger;

import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.ColumnIndexCache;
import ch.threema.storage.DatabaseServiceNew;

public abstract class ModelFactory {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ModelFactory");

	protected static final String NO_RECORD_MSG = "Update of model failed, no records matched for id=";

	final DatabaseServiceNew databaseService;
	private final String tableName;
	protected final ColumnIndexCache columnIndexCache = new ColumnIndexCache();

	ModelFactory(DatabaseServiceNew databaseService, String tableName) {
		this.databaseService = databaseService;
		this.tableName = tableName;
		logger.debug("instantiate {}", getClass());
	}

	/**
	 * Returns the table and index creation statements for a model.
	 *
	 * @return list of SQL statements
	 */
	public abstract String[] getStatements();

	public void dropTable() {
		this.getWritableDatabase().execSQL("DROP TABLE IF EXISTS " + this.getTableName());
	}

	public final void deleteAll() {
		this.getWritableDatabase().execSQL("DELETE FROM " + this.getTableName());
	}

	public final long count() {
		return DatabaseUtils.queryNumEntries(this.getReadableDatabase(), this.getTableName());
	}

	public String getTableName() {
		return this.tableName;
	}

	protected SQLiteDatabase getReadableDatabase() {
		return this.databaseService.getReadableDatabase();
	}

	protected SQLiteDatabase getWritableDatabase() {
		return this.databaseService.getWritableDatabase();
	}

	protected ColumnIndexCache getColumnIndexCache() {
		return this.columnIndexCache;
	}
}

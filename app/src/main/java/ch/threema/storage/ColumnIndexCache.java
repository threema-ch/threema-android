/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2023 Threema GmbH
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

package ch.threema.storage;

import net.sqlcipher.Cursor;

import androidx.collection.SimpleArrayMap;

public class ColumnIndexCache {
	private final SimpleArrayMap<String, Integer> indexMap = new SimpleArrayMap<>();

	public int getColumnIndex(Cursor cursor, String columnName) {
		synchronized (indexMap) {
			if (!indexMap.containsKey(columnName)) {
				int index = cursor.getColumnIndex(columnName);
				indexMap.put(columnName, index);
				return index;
			}
			return indexMap.get(columnName);
		}
	}

	public void clear() {
		synchronized (indexMap) {
			indexMap.clear();
		}
	}
}

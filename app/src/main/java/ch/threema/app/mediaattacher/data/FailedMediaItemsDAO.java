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

import java.util.List;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface FailedMediaItemsDAO {

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insert(FailedMediaItemEntity mediaItem);

	@Delete
	void delete(FailedMediaItemEntity mediaItem);

	@Query("SELECT * FROM failed_media_items WHERE id = :id LIMIT 1")
	FailedMediaItemEntity get(int id);

	@Query("SELECT COUNT(*) FROM failed_media_items")
	int getRowCount();

	@Query("SELECT * FROM failed_media_items ORDER BY id ASC")
	List<FailedMediaItemEntity> getAllItemsByAscIdOrder();
}

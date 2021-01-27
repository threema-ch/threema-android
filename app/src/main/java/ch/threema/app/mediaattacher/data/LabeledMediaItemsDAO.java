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

import java.util.ArrayList;
import java.util.List;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface LabeledMediaItemsDAO {

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insert(LabeledMediaItemEntity mediaItem);

	@Query("INSERT into media_items_table (id, labels) VALUES (:id, :labelList)")
	void insert(int id, ArrayList<String> labelList);

	@Query("DELETE FROM media_items_table")
	void deleteAll();

	@Query("DELETE FROM media_items_table WHERE id = :id")
	void deleteMediaItemById(int id);

	@Query("SELECT * FROM media_items_table")
	List<LabeledMediaItemEntity> getAll();

	@Query("SELECT * FROM media_items_table ORDER BY id ASC")
	List<LabeledMediaItemEntity> getAllItemsByAscIdOrder();

	@Query("SELECT labels from media_items_table WHERE id = :id")
	List<String> getMediaItemLabels(int id);

	@Query("UPDATE media_items_table SET labels = :labels WHERE id = :id")
	void setLabels(List<String> labels, int id);

	@Query("SELECT COUNT(*) FROM media_items_table")
	int getRowCount();
}

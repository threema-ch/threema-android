/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.data.storage

import android.database.sqlite.SQLiteException
import ch.threema.data.repositories.EditHistoryEntryCreateException
import ch.threema.storage.models.AbstractMessageModel

interface EditHistoryDao {

    /**
     * Insert a new edit history entry
     *
     * @param entry The entry to add for the edit history
     * @param messageModel The message referenced by the edit history entry
     *
     * @throws SQLiteException if insertion fails due to a conflict
     * @throws EditHistoryEntryCreateException if inserting the [DbEditHistoryEntry] in the database failed
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    fun create(entry: DbEditHistoryEntry, messageModel: AbstractMessageModel): Long

    /**
     * Delete all edit history entries referencing the specified message id
     */
    fun deleteAllByMessageUid(messageUid: String)

    /**
     * Find all edit history entries referencing the specified message uid
     */
    fun findAllByMessageUid(messageUid: String): List<DbEditHistoryEntry>
}

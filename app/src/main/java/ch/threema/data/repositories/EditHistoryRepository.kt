/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.managers.CoreServiceManager
import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.EditHistoryListModel
import ch.threema.data.models.toDataType
import ch.threema.data.storage.DbEditHistoryEntry
import ch.threema.data.storage.EditHistoryDao
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageType
import java.util.Date

private val logger = getThreemaLogger("EditHistoryRepository")

@SessionScoped
class EditHistoryRepository(
    private val cache: ModelTypeCache<String, EditHistoryListModel>,
    private val editHistoryDao: EditHistoryDao,
    private val coreServiceManager: CoreServiceManager,
) {
    fun getByMessageUid(messageUid: String): EditHistoryListModel? {
        return cache.getOrCreate(messageUid) {
            logger.debug("Load edit history for message {} from database", messageUid)
            EditHistoryListModel(
                editHistoryDao.findAllByMessageUid(messageUid)
                    .map { it.toDataType() },
                coreServiceManager,
            )
        }
    }

    /**
     * Inserts a [DbEditHistoryEntry] into the db.
     * Call this before saving the edited message so the old message is saved in the history.
     *
     * @param message The message model to create an edit history entry from.
     *
     * @throws EditHistoryEntryCreateException if inserting the [DbEditHistoryEntry] in the database failed
     * @throws IllegalStateException if the [message] is not valid to create a [DbEditHistoryEntry] from
     *
     */
    fun createEntry(message: AbstractMessageModel) {
        val oldText: String? = when (message.type) {
            MessageType.TEXT -> message.body
            MessageType.FILE -> message.caption
            else -> throw IllegalStateException("Unhandled messageType ${message.type}")
        }

        synchronized(this) {
            try {
                if (message.editedAt == null && message.createdAt == null) {
                    logger.error("Failed to get valid date to create the edit history entry. Fallback to current date.")
                }
                val historyEntry = DbEditHistoryEntry(
                    uid = 0,
                    messageUid = message.uid!!,
                    messageId = message.id,
                    text = oldText,
                    editedAt = message.editedAt ?: message.createdAt ?: Date(),
                )

                val uid = editHistoryDao.create(historyEntry, message)

                val historyEntryWithUid = historyEntry.copy(uid = uid.toInt())
                cache.get(message.uid!!)?.addEntry(historyEntryWithUid.toDataType())
            } catch (exception: SQLiteException) {
                throw EditHistoryEntryCreateException(exception)
            }
        }
    }

    fun deleteByMessageUid(messageUid: String) {
        logger.debug("Delete by message uid {}", messageUid)
        editHistoryDao.deleteAllByMessageUid(messageUid)
        cache.get(messageUid)?.clear()
        cache.remove(messageUid)
    }
}

class EditHistoryEntryCreateException(e: Exception) :
    ThreemaException("Failed to create the edit history entry in the db", e)

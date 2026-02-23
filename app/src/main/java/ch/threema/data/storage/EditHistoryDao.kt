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

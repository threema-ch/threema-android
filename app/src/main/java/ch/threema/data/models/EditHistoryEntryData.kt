package ch.threema.data.models

import ch.threema.data.storage.DbEditHistoryEntry
import java.util.Date

data class EditHistoryEntryData(
    /** unique id. */
    @JvmField val uid: Int,
    /** The id of the edited message referencing the db row. */
    @JvmField val messageUid: String,
    /** The former text of the edited message. */
    @JvmField val text: String?,
    /** Timestamp when the message was edited and hence the entry created. */
    @JvmField val editedAt: Date,
) {
    fun uid() = uid

    fun messageUid() = messageUid

    fun text() = text

    fun editedAt() = editedAt
}

fun DbEditHistoryEntry.toDataType() = EditHistoryEntryData(
    uid = this.uid,
    messageUid = this.messageUid,
    text = this.text,
    editedAt = this.editedAt,
)

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
    @JvmField val editedAt: Date
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
    editedAt = this.editedAt
)

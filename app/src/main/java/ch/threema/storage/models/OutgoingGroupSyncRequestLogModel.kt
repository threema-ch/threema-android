/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.storage.models

import java.util.Date

/**
 * This model is used to track at what time a group sync request has been sent to what group. This
 * is required to prevent that group sync requests are sent too often per group.
 */
data class OutgoingGroupSyncRequestLogModel(
    val id: Int,
    val apiGroupId: String,
    val creatorIdentity: String,
    val lastRequest: Date?,
) {

    override fun toString(): String {
        return "m_group_request_sync_log.id = " + this.id
    }

    companion object {
        const val TABLE: String = "m_group_request_sync_log"
        const val COLUMN_ID: String = "id"
        const val COLUMN_API_GROUP_ID: String = "apiGroupId"
        const val COLUMN_CREATOR_IDENTITY: String = "creatorIdentity"
        const val COLUMN_LAST_REQUEST: String = "lastRequest"
    }
}

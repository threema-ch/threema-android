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

package ch.threema.storage.models

import ch.threema.domain.types.Identity

/**
 * This model is used to track at what time a group sync request has been answered. This is required
 * to limit the number of answered group sync requests per group and sender of the request.
 */
class IncomingGroupSyncRequestLogModel(
    /**
     * The database id of the group.
     */
    val groupId: Long,
    /**
     * The identity of the sender of the group sync request.
     */
    val senderIdentity: Identity,
    /**
     * The time when the last group request from [senderIdentity] in group [groupId] has been
     * answered.
     */
    var lastHandledRequest: Long,
) {
    companion object {
        const val TABLE = "incoming_group_sync_request_log"

        const val COLUMN_GROUP_ID = "groupId"
        const val COLUMN_SENDER_IDENTITY = "senderIdentity"
        const val COLUMN_LAST_HANDLED_REQUEST = "lastHandledRequest"
    }
}

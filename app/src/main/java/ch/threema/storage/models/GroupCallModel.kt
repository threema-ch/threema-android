/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

class GroupCallModel internal constructor(
    val protocolVersion: Int,
    val callId: String,
    val groupId: Int,
    val sfuBaseUrl: String,
    val gck: String,
    val startedAt: Long,
    val processedAt: Long,
) {
    companion object {
        const val TABLE = "group_call"

        const val COLUMN_CALL_ID = "callId"
        const val COLUMN_GROUP_ID = "groupId"
        const val COLUMN_SFU_BASE_URL = "sfuBaseUrl"
        const val COLUMN_GCK = "gck"
        const val COLUMN_PROTOCOL_VERSION = "protocolVersion"
        const val COLUMN_STARTED_AT = "startedAt"
        const val COLUMN_PROCESSED_AT = "processedAt"
    }

    fun getProtocolVersionUnsigned(): UInt = protocolVersion.toUInt()

    fun getStartedAtUnsigned(): ULong = startedAt.toULong()

    fun getProcessedAtUnsigned(): ULong = processedAt.toULong()
}

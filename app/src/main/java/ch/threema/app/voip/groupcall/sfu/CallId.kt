/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu

import ch.threema.app.voip.groupcall.CryptoCallUtils
import ch.threema.common.toHexString
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData
import ch.threema.storage.models.GroupModel
import java.nio.charset.StandardCharsets

data class CallId(val bytes: ByteArray) {
    companion object {
        fun create(group: GroupModel, callStartData: GroupCallStartData): CallId {
            return CallId(computeCallId(group, callStartData))
        }

        private fun computeCallId(group: GroupModel, callStartData: GroupCallStartData): ByteArray =
            CryptoCallUtils.gcBlake2b256(
                salt = CryptoCallUtils.SALT_CALL_ID,
                data = (
                    group.creatorIdentity.toByteArray(StandardCharsets.UTF_8) +
                        group.apiGroupId.groupId +
                        callStartData.protocolVersion.toByte() +
                        callStartData.gck +
                        callStartData.sfuBaseUrl.encodeToByteArray()
                    ),
            )
    }

    val hex: String by lazy {
        bytes.toHexString()
    }

    override fun toString(): String {
        return hex
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallId) return false

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}

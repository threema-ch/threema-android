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

import ch.threema.app.voip.groupcall.PERSONAL
import ch.threema.app.voip.groupcall.SALT_CALL_ID
import ch.threema.base.utils.Utils
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData
import ch.threema.storage.models.GroupModel
import java.nio.charset.StandardCharsets
import ove.crypto.digest.Blake2b

data class CallId(val bytes: ByteArray) {
    companion object {
        fun create(group: GroupModel, callStartData: GroupCallStartData): CallId {
            return CallId(computeCallId(group, callStartData))
        }

        private fun computeCallId(group: GroupModel, callStartData: GroupCallStartData): ByteArray {
            val params = Blake2b.Param()
            params.digestLength = ProtocolDefines.GC_CALL_ID_LENGTH
            params.setSalt(SALT_CALL_ID.encodeToByteArray())
            params.setPersonal(PERSONAL.encodeToByteArray())
            val digest = Blake2b.Digest.newInstance(params)
            digest.update(group.creatorIdentity.toByteArray(StandardCharsets.UTF_8))
            digest.update(group.apiGroupId.groupId)
            digest.update(callStartData.protocolVersion.toByte())
            digest.update(callStartData.gck)
            digest.update(callStartData.sfuBaseUrl.encodeToByteArray())
            return digest.digest()
        }
    }

    val hex: String by lazy {
        Utils.byteArrayToHexString(bytes)
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

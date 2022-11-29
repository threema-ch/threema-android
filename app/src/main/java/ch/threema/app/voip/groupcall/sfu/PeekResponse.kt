/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

import ch.threema.protobuf.groupcall.SfuHttpResponse

typealias PeekResponse = SfuResponse<PeekResponseBody>

data class PeekResponseBody (
    val startedAt: ULong,
    val maxParticipants: UInt,
    val encryptedCallState: ByteArray?
) {
    companion object {
        fun fromSfuResponseBytes(bytes: ByteArray): PeekResponseBody {
            return SfuHttpResponse.Peek.parseFrom(bytes)
                .let { PeekResponseBody(it.startedAt.toULong(), it.maxParticipants.toUInt(), it.encryptedCallState?.toByteArray()) }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeekResponseBody) return false

        if (startedAt != other.startedAt) return false
        if (maxParticipants != other.maxParticipants) return false
        if (encryptedCallState != null) {
            if (other.encryptedCallState == null) return false
            if (!encryptedCallState.contentEquals(other.encryptedCallState)) return false
        } else if (other.encryptedCallState != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = startedAt.hashCode()
        result = 31 * result + maxParticipants.hashCode()
        result = 31 * result + (encryptedCallState?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "PeekResponseBody(startedAt=$startedAt, maxParticipants=$maxParticipants, encryptedCallState=${encryptedCallState?.let { "*********" }})"
    }
}

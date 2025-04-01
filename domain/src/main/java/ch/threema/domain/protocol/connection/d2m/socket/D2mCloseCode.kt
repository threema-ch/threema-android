/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.domain.protocol.connection.d2m.socket

data class D2mCloseCode(val code: Int, val reason: String) {
    companion object {
        /*
         * WebSocket internal close codes (1xxx)
         */
        const val NORMAL = 1000
        const val SERVER_SHUTTING_DOWN = 1001
        const val INTERNAL_SERVER_ERROR = 1011

        /*
         * Chat server close codes (400x and 410x)
         */
        const val CSP_CONNECTION_CLOSED = 4000
        const val CSP_CONNECTION_COULD_NOT_BE_ESTABLISHED = 4001
        const val CSP_INTERNAL_SERVER_ERROR = 4009

        /*
         * Mediator close codes (40[1-9]x and 41[1-9]x)
         */
        const val D2M_PROTOCOL_ERROR = 4010
        const val D2M_TRANSACTION_TTL_EXCEEDED = 4011

        // Unknown message acked
        const val D2M_UNEXPECTED_ACK = 4012

        // Client idle timeout exceeded
        const val D2M_CLIENT_TIMEOUT = 4013
        const val D2M_UNSUPPORTED_PROTOCOL_VERSION = 4110
        const val D2M_DEVICE_LIMIT_REACHED = 4111

        // Duplicate connection (i.e. the same device reconnected, terminating the previous connection)
        const val D2M_DUPLICATE_CONNECTION = 4112

        // Dropped by other device
        const val D2M_DEVICE_DROPPED = 4113

        // Dropped by server because the reflection queue length limit was reached
        const val D2M_REFLECTION_QUEUE_LIMIT_REACHED = 4114

        // Device slot state mismatch
        const val D2M_EXPECTED_DEVICE_SLOT_MISMATCH = 4115
    }

    fun isReconnectAllowed(): Boolean {
        return code < 4100 || code >= 4200
    }
}

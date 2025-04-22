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

package ch.threema.domain.protocol.rendezvous

import kotlinx.coroutines.Deferred

internal interface RendezvousPath {
    val pid: UInt
    val closedSignal: Deferred<Unit>

    suspend fun connect()

    /**
     * Close this path. This must close all underlying connections.
     */
    fun close()

    /**
     * Write [bytes] to this path. This suspends until the bytes are sent.
     *
     * @throws java.io.IOException if writing is not possible
     */
    suspend fun write(bytes: ByteArray)

    /**
     * Read the next chunk of bytes from this path.
     *
     * @throws java.io.IOException if the path is closed while waiting for bytes
     */
    suspend fun read(): ByteArray
}

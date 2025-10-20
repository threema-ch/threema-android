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

package ch.threema.app.multidevice.linking

import ch.threema.common.toHexString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

sealed interface DeviceLinkingStatus {
    class Connected(
        private val rph: ByteArray,
    ) : DeviceLinkingStatus {
        /**
         *  This [Deferred] will complete when the emoji verification was successful
         */
        private val rphConfirmedSignal = CompletableDeferred<Unit>()

        val emojiIndices: Triple<Int, Int, Int> by lazy {
            Triple(
                (rph[0].toUByte() % 128U).toInt(),
                (rph[1].toUByte() % 128U).toInt(),
                (rph[2].toUByte() % 128U).toInt(),
            )
        }

        suspend fun awaitRendezvousPathConfirmation() {
            rphConfirmedSignal.await()
        }

        fun confirmRendezvousPath() {
            rphConfirmedSignal.complete(Unit)
        }

        // TODO(ANDR-2487): Remove if not needed
        fun declineRendezvousPath() {
            rphConfirmedSignal.completeExceptionally(
                DeviceLinkingException("Rendezvous path declined (rph=${rph.toHexString()})"),
            )
        }
    }

    data class Failed(val throwable: Throwable?) : DeviceLinkingStatus

    data object Completed : DeviceLinkingStatus
}

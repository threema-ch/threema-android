/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.usecases

import ch.threema.app.listeners.ContactTypingListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ContactService
import ch.threema.domain.types.Identity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 *  Creates a *cold* [Flow] that publishes any changes to the currently typing contact identities.
 */
class WatchTypingIdentitiesUseCase(
    private val contactService: ContactService,
) {

    fun call(): Flow<Set<Identity>> = callbackFlow {
        fun trySendCurrent() {
            trySend(contactService.typingIdentities)
        }

        trySendCurrent()

        val contactTypingListener = ContactTypingListener { _, _ -> trySendCurrent() }

        ListenerManager.contactTypingListeners.add(contactTypingListener)

        awaitClose {
            ListenerManager.contactTypingListeners.remove(contactTypingListener)
        }
    }
}

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

package ch.threema.localcrypto.models

sealed interface MasterKeyEvent {
    /**
     * Remote secret protection has been added to the master key and is now in use.
     */
    data object RemoteSecretActivated : MasterKeyEvent

    /**
     * Remote secret protection has been removed from the master key and isn't in use anymore.
     *
     * @param remoteSecretAuthenticationToken the token associated with the now obsolete remote secret.
     * It should be used to delete the remote secret from the server.
     */
    data class RemoteSecretDeactivated(
        val remoteSecretAuthenticationToken: RemoteSecretAuthenticationToken,
    ) : MasterKeyEvent
}

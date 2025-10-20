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

package ch.threema.app.startup

/**
 * Represents an error that occurred during the app's startup sequence.
 *
 * @param isTemporary Indicates whether the error is transient and that the startup sequence can be started over to try and recover from it.
 * False indicates that the app is no longer usable and must be closed (after informing the user).
 */
sealed class AppStartupError(val isTemporary: Boolean = false) {
    /**
     * An unexpected, likely irrecoverable error that occurred while the app was starting up.
     *
     * @param code A short error code, which will be shown on the error screen.
     * Meaningless to the user but can be useful for support and devs to identify the cause of the error.
     */
    data class Unexpected(val code: String) : AppStartupError()

    /**
     * Indicates that RS is enabled, but an admin has revoked the user's access, making the app unusable.
     */
    data object BlockedByAdmin : AppStartupError()

    /**
     * Indicates that a (temporary) error occurred while creating, fetching or deleting the remote secret.
     */
    data object FailedToFetchRemoteSecret : AppStartupError(isTemporary = true)
}

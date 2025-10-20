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

package ch.threema.localcrypto

import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.localcrypto.models.RemoteSecret
import kotlinx.coroutines.Deferred

/**
 * Wrapper around libthreema's RemoteSecretMonitorProtocol, which allows to fetch and monitor the remote secret.
 */
interface RemoteSecretLoop : AutoCloseable {
    val remoteSecret: Deferred<RemoteSecret>

    /**
     * Fetches and monitors the remote secret. Will run forever or until an error occurs, in which case an exception is thrown.
     * If an error occurs, [close] must be called and [run] must not be called again.
     */
    @Throws(RemoteSecretMonitorException::class, BlockedByAdminException::class)
    suspend fun run()
}

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

import ch.threema.common.awaitNonNull
import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretParameters
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class RemoteSecretMonitor(
    private val remoteSecretClient: RemoteSecretClient,
) {
    private val remoteSecretFlow = MutableStateFlow<RemoteSecret?>(null)

    @Throws(RemoteSecretMonitorException::class, BlockedByAdminException::class)
    suspend fun monitor(baseUrl: String, parameters: RemoteSecretParameters): Unit = coroutineScope {
        try {
            remoteSecretClient.createRemoteSecretLoop(baseUrl, parameters).use { loop ->
                launch {
                    remoteSecretFlow.value = loop.remoteSecret.await()
                }
                loop.run()
            }
        } finally {
            remoteSecretFlow.value = null
        }
    }

    suspend fun awaitRemoteSecretAndClear(): RemoteSecret {
        val remoteSecret = remoteSecretFlow.awaitNonNull()
        remoteSecretFlow.value = null
        return remoteSecret
    }
}

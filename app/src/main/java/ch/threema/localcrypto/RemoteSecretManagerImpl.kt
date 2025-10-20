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

import ch.threema.base.ThreemaException
import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.InvalidCredentialsException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretCreationResult
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import java.io.IOException

class RemoteSecretManagerImpl(
    private val remoteSecretClient: RemoteSecretClient,
    private val shouldUseRemoteSecretProtection: () -> Boolean,
    private val remoteSecretMonitor: RemoteSecretMonitor,
    private val getWorkServerBaseUrl: () -> String,
) : RemoteSecretManager {

    override fun checkRemoteSecretProtection(lockData: MasterKeyState.WithRemoteSecret?): RemoteSecretProtectionCheckResult {
        val shouldUseRemoteSecretProtection = shouldUseRemoteSecretProtection()
        val usesRemoteSecretProtection = lockData != null
        return when {
            shouldUseRemoteSecretProtection && !usesRemoteSecretProtection -> RemoteSecretProtectionCheckResult.SHOULD_ACTIVATE
            !shouldUseRemoteSecretProtection && usesRemoteSecretProtection -> RemoteSecretProtectionCheckResult.SHOULD_DEACTIVATE
            else -> RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED
        }
    }

    @Throws(ThreemaException::class, InvalidCredentialsException::class, IOException::class)
    override suspend fun createRemoteSecret(clientParameters: RemoteSecretClientParameters): RemoteSecretCreationResult =
        try {
            remoteSecretClient.createRemoteSecret(
                parameters = clientParameters,
            )
        } catch (e: RemoteSecretClient.RemoteSecretClientException) {
            throw ThreemaException("Failed to create remote secret", e)
        }

    @Throws(ThreemaException::class, InvalidCredentialsException::class, IOException::class)
    override suspend fun deleteRemoteSecret(
        clientParameters: RemoteSecretClientParameters,
        authenticationToken: RemoteSecretAuthenticationToken,
    ) {
        try {
            remoteSecretClient.deleteRemoteSecret(
                parameters = clientParameters,
                authenticationToken = authenticationToken,
            )
        } catch (e: RemoteSecretClient.RemoteSecretClientException) {
            throw ThreemaException("Failed to delete remote secret", e)
        }
    }

    @Throws(RemoteSecretMonitorException::class, BlockedByAdminException::class)
    override suspend fun monitorRemoteSecret(parameters: RemoteSecretParameters) {
        remoteSecretMonitor.monitor(
            baseUrl = getWorkServerBaseUrl(),
            parameters = parameters,
        )
    }

    override suspend fun awaitRemoteSecretAndClear() = remoteSecretMonitor.awaitRemoteSecretAndClear()
}

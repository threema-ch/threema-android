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
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretCreationResult
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import java.io.IOException

interface RemoteSecretManager {
    /**
     * Check if remote secret protection needs to be activated, deactivated, or kept as it is.
     */
    fun checkRemoteSecretProtection(lockData: MasterKeyState.WithRemoteSecret?): RemoteSecretProtectionCheckResult

    @Throws(ThreemaException::class, IOException::class)
    suspend fun createRemoteSecret(clientParameters: RemoteSecretClientParameters): RemoteSecretCreationResult

    /**
     * Deletes a remote secret from the server.
     * Must only be called after the master key is no longer encrypted with the secret, as otherwise
     * we might lose all the user's data.
     */
    @Throws(ThreemaException::class, InvalidCredentialsException::class, IOException::class)
    suspend fun deleteRemoteSecret(clientParameters: RemoteSecretClientParameters, authenticationToken: RemoteSecretAuthenticationToken)

    @Throws(RemoteSecretMonitorException::class, BlockedByAdminException::class)
    suspend fun monitorRemoteSecret(parameters: RemoteSecretParameters)

    /**
     * Waits for the remote secret to become available and returns it.
     * In order for the remote secret to become available, [monitorRemoteSecret] needs to be called.
     * Once the remote secret is returned, it will not be available at least until [monitorRemoteSecret] is called again.
     */
    suspend fun awaitRemoteSecretAndClear(): RemoteSecret
}

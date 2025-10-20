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

import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretCreationResult
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope

class NoOpRemoteSecretManagerImpl : RemoteSecretManager {
    override fun checkRemoteSecretProtection(lockData: MasterKeyState.WithRemoteSecret?) =
        RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED

    override suspend fun createRemoteSecret(clientParameters: RemoteSecretClientParameters): RemoteSecretCreationResult {
        error("must not be called")
    }

    override suspend fun deleteRemoteSecret(
        clientParameters: RemoteSecretClientParameters,
        authenticationToken: RemoteSecretAuthenticationToken,
    ) {
        error("must not be called")
    }

    override suspend fun monitorRemoteSecret(parameters: RemoteSecretParameters) = coroutineScope {
        cancel("nothing to monitor")
    }

    override suspend fun awaitRemoteSecretAndClear(): RemoteSecret {
        awaitCancellation()
    }
}

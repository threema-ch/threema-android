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

package ch.threema.app.apptaskexecutor.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.toCryptographicByteArray
import ch.threema.domain.models.UserCredentials
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val logger = LoggingUtil.getThreemaLogger("RemoteSecretDeleteStepsTask")

class RemoteSecretDeleteStepsTask(
    private val serviceManagerProvider: ServiceManagerProvider,
    private val appStartupMonitor: AppStartupMonitor,
    private val masterKeyManager: MasterKeyManager,
    private val authenticationToken: RemoteSecretAuthenticationToken,
) : PersistableAppTask {

    override suspend fun run() {
        // As app tasks may be run before the app has successfully started up, we need to wait as this task requires the master key manager.
        appStartupMonitor.awaitAll()

        var attempt = 0
        while (true) {
            attempt++
            // Note that we need to collect the client parameters in every loop iteration as they might have changed.
            val serviceManager = serviceManagerProvider.awaitServiceManager()
            val clientParameters = getRemoteSecretClientParameters(serviceManager) ?: run {
                // This will end this task and remove it from persistent storage.
                error("Cannot delete remote secret due to missing client parameters")
            }
            try {
                logger.info("Deleting remote secret from server (attempt {})", attempt)
                masterKeyManager.deleteRemoteSecret(clientParameters, authenticationToken)
                break
            } catch (e: Exception) {
                logger.error("Failed to delete remote secret", e)
                delay(calculateRetryDelay(attempt))
            }
        }
    }

    private fun calculateRetryDelay(attempt: Int) =
        (1.5.pow(attempt.coerceAtMost(10))).toInt() * 5.seconds

    override fun serialize() = RemoteSecretDeleteStepsTaskData(
        authenticationToken = authenticationToken.value,
    )

    private fun getRemoteSecretClientParameters(serviceManager: ServiceManager): RemoteSecretClientParameters? {
        return RemoteSecretClientParameters(
            workServerBaseUrl = serviceManager.serverAddressProviderService
                .serverAddressProvider
                .getWorkServerUrl(serviceManager.isIpv6Preferred)
                ?: return null,
            userIdentity = serviceManager.userService.identity
                ?: return null,
            clientKey = serviceManager.userService.privateKey
                ?.toCryptographicByteArray()
                ?: return null,
            credentials = serviceManager.licenseService.loadCredentials() as? UserCredentials
                ?: return null,
        )
    }

    @Serializable
    class RemoteSecretDeleteStepsTaskData(
        private val authenticationToken: ByteArray,
    ) : AppTaskData, KoinComponent {
        override fun createTask() = RemoteSecretDeleteStepsTask(
            serviceManagerProvider = get(),
            appStartupMonitor = get(),
            masterKeyManager = get(),
            authenticationToken = RemoteSecretAuthenticationToken(authenticationToken),
        )
    }
}

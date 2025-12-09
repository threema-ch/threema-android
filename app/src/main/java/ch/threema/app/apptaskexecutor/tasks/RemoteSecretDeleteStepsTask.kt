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

import ch.threema.app.di.injectNonBinding
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.stores.IdentityProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.toCryptographicByteArray
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.protocol.ServerAddressProvider
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
import org.koin.core.component.inject

private val logger = getThreemaLogger("RemoteSecretDeleteStepsTask")

class RemoteSecretDeleteStepsTask(
    private val authenticationToken: RemoteSecretAuthenticationToken,
) : PersistableAppTask, KoinComponent {

    private val appStartupMonitor: AppStartupMonitor by inject()
    private val masterKeyManager: MasterKeyManager by inject()
    private val identityProvider: IdentityProvider by inject()
    private val serverAddressProvider: ServerAddressProvider by injectNonBinding()
    private val preferenceService: PreferenceService by injectNonBinding()
    private val userService: UserService by injectNonBinding()
    private val licenseService: LicenseService<*> by injectNonBinding()

    override suspend fun run() {
        var attempt = 0
        while (true) {
            // As app tasks may be run before the app has successfully started up, or might still try to run after the app has been locked,
            // we need to wait here as this task has session-scoped dependencies.
            appStartupMonitor.awaitAll()

            attempt++
            try {
                // Note that we need to collect the client parameters in every loop iteration as they might have changed.
                val clientParameters = getRemoteSecretClientParameters() ?: run {
                    // This will end this task and remove it from persistent storage.
                    error("Cannot delete remote secret due to missing client parameters")
                }
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

    private fun getRemoteSecretClientParameters(): RemoteSecretClientParameters? {
        return RemoteSecretClientParameters(
            workServerBaseUrl = serverAddressProvider
                .getWorkServerUrl(preferenceService.isIpv6Preferred)
                ?: return null,
            userIdentity = identityProvider.getIdentity()
                ?: return null,
            clientKey = userService.privateKey
                ?.toCryptographicByteArray()
                ?: return null,
            credentials = licenseService.loadCredentials() as? UserCredentials
                ?: return null,
        )
    }

    @Serializable
    class RemoteSecretDeleteStepsTaskData(
        private val authenticationToken: ByteArray,
    ) : AppTaskData, KoinComponent {
        override fun createTask() = RemoteSecretDeleteStepsTask(
            authenticationToken = RemoteSecretAuthenticationToken(authenticationToken),
        )
    }
}

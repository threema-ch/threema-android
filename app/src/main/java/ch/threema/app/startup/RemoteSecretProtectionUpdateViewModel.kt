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

import ch.threema.app.framework.BaseViewModel
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.startup.models.RemoteSecretUpdateStatus
import ch.threema.app.startup.models.RemoteSecretUpdateType
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.toCryptographicByteArray
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.exceptions.InvalidCredentialsException
import ch.threema.localcrypto.exceptions.PassphraseRequiredException
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("RemoteSecretProtectionUpdateViewModel")

class RemoteSecretProtectionUpdateViewModel(
    private val masterKeyManager: MasterKeyManager,
    private val serverAddressProvider: ServerAddressProvider,
    private val preferenceService: PreferenceService,
    private val userService: UserService,
    private val licenseService: LicenseService<*>,
    private val dispatcherProvider: DispatcherProvider,
) : BaseViewModel<RemoteSecretProtectionUpdateViewState, RemoteSecretProtectionUpdateViewModelEvent>() {
    private var isRunning = false

    override fun initialize() = runInitialization {
        val updateType = when (masterKeyManager.getRemoteSecretProtectionState()) {
            RemoteSecretProtectionCheckResult.SHOULD_ACTIVATE -> RemoteSecretUpdateType.ACTIVATING
            RemoteSecretProtectionCheckResult.SHOULD_DEACTIVATE -> RemoteSecretUpdateType.DEACTIVATING
            RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED,
            null,
            -> {
                emitEvent(RemoteSecretProtectionUpdateViewModelEvent.Done)
                throw CancellationException()
            }
        }

        RemoteSecretProtectionUpdateViewState(
            updateType = updateType,
            status = RemoteSecretUpdateStatus.IDLE,
        )
    }

    override suspend fun onActive() {
        runRemoteSecretProtectionUpdate()
    }

    fun onClickedRetry() {
        runRemoteSecretProtectionUpdate()
    }

    private fun runRemoteSecretProtectionUpdate() = runAction {
        if (isRunning) {
            endAction()
        }
        isRunning = true
        try {
            updateViewState {
                copy(status = RemoteSecretUpdateStatus.IN_PROGRESS)
            }

            withContext(dispatcherProvider.worker) {
                masterKeyManager.updateRemoteSecretProtectionStateIfNeeded(
                    getRemoteSecretClientParameters(),
                )
                masterKeyManager.persistKeyDataIfNeeded()
            }
            updateViewState {
                copy(status = RemoteSecretUpdateStatus.SUCCEEDED)
            }
        } catch (_: PassphraseRequiredException) {
            masterKeyManager.lockWithPassphrase()
        } catch (e: InvalidCredentialsException) {
            logger.warn("Invalid credentials", e)
            emitEvent(RemoteSecretProtectionUpdateViewModelEvent.PromptForCredentials)
        } catch (e: Exception) {
            logger.error("Failed to activate/deactivate remote secret", e)
            updateViewState {
                copy(status = RemoteSecretUpdateStatus.FAILED)
            }
        } finally {
            isRunning = false
        }
    }

    private fun getRemoteSecretClientParameters(): RemoteSecretClientParameters =
        RemoteSecretClientParameters(
            workServerBaseUrl = serverAddressProvider
                .getWorkServerUrl(preferenceService.isIpv6Preferred)
                ?: error("No work server URL found"),
            userIdentity = userService.identity
                ?: error("No user identity found"),
            clientKey = userService.privateKey
                ?.toCryptographicByteArray()
                ?: error("No client key found"),
            credentials = licenseService.loadCredentials() as? UserCredentials
                ?: error("No user credentials found"),
        )

    fun onDismissedDialog() = runAction {
        updateViewState {
            copy(status = RemoteSecretUpdateStatus.IDLE)
        }
        emitEvent(RemoteSecretProtectionUpdateViewModelEvent.Done)
    }
}

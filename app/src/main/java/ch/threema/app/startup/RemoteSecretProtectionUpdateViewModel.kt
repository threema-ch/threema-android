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
import ch.threema.domain.types.toIdentityOrNull
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.exceptions.InvalidCredentialsException
import ch.threema.localcrypto.exceptions.PassphraseRequiredException
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionInstruction
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
        val updateType = when (masterKeyManager.getRemoteSecretProtectionInstruction()) {
            RemoteSecretProtectionInstruction.SHOULD_ACTIVATE -> RemoteSecretUpdateType.ACTIVATING
            RemoteSecretProtectionInstruction.SHOULD_DEACTIVATE -> RemoteSecretUpdateType.DEACTIVATING
            RemoteSecretProtectionInstruction.NO_CHANGE_NEEDED,
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
                .getWorkServerUrl(preferenceService.isIpv6Preferred())
                ?: error("No work server URL found"),
            userIdentity = userService.identity
                ?.toIdentityOrNull()
                ?: error("No user identity found"),
            clientKey = userService.privateKey
                ?.toCryptographicByteArray()
                ?: error("No client key found"),
            credentials = licenseService.loadCredentials() as? UserCredentials
                ?: error("No user credentials found"),
        )

    fun onDismissDialog() = runAction {
        updateViewState {
            copy(status = RemoteSecretUpdateStatus.IDLE)
        }
        emitEvent(RemoteSecretProtectionUpdateViewModelEvent.Done)
    }
}

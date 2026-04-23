package ch.threema.app.reset

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.work.WorkManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.push.PushService
import ch.threema.app.services.PassphraseService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.webclient.manager.WebClientServiceManager
import ch.threema.app.webclient.services.instance.DisconnectContext
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.deleteSecurely
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.localcrypto.KeyStoreSecretKeyManager
import ch.threema.localcrypto.MasterKeyFileProvider
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseOpenHelper
import ch.threema.storage.DatabaseProviderImpl
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ResetAppTask")

/**
 * Deletes all app data and stops the app.
 */
class ResetAppTask(
    private val appContext: Context,
    private val userService: UserService,
    private val dhSessionStore: DHSessionStoreInterface,
    private val preferenceService: PreferenceService,
    private val databaseProvider: DatabaseProviderImpl,
    private val masterKeyFileProvider: MasterKeyFileProvider,
    private val webClientServiceManager: WebClientServiceManager,
    private val keyStoreSecretKeyManager: KeyStoreSecretKeyManager,
    private val multiDeviceManager: MultiDeviceManager,
    private val deleteAllContactsUseCase: DeleteAllContactsHelper,
    private val taskCreator: TaskCreator,
    private val serviceManager: ServiceManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun execute(): Nothing = withContext(dispatcherProvider.worker) {
        logger.info("Resetting app")

        stopWebClientSessions()
        if (PassphraseService.isRunning()) {
            PassphraseService.stop(appContext)
        }
        if (multiDeviceManager.isMultiDeviceActive) {
            taskCreator.scheduleDeactivateMultiDeviceTask().await()
        }
        WorkManager.getInstance(appContext).cancelAllWork()
        PushService.deleteToken(appContext)
        ShortcutUtil.deleteAllShareTargetShortcuts(preferenceService)
        ShortcutUtil.deleteAllPinnedShortcuts()

        // Delete the master key early, so that even if the remaining steps fail,
        // encrypted user data can no longer be unencrypted
        deleteMasterKey()

        // Grace period for any lingering tasks or network requests to complete
        delay(2.seconds)

        // We set a custom exception handler here, as it becomes significantly more likely that a crash might occur
        // somewhere in the app once the identity and contacts are gone and the database is closed.
        // This disables the app's default exception handling, which is ok as we will stop the entire process soon anyway.
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            logger.error("Exception detected", e)
        }

        deleteContacts()
        deleteIdentity()
        stopConnection()
        closeDatabases()
        deleteAllFiles()
        exitProcess(0)
    }

    private fun stopWebClientSessions() {
        webClientServiceManager.sessionService.stopAll(
            DisconnectContext.byUs(DisconnectContext.REASON_SESSION_DELETED),
        )
    }

    private fun deleteMasterKey() {
        masterKeyFileProvider.getVersion2KeyStoreProtectedMasterKeyFile().deleteSecurely(appContext.filesDir)
        masterKeyFileProvider.getVersion2UnencryptedMasterKeyFile().deleteSecurely(appContext.filesDir)

        // The secret key must only be deleted AFTER the master key file(s) have been deleted,
        // as having a master key file but no secret key to unlock it is an invalid state the app cannot recover from.
        keyStoreSecretKeyManager.deleteAllSecretKeys()
    }

    private suspend fun deleteContacts() {
        try {
            deleteAllContactsUseCase.call()
        } catch (e: Exception) {
            // Deletion is done on a best-effort basis, as the database file will be deleted in the end anyway
            logger.warn("Failed to delete contacts", e)
        }
    }

    private fun deleteIdentity() {
        try {
            userService.removeIdentity()
        } catch (e: Exception) {
            logger.warn("Failed to remove identity", e)
        }
    }

    private fun stopConnection() {
        try {
            serviceManager.getConnection().stop()
        } catch (e: Exception) {
            logger.warn("Failed to stop connection", e)
        }
    }

    private fun closeDatabases() {
        databaseProvider.close()
        dhSessionStore.close()
    }

    private fun deleteAllFiles() {
        preferenceService.clear()

        // All app files will be deleted in the end, but with some files we want to be extra careful,
        // so we first overwrite them with zeroes
        DatabaseOpenHelper.getDatabaseFile(appContext).deleteSecurely(appContext.filesDir)
        DatabaseNonceStore.getDatabaseFile(appContext).deleteSecurely(appContext.filesDir)
        DatabaseOpenHelper.getDatabaseBackupFile(appContext).deleteSecurely(appContext.filesDir)

        appContext.getSystemService<ActivityManager>()
            ?.clearApplicationUserData()
    }
}

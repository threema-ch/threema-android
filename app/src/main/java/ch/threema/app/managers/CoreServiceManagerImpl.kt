package ch.threema.app.managers

import ch.threema.app.dev.hasDevFeatures
import ch.threema.app.multidevice.MultiDeviceManagerImpl
import ch.threema.app.services.ServerMessageService
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.tasks.archive.TaskArchiverImpl
import ch.threema.app.tasks.archive.recovery.TaskRecoveryManagerImpl
import ch.threema.app.utils.DeviceCookieManagerImpl
import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.models.AppVersion
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.TaskManagerConfiguration
import ch.threema.domain.taskmanager.TaskManagerProvider
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.DatabaseService
import ch.threema.storage.factories.ServerMessageModelFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * The core service manager contains some core services that are used before the other services are
 * instantiated. Note that some of the provided services must be further initialized before they can
 * be used.
 */
class CoreServiceManagerImpl(
    override val version: AppVersion,
    override val databaseProvider: DatabaseProvider,
    override val preferenceStore: PreferenceStore,
    override val encryptedPreferenceStore: EncryptedPreferenceStore,
    override val identityStore: IdentityStore,
    private val nonceDatabaseStoreProvider: () -> DatabaseNonceStore,
    private val getDebugString: Task<*, *>.() -> String,
) : CoreServiceManager, KoinComponent {
    override val databaseService: DatabaseService
        get() = get()

    /**
     * The task archiver. Note that this must only be used to load the persisted tasks when the
     * service manager has been set.
     */
    override val taskArchiver: TaskArchiverImpl by lazy {
        TaskArchiverImpl(
            taskArchiveFactory = get(),
            taskRecoveryManager = TaskRecoveryManagerImpl(),
            getDebugString = getDebugString,
        )
    }

    /**
     * The device cookie manager. Note that this must only be used when the notification service is
     * passed to it.
     */
    override val deviceCookieManager: DeviceCookieManagerImpl by lazy {
        DeviceCookieManagerImpl(encryptedPreferenceStore, get<ServerMessageModelFactory>())
    }

    /**
     * The task manager. Note that this must only be used to schedule tasks when the task archiver
     * has access to the service manager.
     */
    override val taskManager: TaskManager by lazy {
        TaskManagerProvider.getTaskManager(
            TaskManagerConfiguration(
                taskArchiver = { taskArchiver },
                deviceCookieManager = deviceCookieManager,
                assertContext = hasDevFeatures(),
                getDebugString = getDebugString,
            ),
        )
    }

    /**
     * The multi device manager.
     */
    override val multiDeviceManager: MultiDeviceManagerImpl by lazy {
        MultiDeviceManagerImpl(
            preferenceStore,
            encryptedPreferenceStore,
            get<ServerMessageService>(),
            version,
        )
    }

    /**
     * The nonce factory.
     */
    override val nonceFactory: NonceFactory by lazy { NonceFactory(nonceDatabaseStoreProvider()) }
}

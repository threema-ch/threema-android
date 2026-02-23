package ch.threema.app.managers

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.models.AppVersion
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TaskArchiver
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.DatabaseService

/**
 * The core service manager contains some core services that are used before the other services are
 * instantiated. Note that some of the provided services must be further initialized before they can
 * be used.
 */
interface CoreServiceManager {
    /**
     * The app version.
     */
    val version: AppVersion

    /**
     * The database service.
     */
    val databaseService: DatabaseService

    /**
     * The preference store
     */
    val preferenceStore: PreferenceStore

    /**
     * The preference store for data that needs to be encrypted at rest
     */
    val encryptedPreferenceStore: EncryptedPreferenceStore

    /**
     * The task archiver. Note that this must only be used to load the persisted tasks when the
     * service manager has been set.
     */
    val taskArchiver: TaskArchiver

    /**
     * The device cookie manager. Note that this must only be used when the notification service is
     * passed to it.
     */
    val deviceCookieManager: DeviceCookieManager

    /**
     * The task manager. Note that this must only be used to schedule tasks when the task archiver
     * has access to the service manager.
     */
    val taskManager: TaskManager

    /**
     * The multi device manager.
     */
    val multiDeviceManager: MultiDeviceManager

    /**
     * The identity store.
     */
    val identityStore: IdentityStore

    /**
     * The nonce factory.
     */
    val nonceFactory: NonceFactory
}

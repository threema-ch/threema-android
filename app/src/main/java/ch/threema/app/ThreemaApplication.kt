/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Process
import androidx.annotation.OpenForTesting
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import ch.threema.app.AppConstants.ACTIVITY_CONNECTION_LIFETIME
import ch.threema.app.AppLogging.logAppVersionInfo
import ch.threema.app.AppLogging.logExitReason
import ch.threema.app.apptaskexecutor.AppTaskExecutor
import ch.threema.app.debug.StrictModeMonitor
import ch.threema.app.di.MasterKeyLockStateChangeHandler
import ch.threema.app.di.Qualifiers
import ch.threema.app.di.getOrNull
import ch.threema.app.di.initDependencyInjection
import ch.threema.app.drafts.DraftManager
import ch.threema.app.managers.CoreServiceManagerImpl
import ch.threema.app.managers.ServiceManager
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.passphrase.PassphraseStateMonitor
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.push.PushService
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.services.ThreemaPushService
import ch.threema.app.startup.AppProcessLifecycleObserver
import ch.threema.app.startup.AppStartupError
import ch.threema.app.startup.AppStartupMonitorImpl
import ch.threema.app.startup.MasterKeyEventMonitor
import ch.threema.app.startup.RemoteSecretMonitorRetryController
import ch.threema.app.startup.models.AppSystem
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.EncryptedPreferenceStoreImpl
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.stores.IdentityStoreImpl
import ch.threema.app.stores.MutableIdentityProvider
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.systemupdates.SystemUpdateException
import ch.threema.app.systemupdates.SystemUpdateProvider
import ch.threema.app.systemupdates.SystemUpdater
import ch.threema.app.ui.DynamicColorsHelper
import ch.threema.app.utils.AppVersionProvider.appVersion
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConnectionIndicatorUtil
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.FileUtil
import ch.threema.app.utils.LinuxSecureRandom
import ch.threema.app.utils.LoggingUEH
import ch.threema.app.utils.PushUtil
import ch.threema.app.utils.StateBitmapUtil
import ch.threema.app.utils.Toaster.Companion.showToast
import ch.threema.app.utils.Toaster.Duration.LONG
import ch.threema.app.voip.Config
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl
import ch.threema.app.workers.AutoDeleteWorker
import ch.threema.app.workers.ContactUpdateWorker
import ch.threema.app.workers.ShareTargetUpdateWorker
import ch.threema.app.workers.WorkSyncWorker
import ch.threema.base.ThreemaException
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.now
import ch.threema.data.repositories.ModelRepositories
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.libthreema.LogLevel
import ch.threema.libthreema.initialize as initLibthreema
import ch.threema.localcrypto.MasterKey
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.MasterKeyManagerImpl
import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.logging.LibthreemaLogger
import ch.threema.storage.DatabaseDowngradeException
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseService
import ch.threema.storage.DatabaseState
import ch.threema.storage.DatabaseUpdateException
import ch.threema.storage.SQLDHSessionStore
import ch.threema.storage.deriveDatabasePassword
import ch.threema.storage.setupDatabaseLogging
import kotlin.getValue
import kotlin.system.exitProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

private val logger = LoggingUtil.getThreemaLogger("ThreemaApplication")

@OpenForTesting
open class ThreemaApplication : Application() {

    // TODO(ANDR-4187): Move these dependencies and the logic that uses them to a better place
    private val passphraseStateMonitor: PassphraseStateMonitor by inject()
    private val masterKeyEventMonitor: MasterKeyEventMonitor by inject()
    private val appTaskExecutor: AppTaskExecutor by inject()
    private val appStartupMonitor: AppStartupMonitorImpl by inject()
    private val identityProvider: IdentityProvider by inject()

    override fun onCreate() {
        if (!checkAppReplacingState(applicationContext)) {
            return
        }
        instance = this

        StrictModeMonitor.enableIfNeeded()

        super.onCreate()

        DynamicColorsHelper.applyDynamicColorsIfEnabled(this)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        initLibthreema(LogLevel.TRACE, LibthreemaLogger())

        setUpUnhandledExceptionLogger()

        setUpSecureRandom()

        setUpDayNightMode(this)

        logger.info("*** App launched")
        logAppVersionInfo(applicationContext)
        logExitReason(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))

        initDependencyInjection(this)

        logger.info("Has identity: {}", identityProvider.getIdentity() != null)
        logger.info("Remote secrets supported: {}", ConfigUtils.isRemoteSecretsSupported())

        ProcessLifecycleOwner.get().lifecycle.addObserver(get<AppProcessLifecycleObserver>())

        val masterKeyManager: MasterKeyManagerImpl = try {
            get()
        } catch (e: Exception) {
            logger.error("Failed to create master key manager", e)
            appStartupMonitor.reportUnexpectedAppStartupError("MK-0")
            return
        }

        coroutineScope.launch {
            try {
                withContext(dispatcherProvider.io) {
                    masterKeyManager.readOrGenerateKey()
                }
            } catch (e: Exception) {
                logger.error("Failed to read or generate master key", e)
                appStartupMonitor.reportUnexpectedAppStartupError("MK-1")
                return@launch
            }

            // TODO(ANDR-4187): Move all of these coroutines to a better place
            launch(dispatcherProvider.worker) {
                monitorRemoteSecret(
                    masterKeyManager = masterKeyManager,
                    appStartupMonitor = appStartupMonitor,
                )
            }
            launch(dispatcherProvider.main) {
                monitorMasterKey(
                    masterKeyManager = masterKeyManager,
                    appStartupMonitor = appStartupMonitor,
                )
            }
            launch(dispatcherProvider.worker) {
                masterKeyEventMonitor.monitorMasterKeyEvents()
            }
            launch(dispatcherProvider.worker) {
                passphraseStateMonitor.monitorPassphraseLock()
            }
            launch(dispatcherProvider.worker) {
                appTaskExecutor.start()
                logger.error("App task executor has stopped")
            }
        }

        GlobalBroadcastReceivers.registerBroadcastReceivers(applicationContext)
    }

    private fun checkAppReplacingState(context: Context): Boolean {
        // workaround https://code.google.com/p/android/issues/detail?id=56296
        if (context.resources == null) {
            logger.warn("App is currently installing. Killing it.")
            Process.killProcess(Process.myPid())
            return false
        }
        return true
    }

    private fun setUpUnhandledExceptionLogger() {
        Thread.setDefaultUncaughtExceptionHandler(LoggingUEH())
    }

    private fun setUpSecureRandom() {
        // We instantiate our own SecureRandom implementation to make sure this gets used everywhere
        LinuxSecureRandom()
    }

    private suspend fun monitorRemoteSecret(
        masterKeyManager: MasterKeyManagerImpl,
        appStartupMonitor: AppStartupMonitorImpl,
    ) = coroutineScope {
        while (isActive) {
            try {
                masterKeyManager.monitorRemoteSecret()
            } catch (_: BlockedByAdminException) {
                logger.info("User is blocked by admin")
                masterKeyManager.lockPermanently()
                appStartupMonitor.reportAppStartupError(AppStartupError.BlockedByAdmin)
            } catch (e: RemoteSecretMonitorException) {
                logger.warn("Fetching/monitoring remote secret failed", e)
                masterKeyManager.lockWithRemoteSecret()
                appStartupMonitor.reportAppStartupError(AppStartupError.FailedToFetchRemoteSecret)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Fetching/monitoring remote secret failed unexpectedly", e)
                masterKeyManager.lockPermanently()
                appStartupMonitor.reportAppStartupError(AppStartupError.Unexpected("RS-MONITOR"))
            }

            // Wait for the user to request a retry
            RemoteSecretMonitorRetryController.awaitRetryRequest()
            appStartupMonitor.clearTemporaryStartupErrors()
        }
    }

    private suspend fun monitorMasterKey(
        masterKeyManager: MasterKeyManagerImpl,
        appStartupMonitor: AppStartupMonitorImpl,
    ) = coroutineScope {
        val masterKeyProvider = masterKeyManager.masterKeyProvider
        while (isActive) {
            if (masterKeyManager.isLockedWithRemoteSecret()) {
                try {
                    appStartupMonitor.whileFetchingRemoteSecret {
                        masterKeyManager.unlockWithRemoteSecret()
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to unlock with remote secret", e)
                    appStartupMonitor.reportUnexpectedAppStartupError("RS-UNLOCK")
                    cancel()
                }
            }

            val masterKey = masterKeyProvider.awaitUnlocked()
            onMasterKeyUnlocked(masterKey)

            masterKeyProvider.awaitLocked()
            get<MasterKeyLockStateChangeHandler>().onMasterKeyLocked()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        logger.info("*** App is low on memory")

        super.onLowMemory()
        try {
            get<ServiceManagerProvider>()
                .getServiceManagerOrNull()
                ?.avatarCacheService
                ?.clear()
        } catch (e: Exception) {
            logger.error("Failed to clear avatar cache", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        logger.info("onTrimMemory (level={})", level)
        super.onTrimMemory(level)
    }

    companion object : KoinComponent {
        @SuppressLint("StaticFieldLeak")
        private lateinit var instance: Context

        private val masterKeyManager: MasterKeyManager by inject()
        private val appStartupMonitor: AppStartupMonitorImpl by inject()
        private val masterKeyLockStateChangeHandler: MasterKeyLockStateChangeHandler by inject()
        private val dispatcherProvider: DispatcherProvider by inject()
        private val coroutineScope by lazy { CoroutineScope(dispatcherProvider.worker) }

        private suspend fun onMasterKeyUnlocked(masterKey: MasterKey) {
            logger.info("*** Master key unlocked")

            val appContext = instance

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
            resolveMasterKeyDeactivationRaceCondition(appContext, masterKeyManager, sharedPreferences)
            setUpDayNightMode(appContext)

            StateBitmapUtil.init(appContext)

            ConnectionIndicatorUtil.init(appContext)

            try {
                val preferenceStore: PreferenceStore = get()
                val mutableIdentityProvider: MutableIdentityProvider = get()
                val encryptedPreferenceStore = EncryptedPreferenceStoreImpl(appContext, masterKey)

                setUpSqlCipher()
                val databaseService = createDatabaseService(appContext, masterKey)
                coroutineScope.launch {
                    try {
                        databaseService.migrateIfNeeded()
                    } catch (e: DatabaseUpdateException) {
                        appStartupMonitor.reportUnexpectedAppStartupError("DB-${e.failedDatabaseUpdateVersion}")
                    } catch (e: DatabaseDowngradeException) {
                        appStartupMonitor.reportUnexpectedAppStartupError("DB-DG-${e.oldDatabaseVersion}")
                    }
                }
                val dhSessionStore = createDHSessionStore(appContext, masterKey)
                val identityStore = IdentityStoreImpl(mutableIdentityProvider, preferenceStore, encryptedPreferenceStore)

                // Since the DB updates are kicked off on a different thread, we have to wait for them to start before we continue.
                // Otherwise we might get race-conditions with other threads that might access the DB before the migration thread.
                databaseService.databaseState.first { it != DatabaseState.INIT }

                // Note: the task manager should only be used to schedule tasks once the service manager is set
                val coreServiceManager = createCoreServiceManager(
                    appContext,
                    databaseService,
                    preferenceStore,
                    encryptedPreferenceStore,
                    identityStore,
                )

                val modelRepositories = ModelRepositories(coreServiceManager)

                val systemUpdater = SystemUpdater(sharedPreferences)
                val serviceManager = try {
                    ServiceManager(
                        modelRepositories,
                        dhSessionStore,
                        masterKeyManager.masterKeyProvider,
                        coreServiceManager,
                        get(qualifier = Qualifiers.okHttpBase),
                        getOrNull(),
                    )
                } catch (e: ThreemaException) {
                    logger.error("Could not instantiate service manager", e)
                    appStartupMonitor.reportUnexpectedAppStartupError("SM-0")
                    return
                }
                runSystemUpdatesIfNeeded(
                    appContext,
                    systemUpdater,
                    serviceManager,
                    databaseService,
                    appStartupMonitor,
                )

                masterKeyLockStateChangeHandler.onMasterKeyUnlocked(
                    serviceManager,
                    databaseService.databaseState,
                    systemUpdater.systemUpdateState,
                )

                startThreemaPushIfNeeded(appContext)

                setDefaultPreferences(appContext, sharedPreferences)

                registerConnectionStateChangedListener(appContext, serviceManager.connection)

                if (ConfigUtils.isWorkBuild()) {
                    coroutineScope.launch {
                        AppRestrictionService.getInstance().reload()
                    }
                }

                cancelNewMessageNotification(appContext)

                // trigger a connection now, just to be sure we're up-to-date and any broken connection
                // (e.g. from before a reboot) is preempted.
                with(serviceManager.lifetimeService) {
                    acquireConnection("resetConnection")
                    releaseConnectionLinger("resetConnection", ACTIVITY_CONNECTION_LIFETIME)
                }

                coroutineScope.launch {
                    appStartupMonitor.awaitSystem(AppSystem.DATABASE_UPDATES)

                    markUploadingFilesAsFailed(databaseService)
                    SessionWakeUpServiceImpl.getInstance().processPendingWakeupsAsync()
                    serviceManager.threemaSafeService.schedulePeriodicUpload()
                    scheduleSync(appContext, serviceManager.preferenceService, preferenceStore)
                }

                coroutineScope.launch {
                    loadDraftsFromStorage(serviceManager)
                }

                coroutineScope.launch {
                    appStartupMonitor.awaitAll()
                    AppLogging.disableIfNeeded(appContext, preferenceStore)
                }
            } catch (e: MasterKeyLockedException) {
                logger.error("Master key was unexpectedly locked during onMasterKeyUnlocked", e)
                appStartupMonitor.reportUnexpectedAppStartupError("MK-L")
            } catch (e: SQLiteException) {
                logger.error("Failed to open database", e)
                appStartupMonitor.reportUnexpectedAppStartupError("DB-U0")
            }
        }

        private fun startThreemaPushIfNeeded(context: Context) {
            ThreemaPushService.tryStart(logger, context)
        }

        private fun runSystemUpdatesIfNeeded(
            context: Context,
            systemUpdater: SystemUpdater,
            serviceManager: ServiceManager,
            databaseService: DatabaseService,
            appStartupMonitor: AppStartupMonitorImpl,
        ) {
            val hasUpdates = systemUpdater.checkForUpdates(
                systemUpdateProvider = SystemUpdateProvider(context, serviceManager),
                initialVersion = getInitialSystemUpdateVersion(databaseService),
            )
            if (hasUpdates) {
                coroutineScope.launch {
                    try {
                        systemUpdater.runUpdates()
                    } catch (e: SystemUpdateException) {
                        appStartupMonitor.reportUnexpectedAppStartupError("SU-${e.failedSystemUpdateVersion}")
                    }
                }
            }
        }

        private fun getInitialSystemUpdateVersion(databaseService: DatabaseService): Int? {
            // Until DB version 109, the system updates and database updates were treated as the same thing and as such shared a version number.
            // Now they are split up, with both update types having their own version number which is incremented independently and thus will
            // diverge over time.
            return databaseService.oldVersion?.coerceAtMost(109)
        }

        private fun setUpDayNightMode(context: Context) {
            AppCompatDelegate.setDefaultNightMode(ConfigUtils.getAppThemePrefs(context))
        }

        private fun setUpSqlCipher() {
            System.loadLibrary("sqlcipher")
            setupDatabaseLogging()
        }

        private fun createCoreServiceManager(
            appContext: Context,
            databaseService: DatabaseService,
            preferenceStore: PreferenceStore,
            encryptedPreferenceStore: EncryptedPreferenceStore,
            identityStore: IdentityStoreImpl,
        ) =
            CoreServiceManagerImpl(
                appVersion,
                databaseService,
                preferenceStore,
                encryptedPreferenceStore,
                identityStore,
                nonceDatabaseStoreProvider = {
                    val databaseNonceStore = DatabaseNonceStore(appContext, identityStore)
                    databaseNonceStore.migrateIfNeeded()
                    logger.info("Nonce count (csp): {}", databaseNonceStore.getCount(NonceScope.CSP))
                    logger.info("Nonce count (d2d): {}", databaseNonceStore.getCount(NonceScope.D2D))
                    databaseNonceStore
                },
            )

        private fun cancelNewMessageNotification(context: Context) {
            NotificationManagerCompat.from(context).cancel(NotificationIDs.NEW_MESSAGE_LOCKED_NOTIFICATION_ID)
        }

        private fun registerConnectionStateChangedListener(context: Context, connection: ServerConnection) {
            // Whenever the connection is established, check whether the push token needs to be updated.
            connection.addConnectionStateListener { connectionState ->
                logger.info("ServerConnection state changed: {}", connectionState)
                if (connectionState == ConnectionState.LOGGEDIN) {
                    GlobalAppState.lastLoggedIn = now()

                    if (PushService.servicesInstalled(context) && PushUtil.isPushEnabled(context)) {
                        if (PushUtil.pushTokenNeedsRefresh(context)) {
                            PushUtil.enqueuePushTokenUpdate(context, false, false)
                        } else {
                            logger.debug("Push token is still fresh. No update needed")
                        }
                    }
                }
            }
        }

        private fun scheduleSync(
            context: Context,
            preferenceService: PreferenceService,
            preferenceStore: PreferenceStore,
        ) {
            WorkSyncWorker.schedulePeriodicWorkSync(context, preferenceService)
            ContactUpdateWorker.schedulePeriodicSync(context, preferenceService)
            if (preferenceStore.getBoolean(context.getString(R.string.preferences__direct_share))) {
                ShareTargetUpdateWorker.scheduleShareTargetShortcutUpdate(context)
            }
            AutoDeleteWorker.scheduleAutoDelete(context)
        }

        private fun createDatabaseService(
            context: Context,
            masterKey: MasterKey,
        ): DatabaseService {
            val databaseService = DatabaseService(
                context = context,
                password = masterKey.deriveDatabasePassword(),
                onDatabaseCorrupted = {
                    showToast("Database corrupted. Please restart your device and try again.", duration = LONG)
                    exitProcess(2)
                },
            )
            return databaseService
        }

        private fun resolveMasterKeyDeactivationRaceCondition(
            context: Context,
            masterKeyManager: MasterKeyManager,
            sharedPreferences: SharedPreferences,
        ) {
            // Fix master key preference state if necessary (could be wrong if user kills app
            // while disabling master key passphrase).
            if (
                masterKeyManager.isProtectedWithPassphrase() &&
                !sharedPreferences.getBoolean(context.getString(R.string.preferences__masterkey_switch), false)
            ) {
                logger.debug("Master key is protected, but switch preference is disabled - fixing")
                sharedPreferences.edit {
                    putBoolean(context.getString(R.string.preferences__masterkey_switch), true)
                }
            }
        }

        /**
         * Set the hardware echo cancellation preference depending on the device type exclusion list and initially set all default preferences.
         *
         * This reads all the xml files and applies the default values for each of the preferences. This is done by the preference manager by creating
         * the views and attaching them. As the synchronized settings require the service manager to be available to persist the setting, this method
         * must be called after the service manager has been initialized.
         */
        private fun setDefaultPreferences(context: Context, sharedPreferences: SharedPreferences) {
            // If device is in AEC exclusion list and the user did not choose a preference yet,
            // update the shared preference.
            if (sharedPreferences.getString(context.getString(R.string.preferences__voip_echocancel), "none") == "none") {
                // Determine whether device is excluded from hardware AEC
                val modelInfo = Build.MANUFACTURER + ";" + Build.MODEL
                val exclude = !Config.allowHardwareAec()

                // Set default preference
                sharedPreferences.edit {
                    if (exclude) {
                        logger.debug("Device {} is on AEC exclusion list, switching to software echo cancellation", modelInfo)
                        putString(context.getString(R.string.preferences__voip_echocancel), "sw")
                    } else {
                        logger.debug("Device {} is not on AEC exclusion list", modelInfo)
                        putString(context.getString(R.string.preferences__voip_echocancel), "hw")
                    }
                }
            }

            try {
                PreferenceManager.setDefaultValues(context, R.xml.preference_chat, true)
                PreferenceManager.setDefaultValues(context, R.xml.preference_privacy, true)
                PreferenceManager.setDefaultValues(context, R.xml.preference_appearance, true)
                PreferenceManager.setDefaultValues(context, R.xml.preference_notifications, true)
                PreferenceManager.setDefaultValues(context, R.xml.preference_media, true)
                PreferenceManager.setDefaultValues(context, R.xml.preference_calls, true)
                PreferenceManager.setDefaultValues(context, R.xml.preference_advanced_options, true)
            } catch (e: Exception) {
                logger.error("Failed to set default preferences values", e)
            }
        }

        private fun createDHSessionStore(
            context: Context,
            masterKey: MasterKey,
        ): DHSessionStoreInterface {
            // We create the DH session store here and execute a null operation on it to prevent
            // the app from being launched when the database is downgraded.
            val dhSessionStore = SQLDHSessionStore(context, masterKey.value)
            try {
                dhSessionStore.executeNull()
                return dhSessionStore
            } catch (e: Exception) {
                logger.error("Could not execute a statement on the DH session database", e)
                // The database file seems to be corrupt, therefore we delete the file
                val databaseFile = context.getDatabasePath(SQLDHSessionStore.DATABASE_NAME)
                if (databaseFile.exists()) {
                    FileUtil.deleteFileOrWarn(databaseFile, "sql dh session database", logger)
                }
                return SQLDHSessionStore(context, masterKey.value)
            }
        }

        @WorkerThread
        private fun markUploadingFilesAsFailed(databaseService: DatabaseService) {
            // Mark all file messages with state 'uploading' as failed. This is because the file
            // upload is not continued after app restarts. When the state has been changed to
            // failed, a resend button is displayed on the message. We only need to do this in the
            // uploading state as in sending state a persistent task is already scheduled and the
            // message will be sent when a connection is available.
            with(databaseService) {
                messageModelFactory.markUnscheduledFileMessagesAsFailed()
                groupMessageModelFactory.markUnscheduledFileMessagesAsFailed()
                distributionListMessageModelFactory.markUnscheduledFileMessagesAsFailed()
            }
        }

        @WorkerThread
        private fun loadDraftsFromStorage(serviceManager: ServiceManager) {
            DraftManager.retrieveMessageDraftsFromStorage(serviceManager.preferenceService)
        }

        // TODO(ANDR-4187): Remove this static method
        @JvmStatic
        fun getServiceManager(): ServiceManager? = get<ServiceManagerProvider>().getServiceManagerOrNull()

        // TODO(ANDR-4187): Remove this static method
        @JvmStatic
        fun requireServiceManager(): ServiceManager = get<ServiceManager>()

        @JvmStatic
        fun getAppContext(): Context = instance
    }
}

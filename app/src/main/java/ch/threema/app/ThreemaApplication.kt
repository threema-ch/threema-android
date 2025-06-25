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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import ch.threema.app.AppLogging.logAppVersionInfo
import ch.threema.app.AppLogging.logExitReason
import ch.threema.app.debug.StrictModeMonitor
import ch.threema.app.drafts.DraftManager
import ch.threema.app.managers.CoreServiceManagerImpl
import ch.threema.app.managers.ServiceManager
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.push.PushService
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.services.ThreemaPushService
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.startup.AppStartupMonitorImpl
import ch.threema.app.stores.IdentityStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.systemupdates.SystemUpdateException
import ch.threema.app.systemupdates.SystemUpdateProvider
import ch.threema.app.systemupdates.SystemUpdater
import ch.threema.app.ui.DynamicColorsHelper
import ch.threema.app.utils.AppVersionProvider.appVersion
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConnectionIndicatorUtil
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
import ch.threema.base.utils.toHexString
import ch.threema.common.now
import ch.threema.data.repositories.ModelRepositories
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.libthreema.LogLevel
import ch.threema.libthreema.init as initLibthreema
import ch.threema.localcrypto.MasterKey
import ch.threema.localcrypto.MasterKeyLockedException
import ch.threema.logging.LibthreemaLogger
import ch.threema.storage.DatabaseDowngradeException
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseService
import ch.threema.storage.DatabaseState
import ch.threema.storage.DatabaseUpdateException
import ch.threema.storage.SQLDHSessionStore
import ch.threema.storage.setupDatabaseLogging
import com.datatheorem.android.trustkit.TrustKit
import java.io.File
import java.io.IOException
import kotlin.concurrent.Volatile
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private val logger = LoggingUtil.getThreemaLogger("ThreemaApplication")

@OpenForTesting
open class ThreemaApplication : Application(), DefaultLifecycleObserver {
    override fun onCreate() {
        context = applicationContext

        StrictModeMonitor.enableIfNeeded()

        super<Application>.onCreate()

        DynamicColorsHelper.applyDynamicColorsIfEnabled(this)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        initLibthreema(LogLevel.TRACE, LibthreemaLogger())

        if (!checkAppReplacingState(applicationContext)) {
            return
        }

        setUpCertificatePinning()

        setUpUnhandledExceptionLogger()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        setUpSecureRandom()

        val masterKeyFile = getMasterKeyFile(applicationContext)
            ?: run {
                appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("MK-0"))
                return
            }

        masterKey = try {
            if (!masterKeyFile.exists()) {
                onMasterKeyFileNotFound(applicationContext)
            }
            MasterKey(masterKeyFile, null, true)
        } catch (e: IOException) {
            logger.error("Failed to process master key", e)
            appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("MK-1"))
            return
        }

        if (!masterKey.isLocked) {
            onMasterKeyUnlocked(masterKey)
        } else {
            setUpDayNightMode()
        }

        GlobalBroadcastReceivers.registerBroadcastReceivers(applicationContext)

        startThreemaPushIfNeeded(applicationContext)
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

    private fun setUpCertificatePinning() {
        TrustKit.initializeWithNetworkSecurityConfiguration(this)
    }

    private fun setUpUnhandledExceptionLogger() {
        Thread.setDefaultUncaughtExceptionHandler(LoggingUEH())
    }

    private fun setUpSecureRandom() {
        // We instantiate our own SecureRandom implementation to make sure this gets used everywhere
        LinuxSecureRandom()
    }

    private fun getMasterKeyFile(context: Context): File? {
        val filesDir = context.filesDir
        if (filesDir == null) {
            logger.error("App directory was unexpectedly null")
            return null
        }
        filesDir.mkdirs()
        if (!filesDir.isDirectory) {
            logger.error("App directory could not be created")
            return null
        }
        return File(filesDir, AppConstants.AES_KEY_FILE)
    }

    private fun onMasterKeyFileNotFound(context: Context) {
        // If the MasterKey file does not exist, remove every file that is encrypted with this non-existing MasterKey file
        logger.warn("master key is missing or does not match, deleting DB and preferences")
        deleteDatabaseFiles(context)
        deleteAllPreferences(context)
    }

    private fun deleteDatabaseFiles(context: Context) {
        val defaultDatabaseFile = context.getDatabasePath(DatabaseService.DEFAULT_DATABASE_NAME_V4)
        if (defaultDatabaseFile.exists()) {
            val databaseBackup = File(defaultDatabaseFile.path + ".backup")
            if (!defaultDatabaseFile.renameTo(databaseBackup)) {
                FileUtil.deleteFileOrWarn(defaultDatabaseFile, "threema4 database", logger)
            }
        }

        val nonceDatabaseFile = context.getDatabasePath(DatabaseNonceStore.DATABASE_NAME_V4)
        if (nonceDatabaseFile.exists()) {
            FileUtil.deleteFileOrWarn(nonceDatabaseFile, "nonce4 database", logger)
        }

        val sqldhSessionDatabaseFile = context.getDatabasePath(SQLDHSessionStore.DATABASE_NAME)
        if (sqldhSessionDatabaseFile.exists()) {
            FileUtil.deleteFileOrWarn(sqldhSessionDatabaseFile, "sql dh session database", logger)
        }
    }

    private fun deleteAllPreferences(context: Context) {
        val preferenceStore = PreferenceStore(context, null)
        preferenceStore.clear()
    }

    private fun startThreemaPushIfNeeded(context: Context) {
        ThreemaPushService.tryStart(logger, context)
    }

    override fun onStart(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now visible")
    }

    override fun onStop(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now stopped")
    }

    override fun onCreate(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now created")
    }

    override fun onResume(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now resumed")
        GlobalAppState.isAppResumed = true

        serviceManager?.let { serviceManager ->
            serviceManager.lifetimeService.acquireConnection(AppConstants.ACTIVITY_CONNECTION_TAG)
            logger.info("Connection now acquired")

            reloadAppRestrictionsIfNeeded()
        }
            ?: logger.info("Service manager is null")
    }

    override fun onPause(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now paused")
        GlobalAppState.isAppResumed = false

        serviceManager?.lifetimeService?.releaseConnectionLinger(AppConstants.ACTIVITY_CONNECTION_TAG, ACTIVITY_CONNECTION_LIFETIME)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now destroyed")
        coroutineScope.cancel()
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        logger.info("*** App is low on memory")

        super.onLowMemory()
        try {
            serviceManager?.avatarCacheService?.clear()
        } catch (e: Exception) {
            logger.error("Failed to clear avatar cache", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        logger.info("onTrimMemory (level={})", level)

        super.onTrimMemory(level)

        /* save our master key now if necessary, as we may get killed and if the user was still in the
         * initial setup procedure, this can lead to trouble as the database may already be there
         * but we may no longer be able to access it due to missing master key
         */
        try {
            val masterKey = ThreemaApplication.masterKey
            if (!masterKey.isProtected) {
                if (serviceManager?.notificationPreferenceService?.getWizardRunning() == true) {
                    masterKey.setPassphrase(null)
                }
            }
        } catch (e: Exception) {
            logger.error("Exception", e)
        }
    }

    companion object {
        private const val ACTIVITY_CONNECTION_LIFETIME = 60_000L

        private val coroutineScope = CoroutineScope(Dispatchers.Default)

        @SuppressLint("StaticFieldLeak")
        private lateinit var context: Context

        private val appStartupMonitor = AppStartupMonitorImpl()

        @Volatile
        private var serviceManager: ServiceManager? = null

        private lateinit var masterKey: MasterKey

        private fun reloadAppRestrictionsIfNeeded() {
            // Check app restrictions when the app resumes
            if (ConfigUtils.isWorkBuild()) {
                coroutineScope.launch {
                    AppRestrictionService.getInstance().reload()
                }
            }
        }

        @JvmStatic
        @Synchronized
        fun onMasterKeyUnlocked(masterKey: MasterKey) {
            val appContext = getAppContext()

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
            resolveMasterKeyDeactivationRaceCondition(appContext, masterKey, sharedPreferences)
            setUpDayNightMode()

            logger.info("*** App launched, master key unlocked")
            logAppVersionInfo(appContext)
            logExitReason(appContext, sharedPreferences)

            StateBitmapUtil.init(appContext)

            ConnectionIndicatorUtil.init(appContext)

            try {
                val preferenceStore = PreferenceStore(appContext, masterKey)

                setUpSqlCipher()
                val masterKeyBytes = masterKey.key
                val databaseService = createDatabaseService(appContext, masterKeyBytes)
                val dhSessionStore = createDHSessionStore(appContext, masterKeyBytes)
                val identityStore = IdentityStore(preferenceStore)

                // Note: the task manager should only be used to schedule tasks once the service manager is set
                val coreServiceManager = createCoreServiceManager(
                    appContext,
                    databaseService,
                    preferenceStore,
                    identityStore,
                )

                val modelRepositories = ModelRepositories(coreServiceManager)

                val systemUpdater = SystemUpdater(sharedPreferences)
                appStartupMonitor.init(
                    databaseService.databaseState,
                    systemUpdater.systemUpdateState,
                )

                coroutineScope.launch {
                    try {
                        databaseService.migrateIfNeeded()
                    } catch (e: DatabaseUpdateException) {
                        appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("DB-${e.failedDatabaseUpdateVersion}"))
                    } catch (e: DatabaseDowngradeException) {
                        appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("DB-DG-${e.oldDatabaseVersion}"))
                    }
                }
                runBlocking {
                    // Since the DB migrations are kicked off on a different thread, we have to wait for them to start before we continue.
                    // Otherwise we might get race-conditions with other threads that might access the DB before the migration thread.
                    databaseService.databaseState.first { it != DatabaseState.INIT }
                }

                val serviceManager = try {
                    ServiceManager(
                        modelRepositories,
                        dhSessionStore,
                        masterKey,
                        coreServiceManager,
                    )
                } catch (e: ThreemaException) {
                    logger.error("Could not instantiate service manager", e)
                    appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("SM-0"))
                    return
                }
                runSystemUpdatesIfNeeded(systemUpdater, serviceManager, databaseService)

                ThreemaApplication.serviceManager = serviceManager

                setDefaultPreferences(sharedPreferences)

                registerConnectionStateChangedListener(appContext, serviceManager.connection)

                reloadAppRestrictionsIfNeeded()

                cancelNewMessageNotification(appContext)

                // trigger a connection now, just to be sure we're up-to-date and any broken connection
                // (e.g. from before a reboot) is preempted.
                with(serviceManager.lifetimeService) {
                    acquireConnection("resetConnection")
                    releaseConnectionLinger("resetConnection", ACTIVITY_CONNECTION_LIFETIME)
                }

                GlobalListeners(appContext, serviceManager).setUp()

                coroutineScope.launch {
                    appStartupMonitor.awaitSystem(AppStartupMonitor.AppSystem.DATABASE_UPDATES)

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
                appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("MK-L"))
            } catch (e: SQLiteException) {
                logger.error("Failed to open database", e)
                appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("DB-U0"))
            } catch (e: ThreemaException) {
                // no identity
                logger.info("No valid identity.", e)
            }
        }

        private fun runSystemUpdatesIfNeeded(
            systemUpdater: SystemUpdater,
            serviceManager: ServiceManager,
            databaseService: DatabaseService,
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
                        appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("SU-${e.failedSystemUpdateVersion}"))
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

        private fun setUpDayNightMode() {
            AppCompatDelegate.setDefaultNightMode(ConfigUtils.getAppThemePrefs())
        }

        private fun setUpSqlCipher() {
            System.loadLibrary("sqlcipher")
            setupDatabaseLogging()
        }

        private fun createCoreServiceManager(
            appContext: Context,
            databaseService: DatabaseService,
            preferenceStore: PreferenceStore,
            identityStore: IdentityStore,
        ) =
            CoreServiceManagerImpl(
                appVersion,
                databaseService,
                preferenceStore,
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
            masterKey: ByteArray,
        ): DatabaseService {
            val databaseService = DatabaseService(
                context = context,
                databaseName = DatabaseService.DEFAULT_DATABASE_NAME_V4,
                databaseKey = getDatabaseKey(masterKey),
                onDatabaseCorrupted = {
                    showToast("Database corrupted. Please restart your device and try again.", duration = LONG)
                    exitProcess(2)
                },
            )
            return databaseService
        }

        private fun getDatabaseKey(masterKey: ByteArray) = "x\"${masterKey.toHexString()}\""

        private fun resolveMasterKeyDeactivationRaceCondition(
            context: Context,
            masterKey: MasterKey,
            sharedPreferences: SharedPreferences,
        ) {
            // Fix master key preference state if necessary (could be wrong if user kills app
            // while disabling master key passphrase).
            if (masterKey.isProtected && !sharedPreferences.getBoolean(context.getString(R.string.preferences__masterkey_switch), false)) {
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
        private fun setDefaultPreferences(sharedPreferences: SharedPreferences) {
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
            masterKey: ByteArray,
        ): DHSessionStoreInterface {
            // We create the DH session store here and execute a null operation on it to prevent
            // the app from being launched when the database is downgraded.
            val dhSessionStore = SQLDHSessionStore(context, masterKey)
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
                return SQLDHSessionStore(context, masterKey)
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

        @JvmStatic
        fun onMasterKeyLocked() {
            appStartupMonitor.reset()
        }

        @JvmStatic
        fun getServiceManager(): ServiceManager? = serviceManager

        @JvmStatic
        fun requireServiceManager(): ServiceManager = serviceManager!!

        /**
         * Returns the [ServiceManager] once the app is ready for normal operation, or null if the [timeout] is reached.
         */
        suspend fun awaitServiceManagerWithTimeout(timeout: Duration): ServiceManager? = withTimeoutOrNull(timeout) {
            appStartupMonitor.awaitAll()
            requireServiceManager()
        }

        @JvmStatic
        fun getAppStartupMonitor(): AppStartupMonitor = appStartupMonitor

        @JvmStatic
        fun getMasterKey(): MasterKey = masterKey

        @JvmStatic
        fun getAppContext(): Context = context
    }
}

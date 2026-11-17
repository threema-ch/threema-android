package ch.threema.app.di

import android.content.Context
import android.os.PowerManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import ch.threema.app.BuildFlavor
import ch.threema.app.GlobalAppState
import ch.threema.app.androidcontactsync.usecases.SynchronizeAndroidContactsUseCase
import ch.threema.app.apptaskexecutor.AppTaskExecutor
import ch.threema.app.connection.CspD2mDualConnectionSupplier
import ch.threema.app.connection.ServerConnectionManager
import ch.threema.app.emojis.EmojiRecent
import ch.threema.app.emojis.EmojiService
import ch.threema.app.emojis.search.EmojiSearchIndex
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.multidevice.MultiDeviceManagerImpl
import ch.threema.app.notifications.CallNotificationManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.preference.service.SynchronizedSettingsServiceImpl
import ch.threema.app.processors.IncomingMessageProcessorImpl
import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.protocolsteps.IdentityBlockedSteps
import ch.threema.app.protocolsteps.ValidContactsLookupSteps
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ApiService
import ch.threema.app.services.ApiServiceImpl
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.BlockedIdentitiesServiceImpl
import ch.threema.app.services.CacheService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ContactServiceImpl
import ch.threema.app.services.ConversationService
import ch.threema.app.services.ConversationServiceImpl
import ch.threema.app.services.ConversationTagService
import ch.threema.app.services.ConversationTagServiceImpl
import ch.threema.app.services.DeviceService
import ch.threema.app.services.DeviceServiceImpl
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.DistributionListServiceImpl
import ch.threema.app.services.DownloadService
import ch.threema.app.services.DownloadServiceImpl
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.app.services.ExcludedSyncIdentitiesServiceImpl
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.GroupServiceImpl
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.LifetimeServiceImpl
import ch.threema.app.services.LocaleService
import ch.threema.app.services.LocaleServiceImpl
import ch.threema.app.services.LockAppService
import ch.threema.app.services.MessageService
import ch.threema.app.services.MessageServiceImpl
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.services.ProfilePictureRecipientsServiceImpl
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.SensorService
import ch.threema.app.services.SensorServiceImpl
import ch.threema.app.services.ServerAddressProviderService
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.app.services.SynchronizeContactsServiceImpl
import ch.threema.app.services.UserService
import ch.threema.app.services.UserServiceImpl
import ch.threema.app.services.avatarcache.AvatarCacheService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.license.LicenseServiceNoOp
import ch.threema.app.services.license.LicenseServiceSerial
import ch.threema.app.services.license.LicenseServiceUser
import ch.threema.app.services.messageplayer.MessageFileDecryptionHelper
import ch.threema.app.services.messageplayer.MessagePlayerService
import ch.threema.app.services.messageplayer.MessagePlayerServiceImpl
import ch.threema.app.services.notification.BadgeUpdater
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.services.notification.NotificationServiceImpl
import ch.threema.app.services.poll.PollService
import ch.threema.app.services.poll.PollServiceImpl
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.stores.AuthTokenStore
import ch.threema.app.stores.DatabaseContactStore
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.IdentityStoreImpl
import ch.threema.app.stores.MutableIdentityProvider
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.tasks.archive.TaskArchiverImpl
import ch.threema.app.tasks.archive.recovery.TaskRecoveryManagerImpl
import ch.threema.app.tasks.getDebugString
import ch.threema.app.threemasafe.ThreemaSafeBackupCreator
import ch.threema.app.threemasafe.ThreemaSafeService
import ch.threema.app.threemasafe.ThreemaSafeServiceImpl
import ch.threema.app.typingindicator.TypingIndicatorManager
import ch.threema.app.usecases.conversation.GetConversationCategoryUseCase
import ch.threema.app.utils.AppVersionProvider.appVersion
import ch.threema.app.utils.DeviceIdProvider
import ch.threema.app.utils.DoNotDisturbUtil
import ch.threema.app.utils.ForwardSecurityStatusSender
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.app.voip.groupcall.GroupCallManagerImpl
import ch.threema.app.voip.groupcall.sfu.SfuConnection
import ch.threema.app.voip.groupcall.sfu.SfuConnectionImpl
import ch.threema.app.voip.services.VoipStateService
import ch.threema.app.wallpapers.WallpaperService
import ch.threema.app.webclient.manager.WebClientServiceManager
import ch.threema.app.webclient.services.ServicesContainer
import ch.threema.app.webclient.services.SessionWakeUpService
import ch.threema.app.widget.WidgetUpdater
import ch.threema.app.work.LegacyWorkPropertiesUpdater
import ch.threema.base.HAS_DEV_FEATURES
import ch.threema.base.ThreemaException
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.crypto.NonceStore
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.TimeProvider
import ch.threema.common.lazy
import ch.threema.data.IdentityProvider
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.ModelRepositories
import ch.threema.data.repositories.ServerMessageModelRepository
import ch.threema.domain.protocol.ChallengeResponseHelper
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.api.APIAuthenticator
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.connection.ConvertibleServerConnection
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.stores.DHSessionStore
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TaskArchiver
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.TaskManagerConfiguration
import ch.threema.domain.taskmanager.TaskManagerProvider
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.DatabaseService
import ch.threema.storage.factories.ContactModelFactory
import ch.threema.storage.factories.ConversationTagFactory
import ch.threema.storage.factories.DistributionListMemberModelFactory
import ch.threema.storage.factories.DistributionListModelFactory
import ch.threema.storage.factories.WebClientSessionModelFactory
import java.security.SecureRandom
import java.util.Locale
import java.util.function.Supplier
import kotlin.reflect.KProperty
import kotlin.time.TimeSource
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val logger = getThreemaLogger("SessionScopeContainer")

class SessionScopeContainer(
    private val appContext: Context,
    dhSessionStore: DHSessionStore,
) : KoinComponent {

    private var closed = false

    private val cacheService = CacheService()

    val serviceManager: ServiceManager by lazyWithClosedCheck {
        ServiceManager()
    }

    val identityStore: IdentityStore by lazyWithClosedCheck {
        IdentityStoreImpl(
            identityProvider = get<MutableIdentityProvider>(),
            preferenceStore = get<PreferenceStore>(),
            encryptedPreferenceStore = get<EncryptedPreferenceStore>(),
            globalEventBuses = get(),
        )
    }

    /**
     * The task manager. Note that this must only be used to schedule tasks when the task archiver
     * has access to the service manager.
     */
    val taskManager: TaskManager
        get() = taskManagerOverride ?: _taskManager

    private val _taskManager: TaskManager by lazyWithClosedCheck {
        TaskManagerProvider.getTaskManager(
            TaskManagerConfiguration(
                taskArchiver = { taskArchiver },
                deviceCookieManager = get(),
                assertContext = HAS_DEV_FEATURES,
                getDebugString = getDebugString,
            ),
        )
    }

    private val taskArchiver: TaskArchiver by lazyWithClosedCheck {
        TaskArchiverImpl(
            taskArchiveFactory = get(),
            taskRecoveryManager = TaskRecoveryManagerImpl(),
            getDebugString = getDebugString,
        )
    }

    val multiDeviceManager: MultiDeviceManager
        get() = multiDeviceManagerImpl

    val multiDeviceManagerImpl: MultiDeviceManagerImpl by lazyWithClosedCheck {
        MultiDeviceManagerImpl(
            preferenceStore = get<PreferenceStore>(),
            encryptedPreferenceStore = get<EncryptedPreferenceStore>(),
            serverMessageModelRepository = get<ServerMessageModelRepository>(),
            version = get<Version>(),
        )
    }

    val nonceStore: NonceStore by lazyWithClosedCheck {
        val databaseNonceStore = DatabaseNonceStore(appContext, identityStore)
        databaseNonceStore.migrateIfNeeded()
        databaseNonceStore
    }

    val nonceFactory: NonceFactory by lazyWithClosedCheck {
        val databaseNonceStore = nonceStore
        logger.info("Nonce count (csp): {}", databaseNonceStore.getCount(NonceScope.CSP))
        logger.info("Nonce count (d2d): {}", databaseNonceStore.getCount(NonceScope.D2D))
        NonceFactory(databaseNonceStore)
    }

    val modelRepositories by lazyWithClosedCheck {
        ModelRepositories(
            databaseProvider = get<DatabaseProvider>(),
            identityProvider = get<IdentityProvider>(),
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
            nonceFactory = nonceFactory,
            globalEventBuses = get<GlobalEventBuses>(),
            globalEventFlows = get<GlobalEventFlows>(),
            dispatcherProvider = get<DispatcherProvider>(),
            timeProvider = get<TimeProvider>(),
        )
    }

    val connection: ServerConnection
        get() = convertibleServerConnection

    val serverConnectionManager: ServerConnectionManager by lazyWithClosedCheck {
        ServerConnectionManager(
            identityProvider = get<IdentityProvider>(),
            connection = convertibleServerConnection,
            multiDeviceManager = multiDeviceManager,
        )
    }

    val convertibleServerConnection: ConvertibleServerConnection by lazyWithClosedCheck {
        val connectionSupplier: Supplier<ServerConnection> = CspD2mDualConnectionSupplier(
            powerManager = appContext.getSystemService<PowerManager>()!!,
            multiDeviceManager = multiDeviceManager,
            incomingMessageProcessor = IncomingMessageProcessorImpl(
                serviceManager = get<ServiceManager>(),
                serverMessageModelRepository = get<ServerMessageModelRepository>(),
                globalEventBuses = get<GlobalEventBuses>(),
                typingIndicatorManager = get<TypingIndicatorManager>(),
            ),
            taskManager = taskManager,
            deviceCookieManager = get<DeviceCookieManager>(),
            serverAddressProviderService = serverAddressProviderService,
            identityStore = identityStore,
            version = get<Version>(),
            okHttpClient = get<OkHttpClient>(),
            appStartupMonitor = get<AppStartupMonitor>(),
            isTestBuild = HAS_DEV_FEATURES,
        )
        ConvertibleServerConnection(connectionSupplier)
    }

    val contactStore: DatabaseContactStore by lazyWithClosedCheck {
        DatabaseContactStore(
            get<ContactModelFactory>(),
        )
    }

    val contactService: ContactService by lazyWithClosedCheck {
        ContactServiceImpl(
            appContext,
            contactStore,
            get<AvatarCacheService>(),
            get<DatabaseService>(),
            get<DatabaseProvider>(),
            userService,
            identityStore,
            get<PreferenceService>(),
            synchronizedSettingsService,
            blockedIdentitiesService,
            profilePicRecipientsService,
            get<FileService>(),
            cacheService,
            apiConnector,
            modelRepositories.contacts,
            taskCreator,
            multiDeviceManager,
            get<GlobalEventBuses>(),
        )
    }

    val userService: UserService by lazyWithClosedCheck {
        UserServiceImpl(
            appContext,
            get<PreferenceStore>(),
            localeService,
            apiConnector,
            apiService,
            get<FileService>(),
            identityStore,
            get<PreferenceService>(),
            synchronizedSettingsService,
            taskManager,
            taskCreator,
            multiDeviceManager,
            get<DeviceIdProvider>(),
            get<GlobalEventBuses>(),
            get<LegacyWorkPropertiesUpdater>(),
        )
            .apply {
                // TODO(ANDR-2519): Remove when md allows fs
                setForwardSecurityEnabled(multiDeviceManager.isMdDisabledOrSupportsFs)
            }
    }

    @get:Throws(ThreemaException::class)
    val messageService: MessageService by lazyWithClosedCheck {
        MessageServiceImpl(
            appContext,
            cacheService,
            get<DatabaseService>(),
            contactService,
            get<FileService>(),
            identityStore,
            get<SymmetricEncryptionService>(),
            get<PreferenceService>(),
            synchronizedSettingsService,
            pollService,
            groupService,
            apiService,
            downloadService,
            get<GetConversationCategoryUseCase>(),
            blockedIdentitiesService,
            multiDeviceManager,
            modelRepositories.editHistory,
            modelRepositories.emojiReaction,
            get<ContactModelRepository>(),
            get<GlobalEventBuses>(),
        )
    }

    val localeService: LocaleService by lazyWithClosedCheck {
        LocaleServiceImpl(appContext)
    }

    val deviceService: DeviceService by lazyWithClosedCheck {
        DeviceServiceImpl(appContext)
    }

    val lifetimeService: LifetimeService
        get() = lifetimeServiceOverride ?: _lifetimeService
    private val _lifetimeService: LifetimeService by lazyWithClosedCheck {
        LifetimeServiceImpl(
            appContext = appContext,
            serverConnectionManager = serverConnectionManager,
            downloadService = downloadService,
            taskManager = taskManager,
            globalAppState = get<GlobalAppState>(),
            timeSource = get<TimeSource.WithComparableMarks>(),
            dispatcherProvider = get<DispatcherProvider>(),
        )
    }

    val licenseService: LicenseService<*> by lazyWithClosedCheck {
        when (BuildFlavor.licenseType) {
            BuildFlavor.LicenseType.SERIAL,
            -> LicenseServiceSerial(
                apiConnector,
                get<PreferenceService>(),
                get<DeviceIdProvider>().getDeviceId(),
            )
            BuildFlavor.LicenseType.WORK,
            -> LicenseServiceUser(
                apiConnector,
                get<PreferenceService>(),
                get<DeviceIdProvider>().getDeviceId(),
            )
            BuildFlavor.LicenseType.GOOGLE,
            BuildFlavor.LicenseType.HMS,
            BuildFlavor.LicenseType.NONE,
            -> LicenseServiceNoOp()
        }
    }

    val groupService: GroupService by lazyWithClosedCheck {
        GroupServiceImpl(
            appContext,
            cacheService,
            userService,
            contactService,
            get<DatabaseService>(),
            get<AvatarCacheService>(),
            get<FileService>(),
            get<WallpaperService>(),
            get<RingtoneService>(),
            conversationTagService,
            get<PreferenceService>(),
            modelRepositories.contacts,
            modelRepositories.groups,
            get<ServiceManager>(),
            get<GlobalEventBuses>(),
        )
    }

    val groupProfilePictureUploader: GroupProfilePictureUploader by lazyWithClosedCheck {
        GroupProfilePictureUploader(
            apiService = apiService,
            secureRandom = get<SecureRandom>(),
        )
    }

    val apiService: ApiService by lazyWithClosedCheck {
        ApiServiceImpl(
            apiConnector,
            AuthTokenStore(),
            serverAddressProviderService.serverAddressProvider,
            multiDeviceManager,
            get<OkHttpClient>(qualifier = Qualifiers.okHttpBlobDownload),
            get<OkHttpClient>(qualifier = Qualifiers.okHttpBlobUpload),
        )
    }

    @get:Throws(ThreemaException::class)
    val conversationService: ConversationService by lazyWithClosedCheck {
        ConversationServiceImpl(
            cacheService,
            get<DatabaseService>(),
            get<DatabaseProvider>(),
            contactService,
            groupService,
            distributionListService,
            messageService,
            blockedIdentitiesService,
            conversationTagService,
            get<PreferenceService>(),
            get<GlobalEventBuses>(),
        )
    }

    val notificationService: NotificationService by lazyWithClosedCheck {
        NotificationServiceImpl(
            appContext = appContext,
            notificationManagerCompat = get<NotificationManagerCompat>(),
            getConversationCategoryUseCase = get<GetConversationCategoryUseCase>(),
            lockAppService = get<LockAppService>(),
            notificationPreferenceService = get<NotificationPreferenceService>(),
            ringtoneService = get<RingtoneService>(),
            preferenceService = get<PreferenceService>(),
            identityProvider = get<IdentityProvider>(),
            badgeUpdater = get<BadgeUpdater>(),
            doNotDisturbUtil = get<DoNotDisturbUtil>(),
            timeProvider = get<TimeProvider>(),
            widgetUpdater = get<WidgetUpdater>(),
        )
    }

    val synchronizeContactsService: SynchronizeContactsService by lazyWithClosedCheck {
        SynchronizeContactsServiceImpl(
            contactService,
            modelRepositories.contacts,
            userService,
            synchronizedSettingsService,
            deviceService,
            get<SynchronizeAndroidContactsUseCase>(),
        )
    }

    val blockedIdentitiesService: BlockedIdentitiesService by lazyWithClosedCheck {
        BlockedIdentitiesServiceImpl(
            preferenceService = get<PreferenceService>(),
            multiDeviceManager = multiDeviceManager,
            taskCreator = taskCreator,
            globalEventBuses = get<GlobalEventBuses>(),
        )
    }

    val excludedSyncIdentitiesService: ExcludedSyncIdentitiesService by lazyWithClosedCheck {
        ExcludedSyncIdentitiesServiceImpl(
            preferenceService = get<PreferenceService>(),
            multiDeviceManager = multiDeviceManager,
            taskCreator = taskCreator,
        )
    }

    val distributionListService: DistributionListService by lazyWithClosedCheck {
        DistributionListServiceImpl(
            appContext,
            get<AvatarCacheService>(),
            get<DatabaseService>(),
            contactService,
            conversationTagService,
            get<PreferenceService>(),
            get<GlobalEventBuses>(),
        )
    }

    @get:Throws(ThreemaException::class)
    val messagePlayerService: MessagePlayerService by lazyWithClosedCheck {
        MessagePlayerServiceImpl(
            appContext,
            messageService,
            get<PreferenceService>(),
            get<NotificationPreferenceService>(),
            get<GlobalEventBuses>(),
            messageFileDecryptionHelper,
        )
    }

    private val messageFileDecryptionHelper: MessageFileDecryptionHelper by lazyWithClosedCheck {
        MessageFileDecryptionHelper(
            fileService = get(),
            dispatcherProvider = get(),
        )
    }

    val downloadService: DownloadService by lazyWithClosedCheck {
        DownloadServiceImpl(
            appContext,
            apiService,
        )
    }

    val pollService: PollService by lazyWithClosedCheck {
        PollServiceImpl(
            cacheService.pollModelCache,
            cacheService.linkPollModelCache,
            get<DatabaseService>(),
            userService,
            groupService,
            contactService,
            get<ServiceManager>(),
            get<GlobalEventBuses>(),
        )
    }

    val threemaSafeService: ThreemaSafeService by lazyWithClosedCheck {
        ThreemaSafeServiceImpl(
            appContext,
            get<PreferenceService>(),
            synchronizedSettingsService,
            userService,
            contactService,
            localeService,
            blockedIdentitiesService,
            excludedSyncIdentitiesService,
            profilePicRecipientsService,
            get<DistributionListModelFactory>(),
            get<DistributionListMemberModelFactory>(),
            identityStore,
            apiService,
            apiConnector,
            modelRepositories.contacts,
            modelRepositories.groups,
            get<OkHttpClient>(),
            get<AppRestrictions>(),
            get<ValidContactsLookupSteps>(),
            get<ThreemaSafeBackupCreator>(),
        )
    }

    val sensorService: SensorService by lazyWithClosedCheck {
        SensorServiceImpl(appContext)
    }

    @get:Throws(ThreemaException::class)
    val voipStateService: VoipStateService by lazyWithClosedCheck {
        VoipStateService(
            contactService,
            modelRepositories.contacts,
            get<CallNotificationManager>(),
            lifetimeService,
            get<GlobalEventBuses>(),
            appContext,
        )
    }

    @get:Throws(ThreemaException::class)
    val groupCallManager: GroupCallManager by lazyWithClosedCheck {
        GroupCallManagerImpl(
            appContext = appContext,
            serviceManager = get<ServiceManager>(),
            databaseService = get<DatabaseService>(),
            groupService = groupService,
            contactService = contactService,
            contactModelRepository = modelRepositories.contacts,
            identityProvider = get<IdentityProvider>(),
            preferenceService = get<PreferenceService>(),
            messageService = messageService,
            notificationService = notificationService,
            sfuConnection = sfuConnection,
        )
    }

    val sfuConnection: SfuConnection by lazyWithClosedCheck {
        SfuConnectionImpl(
            apiConnector = apiConnector,
            identityStore = identityStore,
            okHttpClient = get<OkHttpClient>(),
            version = appVersion,
        )
    }

    val conversationTagService: ConversationTagService by lazyWithClosedCheck {
        ConversationTagServiceImpl(get<ConversationTagFactory>())
    }

    val serverAddressProviderService: ServerAddressProviderService by lazyWithClosedCheck {
        object : ServerAddressProviderService {
            override val serverAddressProvider: ServerAddressProvider by lazyWithClosedCheck {
                get<ServerAddressProvider>()
            }
        }
    }

    @get:Throws(ThreemaException::class)
    val webClientServiceManager: WebClientServiceManager by lazyWithClosedCheck {
        WebClientServiceManager(
            ServicesContainer(
                appContext,
                lifetimeService,
                contactService,
                groupService,
                distributionListService,
                conversationService,
                conversationTagService,
                messageService,
                notificationService,
                get<ContactModelFactory>(),
                get<WebClientSessionModelFactory>(),
                blockedIdentitiesService,
                get<PreferenceService>(),
                userService,
                get<FileService>(),
                messageFileDecryptionHelper,
                synchronizeContactsService,
                licenseService,
                get<SessionWakeUpService>(),
                modelRepositories.contacts,
                modelRepositories.groups,
                groupFlowDispatcher,
                get<AppRestrictions>(),
            ),
        )
    }

    val emojiService: EmojiService by lazyWithClosedCheck {
        val searchIndex = EmojiSearchIndex(
            context = appContext,
            preferenceService = get<PreferenceService>(),
        )
        EmojiService(
            preferenceService = get<PreferenceService>(),
            searchIndex = searchIndex,
            recentEmojis = EmojiRecent(get<PreferenceService>()),
        )
    }

    val taskCreator: TaskCreator by lazyWithClosedCheck {
        TaskCreator(get<ServiceManager>())
    }

    @get:Throws(ThreemaException::class)
    val groupFlowDispatcher: GroupFlowDispatcher by lazyWithClosedCheck {
        GroupFlowDispatcher(
            contactModelRepository = modelRepositories.contacts,
            groupModelRepository = modelRepositories.groups,
            contactService = contactService,
            groupService = groupService,
            groupCallManager = groupCallManager,
            userService = userService,
            contactStore = contactStore,
            identityStore = identityStore,
            identityProvider = get<IdentityProvider>(),
            forwardSecurityMessageProcessor = forwardSecurityMessageProcessor,
            nonceFactory = nonceFactory,
            preferenceService = get<PreferenceService>(),
            synchronizedSettingsService = synchronizedSettingsService,
            multiDeviceManager = multiDeviceManager,
            groupProfilePictureUploader = groupProfilePictureUploader,
            apiConnector = apiConnector,
            fileService = get<FileService>(),
            databaseService = get<DatabaseService>(),
            taskManager = taskManager,
            appTaskExecutor = get<AppTaskExecutor>(),
            connection = connection,
            identityBlockedSteps = get<IdentityBlockedSteps>(),
            globalEventBuses = get<GlobalEventBuses>(),
            blockedIdentitiesService = blockedIdentitiesService,
        )
    }

    val apiConnector: APIConnector by lazyWithClosedCheck {
        APIConnector(
            serverAddressProviderService.serverAddressProvider,
            get<ChallengeResponseHelper>(),
            BuildFlavor.IS_WORK,
            get<OkHttpClient>(),
            appVersion,
            Locale.getDefault().language,
            getOrNull<APIAuthenticator>(),
        )
    }

    val synchronizedSettingsService: SynchronizedSettingsService by lazyWithClosedCheck {
        SynchronizedSettingsServiceImpl(
            appContext = appContext,
            preferenceStore = get<PreferenceStore>(),
            taskManager = taskManager,
            multiDeviceManager = multiDeviceManager,
        )
    }

    val profilePicRecipientsService: ProfilePictureRecipientsService by lazyWithClosedCheck {
        ProfilePictureRecipientsServiceImpl(get<PreferenceService>())
    }

    val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor by lazyWithClosedCheck {
        ForwardSecurityMessageProcessor(
            dhSessionStore = dhSessionStore,
            contactStore = contactStore,
            identityStore = identityStore,
            nonceFactory = nonceFactory,
            statusListener = ForwardSecurityStatusSender(
                contactService,
                messageService,
                apiConnector,
                userService,
                modelRepositories.contacts,
            ),
        )
            .apply {
                // TODO(ANDR-2519): Remove when md allows fs
                setForwardSecurityEnabled(multiDeviceManager.isMdDisabledOrSupportsFs)
            }
    }

    val dhSessionStore: DHSessionStore = dhSessionStore
        get() {
            ensureNotClosed()
            return field
        }

    fun close() {
        closed = true
    }

    private fun ensureNotClosed() {
        check(!closed) { "ServiceManager is closed" }
    }

    private fun <T> lazyWithClosedCheck(getValue: () -> T) =
        Delegate(::ensureNotClosed, getValue)

    private class Delegate<T>(val ensureNotClosed: () -> Unit, val getValue: () -> T) {
        private val cachedValue by lazy {
            getValue()
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            ensureNotClosed()
            return cachedValue
        }
    }

    // TODO(ANDR-4336): The following overrides exist only as workarounds for device tests.
    //  In the long run, this hack should be replaced with proper mocking of dependencies.
    @VisibleForTesting
    var lifetimeServiceOverride: LifetimeService? = null

    @VisibleForTesting
    var taskManagerOverride: TaskManager? = null
}

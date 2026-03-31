package ch.threema.app.managers;

import android.content.Context;
import android.os.PowerManager;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.security.SecureRandom;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.BuildFlavor;
import ch.threema.app.androidcontactsync.usecases.UpdateContactNameUseCase;
import ch.threema.app.apptaskexecutor.AppTaskExecutor;
import ch.threema.app.backuprestore.BackupChatService;
import ch.threema.app.backuprestore.BackupChatServiceImpl;
import ch.threema.app.connection.CspD2mDualConnectionSupplier;
import ch.threema.app.notifications.CallNotificationManager;
import ch.threema.app.preference.service.SynchronizedSettingsService;
import ch.threema.app.preference.service.SynchronizedSettingsServiceImpl;
import ch.threema.app.protocolsteps.IdentityBlockedSteps;
import ch.threema.app.restrictions.AppRestrictions;
import ch.threema.app.startup.AppStartupMonitor;
import ch.threema.app.stores.IdentityProvider;
import ch.threema.base.SessionScoped;
import ch.threema.app.emojis.EmojiRecent;
import ch.threema.app.emojis.EmojiService;
import ch.threema.app.emojis.search.EmojiSearchIndex;
import ch.threema.app.exceptions.NoIdentityException;
import ch.threema.app.files.WallpaperFileHandleProvider;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.onprem.OnPremConfigFetcherProvider;
import ch.threema.app.onprem.OnPremServerAddressProvider;
import ch.threema.app.processors.IncomingMessageProcessorImpl;
import ch.threema.app.profilepicture.GroupProfilePictureUploader;
import ch.threema.app.services.ActivityService;
import ch.threema.app.services.ApiService;
import ch.threema.app.services.ApiServiceImpl;
import ch.threema.app.services.avatarcache.AvatarCacheService;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.BlockedIdentitiesServiceImpl;
import ch.threema.app.services.CacheService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ContactServiceImpl;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.ConversationCategoryServiceImpl;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationServiceImpl;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.ConversationTagServiceImpl;
import ch.threema.app.services.DefaultServerAddressProvider;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.DeviceServiceImpl;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.DistributionListServiceImpl;
import ch.threema.app.services.DownloadService;
import ch.threema.app.services.DownloadServiceImpl;
import ch.threema.app.services.ExcludedSyncIdentitiesService;
import ch.threema.app.services.ExcludedSyncIdentitiesServiceImpl;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.GroupServiceImpl;
import ch.threema.app.services.ProfilePictureRecipientsService;
import ch.threema.app.services.ProfilePictureRecipientsServiceImpl;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.LifetimeServiceImpl;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.LocaleServiceImpl;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.services.notification.NotificationServiceImpl;
import ch.threema.app.services.PinLockService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.services.SensorService;
import ch.threema.app.services.SensorServiceImpl;
import ch.threema.app.services.ServerAddressProviderService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.SynchronizeContactsServiceImpl;
import ch.threema.app.services.UserService;
import ch.threema.app.services.UserServiceImpl;
import ch.threema.app.services.WallpaperService;
import ch.threema.app.services.WallpaperServiceImpl;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.ballot.BallotServiceImpl;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceSerial;
import ch.threema.app.services.license.LicenseServiceUser;
import ch.threema.app.services.messageplayer.MessagePlayerService;
import ch.threema.app.services.messageplayer.MessagePlayerServiceImpl;
import ch.threema.app.stores.AuthTokenStore;
import ch.threema.app.stores.DatabaseContactStore;
import ch.threema.app.stores.EncryptedPreferenceStore;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.tasks.TaskCreator;
import ch.threema.app.threemasafe.ThreemaSafeService;
import ch.threema.app.threemasafe.ThreemaSafeServiceImpl;
import ch.threema.app.utils.AppVersionProvider;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DeviceIdProvider;
import ch.threema.app.utils.ForwardSecurityStatusSender;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.app.voip.groupcall.GroupCallManagerImpl;
import ch.threema.app.voip.groupcall.sfu.SfuConnection;
import ch.threema.app.voip.groupcall.sfu.SfuConnectionImpl;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.app.webclient.manager.WebClientServiceManager;
import ch.threema.app.webclient.services.ServicesContainer;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.crypto.SymmetricEncryptionService;

import static ch.threema.app.dev.UtilsKt.hasDevFeatures;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.data.repositories.ModelRepositories;
import ch.threema.domain.models.LicenseCredentials;
import ch.threema.domain.models.UserCredentials;
import ch.threema.domain.protocol.api.APIAuthenticator;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.connection.ConvertibleServerConnection;
import ch.threema.domain.protocol.connection.ServerConnection;
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.domain.taskmanager.IncomingMessageProcessor;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;
import ch.threema.localcrypto.MasterKeyProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.factories.ConversationTagFactory;
import ch.threema.storage.factories.ServerMessageModelFactory;
import ch.threema.storage.factories.WebClientSessionModelFactory;
import java.util.function.Supplier;
import kotlin.Lazy;
import okhttp3.OkHttpClient;

import static ch.threema.common.LazyKt.lazy;

@Deprecated
@SessionScoped
public class ServiceManager {
    private static final Logger logger = getThreemaLogger("ServiceManager");

    @NonNull
    private final Context appContext;
    @NonNull
    private final CoreServiceManager coreServiceManager;
    @NonNull
    private final Lazy<Boolean> isIpv6Preferred;
    @NonNull
    private final MasterKeyProvider masterKeyProvider;
    @NonNull
    private final CacheService cacheService;
    @Nullable
    private DatabaseContactStore contactStore;
    @Nullable
    private APIConnector apiConnector;
    @Nullable
    private ContactService contactService;
    @Nullable
    private UserService userService;
    @Nullable
    private MessageService messageService;
    private final Lazy<SynchronizedSettingsService> synchronizedSettingsServiceLazy = lazy(this::createSynchronizedSettingsService);
    @Nullable
    private LocaleService localeService;
    @Nullable
    private DeviceService deviceService;
    @Nullable
    private LifetimeService lifetimeService;
    @Nullable
    private LicenseService<?> licenseService;
    @Nullable
    private GroupService groupService;
    @Nullable
    private GroupProfilePictureUploader groupProfilePictureUploader;
    @Nullable
    private LockAppService lockAppService;
    @Nullable
    private ActivityService activityService;
    @Nullable
    private ApiService apiService;
    @Nullable
    private ConversationService conversationService;
    @Nullable
    private NotificationService notificationService;
    @Nullable
    private SynchronizeContactsService synchronizeContactsService;

    @Nullable
    private BlockedIdentitiesService blockedIdentitiesService;
    @Nullable
    private ExcludedSyncIdentitiesService excludedSyncIdentitiesService;
    @Nullable
    private ProfilePictureRecipientsService profilePictureRecipientsService;
    @Nullable
    private ConversationCategoryService conversationCategoryService;
    @Nullable
    private DistributionListService distributionListService;
    @Nullable
    private IncomingMessageProcessor incomingMessageProcessor;
    @Nullable
    private MessagePlayerService messagePlayerService = null;
    @Nullable
    private DownloadServiceImpl downloadService;
    @Nullable
    private BallotService ballotService;
    @Nullable
    private WallpaperService wallpaperService;
    @Nullable
    private ThreemaSafeService threemaSafeService;
    @Nullable
    private BackupChatService backupChatService;
    @NonNull
    private final ModelRepositories modelRepositories;
    @Nullable
    private SensorService sensorService;
    @Nullable
    private VoipStateService voipStateService;
    @Nullable
    private GroupCallManager groupCallManager;
    @Nullable
    private SfuConnection sfuConnection;
    @Nullable
    private ConversationTagServiceImpl conversationTagService;
    @Nullable
    private ServerAddressProviderService serverAddressProviderService;
    @Nullable
    private WebClientServiceManager webClientServiceManager;

    @NonNull
    private final DHSessionStoreInterface dhSessionStore;

    @Nullable
    private ForwardSecurityMessageProcessor forwardSecurityMessageProcessor;

    @Nullable
    private SymmetricEncryptionService symmetricEncryptionService;

    @Nullable
    private EmojiService emojiService;

    @Nullable
    private TaskCreator taskCreator;

    @Nullable
    private GroupFlowDispatcher groupFlowDispatcher;

    @NonNull
    private final ConvertibleServerConnection connection;

    private boolean closed = false;

    public ServiceManager(
        @NonNull Context appContext,
        @NonNull ModelRepositories modelRepositories,
        @NonNull DHSessionStoreInterface dhSessionStore,
        @NonNull MasterKeyProvider masterKeyProvider,
        @NonNull CoreServiceManagerImpl coreServiceManager
    ) throws ThreemaException {
        this.appContext = appContext;
        this.cacheService = new CacheService();
        this.coreServiceManager = coreServiceManager;
        this.isIpv6Preferred = lazy(() -> getPreferenceService().isIpv6Preferred());
        this.masterKeyProvider = masterKeyProvider;
        this.modelRepositories = modelRepositories;
        this.dhSessionStore = dhSessionStore;
        // Finalize initialization of device cookie manager before the connection is created.
        coreServiceManager.getDeviceCookieManager().setNotificationService(getNotificationService());
        this.connection = createServerConnection();
        coreServiceManager.getMultiDeviceManager().setReconnectHandle(connection);
    }

    @NonNull
    public DatabaseContactStore getContactStore() {
        ensureNotClosed();
        if (this.contactStore == null) {
            this.contactStore = new DatabaseContactStore(
                KoinJavaComponent.get(DatabaseService.class),
                this.getServerAddressProviderService().getServerAddressProvider()
            );
        }

        return this.contactStore;
    }

    @NonNull
    public APIConnector getAPIConnector() {
        ensureNotClosed();
        if (this.apiConnector == null) {
            try {
                APIAuthenticator authenticator = null;
                if (BuildFlavor.getCurrent().getLicenseType() == BuildFlavor.LicenseType.ONPREM) {
                    // On Premise always requires authentication
                    PreferenceService preferenceService = this.getPreferenceService();
                    authenticator = () -> {
                        var username = preferenceService.getLicenseUsername();
                        var password = preferenceService.getLicensePassword();
                        if (username != null && password != null) {
                            return new UserCredentials(username, password);
                        }
                        return null;
                    };
                }

                this.apiConnector = new APIConnector(
                    isIpv6Preferred.getValue(),
                    this.getServerAddressProviderService().getServerAddressProvider(),
                    ConfigUtils.isWorkBuild(),
                    getOkHttpClient(),
                    AppVersionProvider.getAppVersion(),
                    Locale.getDefault().getLanguage(),
                    authenticator
                );

            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }

        return this.apiConnector;
    }

    /**
     * Start the server connection. Do not call this directly; use the LifetimeService!
     */
    public void startConnection() throws ThreemaException {
        ensureNotClosed();
        logger.trace("startConnection");

        String currentIdentity = this.coreServiceManager.getIdentityStore().getIdentityString();
        if (currentIdentity == null || currentIdentity.isEmpty()) {
            throw new NoIdentityException();
        }

        if (masterKeyProvider.isLocked()) {
            throw new MasterKeyLockedException();
        }

        logger.info("Starting connection");
        this.connection.start();
    }

    @NonNull
    public PreferenceStore getPreferenceStore() {
        ensureNotClosed();
        return coreServiceManager.getPreferenceStore();
    }

    @NonNull
    public EncryptedPreferenceStore getEncryptedPreferenceStore() {
        ensureNotClosed();
        return coreServiceManager.getEncryptedPreferenceStore();
    }

    /**
     * Stop the connection. Do not call this directly; use the LifetimeService!
     */
    public void stopConnection() throws InterruptedException {
        logger.info("Stopping connection");
        InterruptedException interrupted = null;
        try {
            this.connection.stop();
        } catch (InterruptedException e) {
            logger.error("Interrupted while stopping connection");
            interrupted = e;
        }

        // Re-set interrupted flag
        if (interrupted != null) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    @NonNull
    public UserService getUserService() {
        ensureNotClosed();
        if (this.userService == null) {
            try {
                this.userService = new UserServiceImpl(
                    appContext,
                    this.coreServiceManager.getPreferenceStore(),
                    this.getLocaleService(),
                    this.getAPIConnector(),
                    this.getApiService(),
                    this.getFileService(),
                    this.getIdentityStore(),
                    this.getPreferenceService(),
                    this.getSynchronizedSettingsService(),
                    this.getTaskManager(),
                    this.getTaskCreator(),
                    this.getMultiDeviceManager(),
                    getDeviceIdProvider()
                );
                // TODO(ANDR-2519): Remove when md allows fs
                this.userService.setForwardSecurityEnabled(getMultiDeviceManager().isMdDisabledOrSupportsFs());
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }

        return this.userService;
    }

    public @NonNull ContactService getContactService() throws MasterKeyLockedException {
        ensureNotClosed();
        if (this.contactService == null) {
            if (masterKeyProvider.isLocked()) {
                throw new MasterKeyLockedException();
            }
            this.contactService = new ContactServiceImpl(
                appContext,
                this.getContactStore(),
                this.getAvatarCacheService(),
                KoinJavaComponent.get(DatabaseService.class),
                KoinJavaComponent.get(DatabaseProvider.class),
                this.getUserService(),
                this.getIdentityStore(),
                this.getPreferenceService(),
                this.getSynchronizedSettingsService(),
                this.getBlockedIdentitiesService(),
                this.getProfilePicRecipientsService(),
                this.getFileService(),
                this.cacheService,
                this.getLicenseService(),
                this.getAPIConnector(),
                this.getModelRepositories().getContacts(),
                this.getTaskCreator(),
                this.getMultiDeviceManager()
            );
        }

        return this.contactService;
    }

    @NonNull
    public MessageService getMessageService() throws ThreemaException {
        ensureNotClosed();
        if (this.messageService == null) {
            this.messageService = new MessageServiceImpl(
                appContext,
                this.cacheService,
                KoinJavaComponent.get(DatabaseService.class),
                this.getContactService(),
                this.getFileService(),
                this.getIdentityStore(),
                this.getSymmetricEncryptionService(),
                this.getPreferenceService(),
                this.getSynchronizedSettingsService(),
                this.getLockAppService(),
                this.getBallotService(),
                this.getGroupService(),
                this.getApiService(),
                this.getDownloadService(),
                this.getConversationCategoryService(),
                this.getBlockedIdentitiesService(),
                this.getMultiDeviceManager(),
                this.getModelRepositories().getEditHistory(),
                this.getModelRepositories().getEmojiReaction(),
                KoinJavaComponent.get(ServerMessageModelFactory.class)
            );
        }

        return this.messageService;
    }

    @NonNull
    public PreferenceService getPreferenceService() {
        ensureNotClosed();
        return KoinJavaComponent.get(PreferenceService.class);
    }

    @NonNull
    public SynchronizedSettingsService getSynchronizedSettingsService() {
        ensureNotClosed();
        return synchronizedSettingsServiceLazy.getValue();
    }

    @NonNull
    private NotificationPreferenceService getNotificationPreferenceService() {
        ensureNotClosed();
        return KoinJavaComponent.get(NotificationPreferenceService.class);
    }

    @NonNull
    public FileService getFileService() {
        ensureNotClosed();
        return KoinJavaComponent.get(FileService.class);
    }

    @NonNull
    public LocaleService getLocaleService() {
        ensureNotClosed();
        if (this.localeService == null) {
            this.localeService = new LocaleServiceImpl(appContext);
        }

        return this.localeService;
    }

    @NonNull
    public ServerConnection getConnection() {
        ensureNotClosed();
        return this.connection;
    }

    @NonNull
    public DeviceService getDeviceService() {
        ensureNotClosed();
        if (this.deviceService == null) {
            this.deviceService = new DeviceServiceImpl(appContext);
        }

        return this.deviceService;
    }

    @NonNull
    public LifetimeService getLifetimeService() {
        ensureNotClosed();
        if (this.lifetimeService == null) {
            this.lifetimeService = new LifetimeServiceImpl(appContext);
        }

        return this.lifetimeService;
    }

    @NonNull
    public AvatarCacheService getAvatarCacheService() {
        ensureNotClosed();
        return KoinJavaComponent.get(AvatarCacheService.class);
    }

    @NonNull
    public LicenseService getLicenseService() {
        ensureNotClosed();
        if (this.licenseService == null) {
            switch (BuildFlavor.getCurrent().getLicenseType()) {
                case SERIAL:
                    this.licenseService = new LicenseServiceSerial(
                        this.getAPIConnector(),
                        this.getPreferenceService(),
                        getDeviceIdProvider().getDeviceId()
                    );
                    break;
                case GOOGLE_WORK:
                case HMS_WORK:
                case ONPREM:
                    this.licenseService = new LicenseServiceUser(
                        this.getAPIConnector(),
                        this.getPreferenceService(),
                        getDeviceIdProvider().getDeviceId()
                    );
                    break;
                default:
                    this.licenseService = new LicenseService<>() {
                        @Override
                        public String validate(LicenseCredentials credentials) {
                            return null;
                        }

                        @Override
                        public String validate(boolean allowException) {
                            return null;
                        }

                        @Override
                        public boolean hasCredentials() {
                            return false;
                        }

                        @Override
                        public boolean isLicensed() {
                            return true;
                        }

                        @Override
                        public LicenseCredentials loadCredentials() {
                            return null;
                        }
                    };
            }
        }

        return this.licenseService;
    }

    @NonNull
    private DeviceIdProvider getDeviceIdProvider() {
        ensureNotClosed();
        return KoinJavaComponent.get(DeviceIdProvider.class);
    }

    @NonNull
    public LockAppService getLockAppService() {
        ensureNotClosed();
        if (null == this.lockAppService) {
            this.lockAppService = new PinLockService(
                appContext,
                this.getPreferenceService(),
                this.getUserService()
            );
        }

        return this.lockAppService;
    }

    @NonNull
    public ActivityService getActivityService() {
        ensureNotClosed();
        if (null == this.activityService) {
            this.activityService = new ActivityService(
                appContext,
                this.getLockAppService(),
                this.getPreferenceService(),
                masterKeyProvider
            );
        }
        return this.activityService;
    }

    @NonNull
    public GroupService getGroupService() throws MasterKeyLockedException {
        ensureNotClosed();
        if (null == this.groupService) {
            this.groupService = new GroupServiceImpl(
                appContext,
                this.cacheService,
                this.getUserService(),
                this.getContactService(),
                KoinJavaComponent.get(DatabaseService.class),
                this.getAvatarCacheService(),
                this.getFileService(),
                this.getWallpaperService(),
                this.getConversationCategoryService(),
                this.getRingtoneService(),
                this.getConversationTagService(),
                this.getPreferenceService(),
                this.getModelRepositories().getContacts(),
                this.getModelRepositories().getGroups(),
                this
            );
        }
        return this.groupService;
    }

    @NonNull
    public GroupProfilePictureUploader getGroupProfilePictureUploader() {
        ensureNotClosed();
        if (groupProfilePictureUploader == null) {
            groupProfilePictureUploader = new GroupProfilePictureUploader(
                getApiService(),
                KoinJavaComponent.get(SecureRandom.class)
            );
        }
        return groupProfilePictureUploader;
    }

    @NonNull
    public ApiService getApiService() {
        ensureNotClosed();
        if (null == this.apiService) {
            this.apiService = new ApiServiceImpl(
                AppVersionProvider.getAppVersion(),
                isIpv6Preferred.getValue(),
                this.getAPIConnector(),
                new AuthTokenStore(),
                this.getServerAddressProviderService().getServerAddressProvider(),
                this.getMultiDeviceManager(),
                this.getOkHttpClient()
            );
        }
        return this.apiService;
    }

    @NonNull
    public DistributionListService getDistributionListService() throws MasterKeyLockedException, NoIdentityException {
        ensureNotClosed();
        if (null == this.distributionListService) {
            this.distributionListService = new DistributionListServiceImpl(
                appContext,
                this.getAvatarCacheService(),
                KoinJavaComponent.get(DatabaseService.class),
                this.getContactService(),
                this.getConversationTagService(),
                this.getPreferenceService()
            );
        }

        return this.distributionListService;
    }

    @NonNull
    public ConversationTagService getConversationTagService() {
        ensureNotClosed();
        if (this.conversationTagService == null) {
            this.conversationTagService = new ConversationTagServiceImpl(
                KoinJavaComponent.get(ConversationTagFactory.class),
                this.getTaskCreator(),
                this.getMultiDeviceManager()
            );
        }

        return this.conversationTagService;
    }

    @NonNull
    public ConversationService getConversationService() throws ThreemaException {
        ensureNotClosed();
        if (null == this.conversationService) {
            this.conversationService = new ConversationServiceImpl(
                this.cacheService,
                KoinJavaComponent.get(DatabaseService.class),
                KoinJavaComponent.get(DatabaseProvider.class),
                this.getContactService(),
                this.getGroupService(),
                this.getDistributionListService(),
                this.getMessageService(),
                this.getConversationCategoryService(),
                this.getBlockedIdentitiesService(),
                this.getConversationTagService(),
                this.getPreferenceService()
            );
        }

        return this.conversationService;
    }

    @NonNull
    public OnPremConfigFetcherProvider getOnPremConfigFetcherProvider() {
        ensureNotClosed();
        return KoinJavaComponent.get(OnPremConfigFetcherProvider.class);
    }

    @NonNull
    public ServerAddressProviderService getServerAddressProviderService() {
        ensureNotClosed();
        if (serverAddressProviderService == null) {
            this.serverAddressProviderService = () -> {
                if (ConfigUtils.isOnPremBuild()) {
                    return new OnPremServerAddressProvider(getOnPremConfigFetcherProvider()::getOnPremConfigFetcher);
                } else {
                    return new DefaultServerAddressProvider();
                }
            };
        }

        return this.serverAddressProviderService;
    }

    @NonNull
    public NotificationService getNotificationService() {
        ensureNotClosed();
        if (this.notificationService == null) {
            this.notificationService = new NotificationServiceImpl(
                appContext,
                this.getLockAppService(),
                this.getConversationCategoryService(),
                this.getNotificationPreferenceService(),
                this.getRingtoneService(),
                this.getPreferenceService(),
                KoinJavaComponent.get(IdentityProvider.class)
            );
        }
        return this.notificationService;
    }

    @NonNull
    public SynchronizeContactsService getSynchronizeContactsService() throws MasterKeyLockedException {
        ensureNotClosed();
        if (this.synchronizeContactsService == null) {
            this.synchronizeContactsService = new SynchronizeContactsServiceImpl(
                appContext,
                this.getAPIConnector(),
                this.getContactService(),
                this.getModelRepositories().getContacts(),
                this.getUserService(),
                this.getLocaleService(),
                this.getExcludedSyncIdentitiesService(),
                this.getPreferenceService(),
                this.getSynchronizedSettingsService(),
                this.getDeviceService(),
                this.getIdentityStore(),
                this.getBlockedIdentitiesService(),
                KoinJavaComponent.get(AppTaskExecutor.class),
                KoinJavaComponent.get(UpdateContactNameUseCase.class)
            );
        }

        return this.synchronizeContactsService;
    }

    @NonNull
    public BlockedIdentitiesService getBlockedIdentitiesService() {
        ensureNotClosed();
        if (this.blockedIdentitiesService == null) {
            this.blockedIdentitiesService = new BlockedIdentitiesServiceImpl(
                getPreferenceService(),
                getMultiDeviceManager(),
                getTaskCreator()
            );
        }
        return this.blockedIdentitiesService;
    }

    @NonNull
    public IdentityBlockedSteps getIdentityBlockedSteps() {
        return KoinJavaComponent.get(IdentityBlockedSteps.class);
    }

    @NonNull
    public ConversationCategoryService getConversationCategoryService() {
        ensureNotClosed();
        if (this.conversationCategoryService == null) {
            this.conversationCategoryService = new ConversationCategoryServiceImpl(
                this.getPreferenceService(),
                this.getPreferenceStore(),
                this.getMultiDeviceManager(),
                this.getTaskCreator()
            );
        }
        return this.conversationCategoryService;
    }

    @NonNull
    public ExcludedSyncIdentitiesService getExcludedSyncIdentitiesService() {
        ensureNotClosed();
        if (this.excludedSyncIdentitiesService == null) {
            this.excludedSyncIdentitiesService = new ExcludedSyncIdentitiesServiceImpl(
                getPreferenceService(),
                getMultiDeviceManager(),
                getTaskCreator()
            );
        }
        return this.excludedSyncIdentitiesService;
    }

    @NonNull
    public MessagePlayerService getMessagePlayerService() throws ThreemaException {
        ensureNotClosed();
        if (this.messagePlayerService == null) {
            this.messagePlayerService = new MessagePlayerServiceImpl(
                appContext,
                this.getMessageService(),
                this.getFileService(),
                this.getPreferenceService(),
                this.getNotificationPreferenceService(),
                this.getConversationCategoryService()
            );
        }
        return this.messagePlayerService;
    }

    @NonNull
    public DownloadService getDownloadService() {
        ensureNotClosed();
        if (this.downloadService == null) {
            this.downloadService = new DownloadServiceImpl(
                appContext,
                this.getApiService()
            );
        }
        return this.downloadService;
    }

    @NonNull
    public BallotService getBallotService() throws NoIdentityException, MasterKeyLockedException {
        ensureNotClosed();
        if (this.ballotService == null) {
            this.ballotService = new BallotServiceImpl(
                this.cacheService.getBallotModelCache(),
                this.cacheService.getLinkBallotModelCache(),
                KoinJavaComponent.get(DatabaseService.class),
                this.getUserService(),
                this.getGroupService(),
                this.getContactService(),
                this);
        }
        return this.ballotService;
    }

    @NonNull
    public WallpaperService getWallpaperService() {
        ensureNotClosed();
        if (this.wallpaperService == null) {
            this.wallpaperService = new WallpaperServiceImpl(
                appContext,
                KoinJavaComponent.get(WallpaperFileHandleProvider.class),
                getPreferenceService()
            );
        }

        return this.wallpaperService;
    }

    public @NonNull ThreemaSafeService getThreemaSafeService() throws MasterKeyLockedException, NoIdentityException {
        ensureNotClosed();
        if (this.threemaSafeService == null) {
            this.threemaSafeService = new ThreemaSafeServiceImpl(
                appContext,
                this.getPreferenceService(),
                this.getSynchronizedSettingsService(),
                this.getUserService(),
                this.getContactService(),
                this.getGroupService(),
                this.getDistributionListService(),
                this.getLocaleService(),
                this.getFileService(),
                this.getBlockedIdentitiesService(),
                this.getExcludedSyncIdentitiesService(),
                this.getProfilePicRecipientsService(),
                KoinJavaComponent.get(DatabaseService.class),
                this.getIdentityStore(),
                this.getApiService(),
                this.getAPIConnector(),
                this.getConversationCategoryService(),
                this.getServerAddressProviderService().getServerAddressProvider(),
                this.getEncryptedPreferenceStore(),
                this.getModelRepositories().getContacts(),
                this.getOkHttpClient(),
                KoinJavaComponent.get(AppRestrictions.class)
            );
        }
        return this.threemaSafeService;
    }

    @NonNull
    public IdentityStore getIdentityStore() {
        ensureNotClosed();
        return this.coreServiceManager.getIdentityStore();
    }

    @NonNull
    public RingtoneService getRingtoneService() {
        ensureNotClosed();
        return KoinJavaComponent.get(RingtoneService.class);
    }

    @NonNull
    public BackupChatService getBackupChatService() throws ThreemaException {
        ensureNotClosed();
        if (this.backupChatService == null) {
            this.backupChatService = new BackupChatServiceImpl(
                appContext,
                this.getFileService(),
                this.getMessageService(),
                this.getContactService(),
                this.getPreferenceService()
            );
        }

        return this.backupChatService;
    }

    @NonNull
    public SensorService getSensorService() {
        ensureNotClosed();
        if (this.sensorService == null) {
            this.sensorService = new SensorServiceImpl(appContext);
        }
        return this.sensorService;
    }

    @NonNull
    public WebClientServiceManager getWebClientServiceManager() throws ThreemaException {
        ensureNotClosed();
        if (this.webClientServiceManager == null) {
            this.webClientServiceManager = new WebClientServiceManager(new ServicesContainer(
                appContext,
                this.getLifetimeService(),
                this.getContactService(),
                this.getGroupService(),
                this.getDistributionListService(),
                this.getConversationService(),
                this.getConversationTagService(),
                this.getMessageService(),
                this.getNotificationService(),
                KoinJavaComponent.get(ContactModelFactory.class),
                KoinJavaComponent.get(WebClientSessionModelFactory.class),
                this.getBlockedIdentitiesService(),
                this.getPreferenceService(),
                this.getUserService(),
                this.getConversationCategoryService(),
                this.getFileService(),
                this.getSynchronizeContactsService(),
                this.getLicenseService(),
                this.getAPIConnector(),
                this.getModelRepositories().getContacts(),
                this.getModelRepositories().getGroups(),
                this.getGroupFlowDispatcher(),
                KoinJavaComponent.get(AppRestrictions.class)
            ));
        }
        return this.webClientServiceManager;
    }

    @NonNull
    public ProfilePictureRecipientsService getProfilePicRecipientsService() {
        ensureNotClosed();
        if (this.profilePictureRecipientsService == null) {
            this.profilePictureRecipientsService = new ProfilePictureRecipientsServiceImpl(getPreferenceService());
        }
        return this.profilePictureRecipientsService;
    }

    @NonNull
    public VoipStateService getVoipStateService() throws ThreemaException {
        ensureNotClosed();
        if (this.voipStateService == null) {
            this.voipStateService = new VoipStateService(
                getContactService(),
                getModelRepositories().getContacts(),
                getCallNotificationManager(),
                getLifetimeService(),
                appContext
            );
        }
        return this.voipStateService;
    }

    @NonNull
    public CallNotificationManager getCallNotificationManager() {
        ensureNotClosed();
        return KoinJavaComponent.get(CallNotificationManager.class);
    }

    @NonNull
    public DatabaseService getDatabaseService() {
        return KoinJavaComponent.get(DatabaseService.class);
    }

    @NonNull
    public ModelRepositories getModelRepositories() {
        ensureNotClosed();
        return this.modelRepositories;
    }

    @NonNull
    public DHSessionStoreInterface getDHSessionStore() {
        ensureNotClosed();
        return this.dhSessionStore;
    }

    @NonNull
    public ForwardSecurityMessageProcessor getForwardSecurityMessageProcessor() throws ThreemaException {
        ensureNotClosed();
        if (this.forwardSecurityMessageProcessor == null) {
            this.forwardSecurityMessageProcessor = new ForwardSecurityMessageProcessor(
                this.getDHSessionStore(),
                this.getContactStore(),
                this.getIdentityStore(),
                this.getNonceFactory(),
                new ForwardSecurityStatusSender(
                    this.getContactService(),
                    this.getMessageService(),
                    this.getAPIConnector(),
                    this.getUserService(),
                    this.getModelRepositories().getContacts()
                )
            );
            // TODO(ANDR-2519): Remove when md allows fs
            forwardSecurityMessageProcessor.setForwardSecurityEnabled(getMultiDeviceManager().isMdDisabledOrSupportsFs());
        }
        return this.forwardSecurityMessageProcessor;
    }

    @NonNull
    public SymmetricEncryptionService getSymmetricEncryptionService() {
        ensureNotClosed();
        if (symmetricEncryptionService == null) {
            symmetricEncryptionService = new SymmetricEncryptionService();
        }
        return symmetricEncryptionService;
    }

    @NonNull
    public EmojiService getEmojiService() {
        ensureNotClosed();
        if (emojiService == null) {
            EmojiSearchIndex searchIndex = new EmojiSearchIndex(
                appContext,
                getPreferenceService()
            );
            emojiService = new EmojiService(
                getPreferenceService(),
                searchIndex,
                new EmojiRecent(getPreferenceService())
            );
        }
        return emojiService;
    }

    @NonNull
    public GroupCallManager getGroupCallManager() throws ThreemaException {
        ensureNotClosed();
        if (groupCallManager == null) {
            groupCallManager = new GroupCallManagerImpl(
                appContext,
                this,
                getDatabaseService(),
                getGroupService(),
                getContactService(),
                getModelRepositories().getContacts(),
                KoinJavaComponent.get(IdentityProvider.class),
                getPreferenceService(),
                getMessageService(),
                getNotificationService(),
                getSfuConnection()
            );
        }
        return groupCallManager;
    }

    @NonNull
    public SfuConnection getSfuConnection() {
        ensureNotClosed();
        if (sfuConnection == null) {
            sfuConnection = new SfuConnectionImpl(
                getAPIConnector(),
                getIdentityStore(),
                getOkHttpClient(),
                AppVersionProvider.getAppVersion()
            );
        }
        return sfuConnection;
    }

    public @NonNull NonceFactory getNonceFactory() {
        ensureNotClosed();
        return coreServiceManager.getNonceFactory();
    }

    private @NonNull IncomingMessageProcessor getIncomingMessageProcessor() {
        if (this.incomingMessageProcessor == null) {
            this.incomingMessageProcessor = new IncomingMessageProcessorImpl(this);
        }
        return this.incomingMessageProcessor;
    }

    public @NonNull TaskManager getTaskManager() {
        ensureNotClosed();
        return this.coreServiceManager.getTaskManager();
    }

    public @NonNull TaskCreator getTaskCreator() {
        ensureNotClosed();
        if (this.taskCreator == null) {
            this.taskCreator = new TaskCreator(this);
        }
        return this.taskCreator;
    }

    @NonNull
    public MultiDeviceManager getMultiDeviceManager() {
        ensureNotClosed();
        return this.coreServiceManager.getMultiDeviceManager();
    }

    @NonNull
    public GroupFlowDispatcher getGroupFlowDispatcher() throws ThreemaException {
        ensureNotClosed();
        if (this.groupFlowDispatcher == null) {
            this.groupFlowDispatcher = new GroupFlowDispatcher(
                getModelRepositories().getContacts(),
                getModelRepositories().getGroups(),
                getContactService(),
                getGroupService(),
                getGroupCallManager(),
                getUserService(),
                getContactStore(),
                getIdentityStore(),
                getForwardSecurityMessageProcessor(),
                getNonceFactory(),
                getPreferenceService(),
                getSynchronizedSettingsService(),
                getMultiDeviceManager(),
                getGroupProfilePictureUploader(),
                getAPIConnector(),
                getFileService(),
                getDatabaseService(),
                getTaskManager(),
                getConnection(),
                getIdentityBlockedSteps()
            );
        }
        return this.groupFlowDispatcher;
    }

    @NonNull
    public OkHttpClient getOkHttpClient() {
        ensureNotClosed();
        return KoinJavaComponent.get(OkHttpClient.class);
    }

    @NonNull
    private ConvertibleServerConnection createServerConnection() {
        Supplier<ServerConnection> connectionSupplier = new CspD2mDualConnectionSupplier(
            (PowerManager) appContext.getSystemService(Context.POWER_SERVICE),
            getMultiDeviceManager(),
            getIncomingMessageProcessor(),
            getTaskManager(),
            getDeviceCookieManager(),
            getServerAddressProviderService(),
            getIdentityStore(),
            coreServiceManager.getVersion(),
            isIpv6Preferred.getValue(),
            getOkHttpClient(),
            KoinJavaComponent.get(AppStartupMonitor.class),
            hasDevFeatures()
        );
        return new ConvertibleServerConnection(connectionSupplier);
    }

    @NonNull
    public DeviceCookieManager getDeviceCookieManager() {
        ensureNotClosed();
        return coreServiceManager.getDeviceCookieManager();
    }

    @NonNull
    private SynchronizedSettingsService createSynchronizedSettingsService() {
        return new SynchronizedSettingsServiceImpl(
            appContext,
            coreServiceManager.getPreferenceStore(),
            getTaskManager(),
            getMultiDeviceManager()
        );
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("ServiceManager is closed");
        }
    }

    public void close() {
        closed = true;
    }
}

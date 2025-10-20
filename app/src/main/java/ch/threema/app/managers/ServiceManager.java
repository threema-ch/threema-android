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

package ch.threema.app.managers;

import android.content.Context;
import android.os.PowerManager;

import org.slf4j.Logger;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.backuprestore.BackupChatService;
import ch.threema.app.backuprestore.BackupChatServiceImpl;
import ch.threema.app.connection.CspD2mDualConnectionSupplier;
import ch.threema.app.emojis.EmojiRecent;
import ch.threema.app.emojis.EmojiService;
import ch.threema.app.emojis.search.EmojiSearchIndex;
import ch.threema.app.exceptions.NoIdentityException;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.onprem.OnPremCertPinning;
import ch.threema.app.onprem.OnPremServerAddressProvider;
import ch.threema.app.processors.IncomingMessageProcessorImpl;
import ch.threema.app.services.ActivityService;
import ch.threema.app.services.ApiService;
import ch.threema.app.services.ApiServiceImpl;
import ch.threema.app.services.AppDirectoryProvider;
import ch.threema.app.services.AvatarCacheService;
import ch.threema.app.services.AvatarCacheServiceImpl;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.BlockedIdentitiesServiceImpl;
import ch.threema.app.services.BrowserDetectionService;
import ch.threema.app.services.BrowserDetectionServiceImpl;
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
import ch.threema.app.services.FileServiceImpl;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.GroupServiceImpl;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.IdListServiceImpl;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.LifetimeServiceImpl;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.LocaleServiceImpl;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.services.NotificationPreferenceServiceImpl;
import ch.threema.app.services.OnPremConfigFetcherProvider;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.services.notification.NotificationServiceImpl;
import ch.threema.app.services.PinLockService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.preference.service.PreferenceServiceImpl;
import ch.threema.app.services.QRCodeService;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.services.RingtoneServiceImpl;
import ch.threema.app.services.SensorService;
import ch.threema.app.services.SensorServiceImpl;
import ch.threema.app.services.ServerAddressProviderService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.SynchronizeContactsServiceImpl;
import ch.threema.app.services.SystemScreenLockService;
import ch.threema.app.services.SystemScreenLockServiceImpl;
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
import ch.threema.app.utils.DeviceIdUtil;
import ch.threema.app.utils.ForwardSecurityStatusSender;
import ch.threema.app.utils.LazyProperty;
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
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.repositories.ModelRepositories;
import ch.threema.domain.models.LicenseCredentials;
import ch.threema.domain.models.UserCredentials;
import ch.threema.domain.onprem.OnPremConfigStore;
import ch.threema.domain.onprem.OnPremConfigParser;
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
import ch.threema.storage.DatabaseService;
import java8.util.function.Supplier;
import okhttp3.OkHttpClient;

public class ServiceManager {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ServiceManager");

    @NonNull
    private final CoreServiceManager coreServiceManager;
    @NonNull
    private final Supplier<Boolean> isIpv6Preferred;
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
    @Nullable
    private QRCodeService qrCodeService;
    @Nullable
    private FileService fileService;
    @Nullable
    private PreferenceService preferencesService;
    @Nullable
    private NotificationPreferenceService notificationPreferenceService;
    @Nullable
    private LocaleService localeService;
    @Nullable
    private DeviceService deviceService;
    @Nullable
    private LifetimeService lifetimeService;
    @Nullable
    private AvatarCacheService avatarCacheService;
    @Nullable
    private LicenseService licenseService;
    @Nullable
    private GroupService groupService;
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
    private SystemScreenLockService systemScreenLockService;

    @Nullable
    private BlockedIdentitiesService blockedIdentitiesService;
    @Nullable
    private ExcludedSyncIdentitiesService excludedSyncIdentitiesService;
    @Nullable
    private IdListService profilePicRecipientsService;
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
    private RingtoneService ringtoneService;
    @Nullable
    private BackupChatService backupChatService;
    @NonNull
    private final DatabaseService databaseService;
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
    private BrowserDetectionService browserDetectionService;
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
    @Nullable
    private OnPremConfigFetcherProvider onPremConfigFetcherProvider = null;

    @NonNull
    private final OkHttpClient baseOkHttpClient;

    @Nullable
    private final OnPremConfigStore onPremConfigStore;

    @NonNull
    private final LazyProperty<OkHttpClient> okHttpClient = new LazyProperty<>(this::createOkHttpClient);

    public ServiceManager(
        @NonNull ModelRepositories modelRepositories,
        @NonNull DHSessionStoreInterface dhSessionStore,
        @NonNull MasterKeyProvider masterKeyProvider,
        @NonNull CoreServiceManagerImpl coreServiceManager,
        @NonNull OkHttpClient baseOkHttpClient,
        @Nullable OnPremConfigStore onPremConfigStore
    ) throws ThreemaException {
        this.cacheService = new CacheService();
        this.coreServiceManager = coreServiceManager;
        this.isIpv6Preferred = new LazyProperty<>(() -> getPreferenceService().isIpv6Preferred());
        this.masterKeyProvider = masterKeyProvider;
        this.databaseService = coreServiceManager.getDatabaseService();
        this.modelRepositories = modelRepositories;
        this.dhSessionStore = dhSessionStore;
        this.baseOkHttpClient = baseOkHttpClient;
        this.onPremConfigStore = onPremConfigStore;
        // Finalize initialization of task archiver and device cookie manager before the connection
        // is created.
        coreServiceManager.getTaskArchiver().setServiceManager(this);
        coreServiceManager.getDeviceCookieManager().setNotificationService(getNotificationService());
        this.connection = createServerConnection();
        coreServiceManager.getMultiDeviceManager().setReconnectHandle(connection);
    }

    @NonNull
    public DatabaseContactStore getContactStore() {
        if (this.contactStore == null) {
            this.contactStore = new DatabaseContactStore(
                this.databaseService,
                this.getServerAddressProviderService().getServerAddressProvider()
            );
        }

        return this.contactStore;
    }

    @NonNull
    public APIConnector getAPIConnector() {
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
                    isIpv6Preferred.get(),
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
        logger.trace("startConnection");

        String currentIdentity = this.coreServiceManager.getIdentityStore().getIdentity();
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
        return coreServiceManager.getPreferenceStore();
    }

    @NonNull
    public EncryptedPreferenceStore getEncryptedPreferenceStore() {
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

    @WorkerThread
    private void reconnectConnection() throws InterruptedException {
        connection.reconnect();
    }

    @NonNull
    public UserService getUserService() {
        if (this.userService == null) {
            try {
                this.userService = new UserServiceImpl(
                    this.getContext(),
                    this.coreServiceManager.getPreferenceStore(),
                    this.getLocaleService(),
                    this.getAPIConnector(),
                    this.getApiService(),
                    this.getFileService(),
                    this.getIdentityStore(),
                    this.getPreferenceService(),
                    this.getTaskManager(),
                    this.getTaskCreator(),
                    this.getMultiDeviceManager()
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
        if (this.contactService == null) {
            if (masterKeyProvider.isLocked()) {
                throw new MasterKeyLockedException();
            }
            this.contactService = new ContactServiceImpl(
                this.getContext(),
                this.getContactStore(),
                this.getAvatarCacheService(),
                this.databaseService,
                this.getUserService(),
                this.getIdentityStore(),
                this.getPreferenceService(),
                this.getBlockedIdentitiesService(),
                this.getProfilePicRecipientsService(),
                this.getFileService(),
                this.cacheService,
                this.getApiService(),
                this.getLicenseService(),
                this.getAPIConnector(),
                this.getOkHttpClient(),
                this.getModelRepositories().getContacts(),
                this.getTaskCreator(),
                this.getMultiDeviceManager()
            );
        }

        return this.contactService;
    }

    @NonNull
    public MessageService getMessageService() throws ThreemaException {
        if (this.messageService == null) {
            this.messageService = new MessageServiceImpl(
                this.getContext(),
                this.cacheService,
                this.databaseService,
                this.getContactService(),
                this.getFileService(),
                this.getIdentityStore(),
                this.getSymmetricEncryptionService(),
                this.getPreferenceService(),
                this.getLockAppService(),
                this.getBallotService(),
                this.getGroupService(),
                this.getApiService(),
                this.getDownloadService(),
                this.getConversationCategoryService(),
                this.getBlockedIdentitiesService(),
                this.getMultiDeviceManager(),
                this.getModelRepositories().getEditHistory(),
                this.getModelRepositories().getEmojiReaction()
            );
        }

        return this.messageService;
    }

    @NonNull
    public PreferenceService getPreferenceService() {
        if (this.preferencesService == null) {
            this.preferencesService = new PreferenceServiceImpl(
                getContext(),
                coreServiceManager.getPreferenceStore(),
                coreServiceManager.getEncryptedPreferenceStore(),
                getTaskManager(),
                getMultiDeviceManager(),
                getNonceFactory()
            );
        }
        return this.preferencesService;
    }

    @NonNull
    public NotificationPreferenceService getNotificationPreferenceService() {
        if (notificationPreferenceService == null) {
            notificationPreferenceService = new NotificationPreferenceServiceImpl(
                getContext(), getPreferenceStore()
            );
        }
        return notificationPreferenceService;
    }

    @NonNull
    public QRCodeService getQRCodeService() {
        if (this.qrCodeService == null) {
            this.qrCodeService = new QRCodeServiceImpl(this.getUserService());
        }

        return this.qrCodeService;
    }

    @NonNull
    public FileService getFileService() {
        if (this.fileService == null) {
            this.fileService = new FileServiceImpl(
                this.getContext(),
                new AppDirectoryProvider((getContext())),
                masterKeyProvider,
                this.getPreferenceService(),
                this.getNotificationPreferenceService(),
                this.getAvatarCacheService()
            );
        }

        return this.fileService;
    }

    @NonNull
    public LocaleService getLocaleService() {
        if (this.localeService == null) {
            this.localeService = new LocaleServiceImpl(this.getContext());
        }

        return this.localeService;
    }

    @NonNull
    public ServerConnection getConnection() {
        return this.connection;
    }

    @NonNull
    public DeviceService getDeviceService() {
        if (this.deviceService == null) {
            this.deviceService = new DeviceServiceImpl(this.getContext());
        }

        return this.deviceService;
    }

    @NonNull
    public LifetimeService getLifetimeService() {
        if (this.lifetimeService == null) {
            this.lifetimeService = new LifetimeServiceImpl(this.getContext());
        }

        return this.lifetimeService;
    }

    @NonNull
    public AvatarCacheService getAvatarCacheService() {
        if (this.avatarCacheService == null) {
            this.avatarCacheService = new AvatarCacheServiceImpl(this.getContext());
        }

        return this.avatarCacheService;
    }

    @NonNull
    public LicenseService getLicenseService() {
        if (this.licenseService == null) {
            switch (BuildFlavor.getCurrent().getLicenseType()) {
                case SERIAL:
                    this.licenseService = new LicenseServiceSerial(
                        this.getAPIConnector(),
                        this.getPreferenceService(),
                        DeviceIdUtil.getDeviceId(getContext()));
                    break;
                case GOOGLE_WORK:
                case HMS_WORK:
                case ONPREM:
                    this.licenseService = new LicenseServiceUser(
                        this.getAPIConnector(),
                        this.getPreferenceService(),
                        DeviceIdUtil.getDeviceId(getContext()));
                    break;
                default:
                    this.licenseService = new LicenseService() {
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
    public LockAppService getLockAppService() {
        if (null == this.lockAppService) {
            this.lockAppService = new PinLockService(
                this.getContext(),
                this.getPreferenceService(),
                this.getUserService()
            );
        }

        return this.lockAppService;
    }

    @NonNull
    public ActivityService getActivityService() {
        if (null == this.activityService) {
            this.activityService = new ActivityService(
                this.getContext(),
                this.getLockAppService(),
                this.getPreferenceService(),
                masterKeyProvider
            );
        }
        return this.activityService;
    }

    @NonNull
    public GroupService getGroupService() throws MasterKeyLockedException {
        if (null == this.groupService) {
            this.groupService = new GroupServiceImpl(
                this.getContext(),
                this.cacheService,
                this.getUserService(),
                this.getContactService(),
                this.databaseService,
                this.getAvatarCacheService(),
                this.getFileService(),
                this.getWallpaperService(),
                this.getConversationCategoryService(),
                this.getRingtoneService(),
                this.getConversationTagService(),
                this.getModelRepositories().getContacts(),
                this.getModelRepositories().getGroups(),
                this
            );
        }
        return this.groupService;
    }

    @NonNull
    public ApiService getApiService() {
        if (null == this.apiService) {
            this.apiService = new ApiServiceImpl(
                AppVersionProvider.getAppVersion(),
                isIpv6Preferred.get(),
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
        if (null == this.distributionListService) {
            this.distributionListService = new DistributionListServiceImpl(
                this.getContext(),
                this.getAvatarCacheService(),
                this.databaseService,
                this.getContactService(),
                this.getConversationTagService()
            );
        }

        return this.distributionListService;
    }

    @NonNull
    public ConversationTagService getConversationTagService() {
        if (this.conversationTagService == null) {
            this.conversationTagService = new ConversationTagServiceImpl(
                this.databaseService,
                this.getTaskCreator(),
                this.getMultiDeviceManager()
            );
        }

        return this.conversationTagService;
    }

    @NonNull
    public ConversationService getConversationService() throws ThreemaException {
        if (null == this.conversationService) {
            this.conversationService = new ConversationServiceImpl(
                this.getContext(),
                this.cacheService,
                this.databaseService,
                this.getContactService(),
                this.getGroupService(),
                this.getDistributionListService(),
                this.getMessageService(),
                this.getConversationCategoryService(),
                this.getBlockedIdentitiesService(),
                this.getConversationTagService()
            );
        }

        return this.conversationService;
    }

    @NonNull
    public OnPremConfigFetcherProvider getOnPremConfigFetcherProvider() {
        if (!ConfigUtils.isOnPremBuild()) {
            throw new IllegalStateException("Cannot create OnPremConfigFetcherProvider outside of an OnPrem build");
        }
        if (onPremConfigFetcherProvider == null) {
            onPremConfigFetcherProvider = new OnPremConfigFetcherProvider(
                getPreferenceService(),
                new OnPremConfigParser(),
                onPremConfigStore,
                baseOkHttpClient,
                BuildConfig.ONPREM_CONFIG_TRUSTED_PUBLIC_KEYS
            );
        }
        return onPremConfigFetcherProvider;
    }

    @NonNull
    public ServerAddressProviderService getServerAddressProviderService() {
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
        if (this.notificationService == null) {
            this.notificationService = new NotificationServiceImpl(
                this.getContext(),
                this.getLockAppService(),
                this.getConversationCategoryService(),
                this.getNotificationPreferenceService(),
                this.getRingtoneService()
            );
        }
        return this.notificationService;
    }

    @NonNull
    public SynchronizeContactsService getSynchronizeContactsService() throws MasterKeyLockedException {
        if (this.synchronizeContactsService == null) {
            this.synchronizeContactsService = new SynchronizeContactsServiceImpl(
                this.getContext(),
                this.getAPIConnector(),
                this.getContactService(),
                this.getModelRepositories().getContacts(),
                this.getUserService(),
                this.getLocaleService(),
                this.getExcludedSyncIdentitiesService(),
                this.getPreferenceService(),
                this.getDeviceService(),
                this.getFileService(),
                this.getIdentityStore(),
                this.getBlockedIdentitiesService(),
                this.getApiService(),
                this.getOkHttpClient()
            );
        }

        return this.synchronizeContactsService;
    }

    @NonNull
    public BlockedIdentitiesService getBlockedIdentitiesService() {
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
    public ConversationCategoryService getConversationCategoryService() {
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
        if (this.messagePlayerService == null) {
            this.messagePlayerService = new MessagePlayerServiceImpl(
                getContext(),
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
        if (this.downloadService == null) {
            this.downloadService = new DownloadServiceImpl(
                this.getContext(),
                this.getFileService(),
                this.getApiService()
            );
        }
        return this.downloadService;
    }

    @NonNull
    public BallotService getBallotService() throws NoIdentityException, MasterKeyLockedException {
        if (this.ballotService == null) {
            this.ballotService = new BallotServiceImpl(
                this.cacheService.getBallotModelCache(),
                this.cacheService.getLinkBallotModelCache(),
                this.databaseService,
                this.getUserService(),
                this.getGroupService(),
                this.getContactService(),
                this);
        }
        return this.ballotService;
    }

    @NonNull
    public WallpaperService getWallpaperService() {
        if (this.wallpaperService == null) {
            this.wallpaperService = new WallpaperServiceImpl(
                getContext(),
                getFileService(),
                getPreferenceService(),
                masterKeyProvider
            );
        }

        return this.wallpaperService;
    }

    public @NonNull ThreemaSafeService getThreemaSafeService() throws MasterKeyLockedException, NoIdentityException {
        if (this.threemaSafeService == null) {
            this.threemaSafeService = new ThreemaSafeServiceImpl(
                this.getContext(),
                this.getPreferenceService(),
                this.getUserService(),
                this.getContactService(),
                this.getGroupService(),
                this.getDistributionListService(),
                this.getLocaleService(),
                this.getFileService(),
                this.getBlockedIdentitiesService(),
                this.getExcludedSyncIdentitiesService(),
                this.getProfilePicRecipientsService(),
                this.getDatabaseService(),
                this.getIdentityStore(),
                this.getApiService(),
                this.getAPIConnector(),
                this.getConversationCategoryService(),
                this.getServerAddressProviderService().getServerAddressProvider(),
                this.getEncryptedPreferenceStore(),
                this.getModelRepositories().getContacts(),
                this.getOkHttpClient()
            );
        }
        return this.threemaSafeService;
    }

    @NonNull
    public Context getContext() {
        return ThreemaApplication.getAppContext();
    }

    @NonNull
    public IdentityStore getIdentityStore() {
        return this.coreServiceManager.getIdentityStore();
    }

    @NonNull
    public RingtoneService getRingtoneService() {
        if (this.ringtoneService == null) {
            this.ringtoneService = new RingtoneServiceImpl(
                this.getNotificationPreferenceService()
            );
        }

        return this.ringtoneService;
    }

    @NonNull
    public BackupChatService getBackupChatService() throws ThreemaException {
        if (this.backupChatService == null) {
            this.backupChatService = new BackupChatServiceImpl(
                this.getContext(),
                this.getFileService(),
                this.getMessageService(),
                this.getContactService()
            );
        }

        return this.backupChatService;
    }

    @NonNull
    public SystemScreenLockService getScreenLockService() {
        if (this.systemScreenLockService == null) {
            this.systemScreenLockService = new SystemScreenLockServiceImpl(
                this.getContext(),
                this.getLockAppService(),
                this.getPreferenceService()
            );
        }
        return this.systemScreenLockService;
    }

    @NonNull
    public SensorService getSensorService() {
        if (this.sensorService == null) {
            this.sensorService = new SensorServiceImpl(this.getContext());
        }
        return this.sensorService;
    }

    @NonNull
    public WebClientServiceManager getWebClientServiceManager() throws ThreemaException {
        if (this.webClientServiceManager == null) {
            this.webClientServiceManager = new WebClientServiceManager(new ServicesContainer(
                this.getContext().getApplicationContext(),
                this.getLifetimeService(),
                this.getContactService(),
                this.getGroupService(),
                this.getDistributionListService(),
                this.getConversationService(),
                this.getConversationTagService(),
                this.getMessageService(),
                this.getNotificationService(),
                this.databaseService,
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
                this.getGroupFlowDispatcher()
            ));
        }
        return this.webClientServiceManager;
    }

    @NonNull
    public BrowserDetectionService getBrowserDetectionService() {
        if (this.browserDetectionService == null) {
            this.browserDetectionService = new BrowserDetectionServiceImpl();
        }
        return this.browserDetectionService;
    }

    @NonNull
    public IdListService getProfilePicRecipientsService() {
        if (this.profilePicRecipientsService == null) {
            this.profilePicRecipientsService = new IdListServiceImpl("identity_list_profilepics", this.getPreferenceService());
        }
        return this.profilePicRecipientsService;
    }

    @NonNull
    public VoipStateService getVoipStateService() throws ThreemaException {
        if (this.voipStateService == null) {
            this.voipStateService = new VoipStateService(
                getContactService(),
                getNotificationPreferenceService(),
                getLifetimeService(),
                getContext()
            );
        }
        return this.voipStateService;
    }

    @NonNull
    public DatabaseService getDatabaseService() {
        return this.databaseService;
    }

    @NonNull
    public ModelRepositories getModelRepositories() {
        return this.modelRepositories;
    }

    @NonNull
    public DHSessionStoreInterface getDHSessionStore() {
        return this.dhSessionStore;
    }

    @NonNull
    public ForwardSecurityMessageProcessor getForwardSecurityMessageProcessor() throws ThreemaException {
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
        if (symmetricEncryptionService == null) {
            symmetricEncryptionService = new SymmetricEncryptionService();
        }
        return symmetricEncryptionService;
    }

    @NonNull
    public EmojiService getEmojiService() {
        if (emojiService == null) {
            EmojiSearchIndex searchIndex = new EmojiSearchIndex(
                getContext().getApplicationContext(),
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
        if (groupCallManager == null) {
            groupCallManager = new GroupCallManagerImpl(
                getContext().getApplicationContext(),
                this,
                getDatabaseService(),
                getGroupService(),
                getContactService(),
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
        return coreServiceManager.getNonceFactory();
    }

    private @NonNull IncomingMessageProcessor getIncomingMessageProcessor() {
        if (this.incomingMessageProcessor == null) {
            this.incomingMessageProcessor = new IncomingMessageProcessorImpl(this);
        }
        return this.incomingMessageProcessor;
    }

    public @NonNull TaskManager getTaskManager() {
        return this.coreServiceManager.getTaskManager();
    }

    public @NonNull TaskCreator getTaskCreator() {
        if (this.taskCreator == null) {
            this.taskCreator = new TaskCreator(this);
        }
        return this.taskCreator;
    }

    @NonNull
    public MultiDeviceManager getMultiDeviceManager() {
        return this.coreServiceManager.getMultiDeviceManager();
    }

    @NonNull
    public GroupFlowDispatcher getGroupFlowDispatcher() throws ThreemaException {
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
                getBlockedIdentitiesService(),
                getPreferenceService(),
                getMultiDeviceManager(),
                getApiService(),
                getAPIConnector(),
                getFileService(),
                getDatabaseService(),
                getTaskManager(),
                getConnection()
            );
        }
        return this.groupFlowDispatcher;
    }

    @NonNull
    public OkHttpClient getOkHttpClient() {
        return okHttpClient.get();
    }

    @NonNull
    private ConvertibleServerConnection createServerConnection() throws ThreemaException {
        Supplier<ServerConnection> connectionSupplier = new CspD2mDualConnectionSupplier(
            (PowerManager) getContext().getSystemService(Context.POWER_SERVICE),
            getMultiDeviceManager(),
            getIncomingMessageProcessor(),
            getTaskManager(),
            getDeviceCookieManager(),
            getServerAddressProviderService(),
            getIdentityStore(),
            coreServiceManager.getVersion(),
            isIpv6Preferred.get(),
            okHttpClient,
            ConfigUtils.isDevBuild()
        );
        return new ConvertibleServerConnection(connectionSupplier);
    }

    @NonNull
    public DeviceCookieManager getDeviceCookieManager() {
        return coreServiceManager.getDeviceCookieManager();
    }

    @NonNull
    private OkHttpClient createOkHttpClient() {
        if (ConfigUtils.isOnPremBuild()) {
            return OnPremCertPinning.INSTANCE.createClientWithCertPinning(
                baseOkHttpClient,
                getOnPremConfigFetcherProvider()
            );
        } else {
            return baseOkHttpClient;
        }
    }

    public boolean isIpv6Preferred() {
        return isIpv6Preferred.get();
    }
}

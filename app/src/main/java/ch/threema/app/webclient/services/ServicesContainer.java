package ch.threema.app.webclient.services;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import ch.threema.app.restrictions.AppRestrictions;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.factories.WebClientSessionModelFactory;

/**
 * Contains all necessary services used by the web client.
 */
@AnyThread
public class ServicesContainer {
    // App context
    @NonNull
    public final Context appContext;

    // Services
    @NonNull
    public final LifetimeService lifetime;
    @NonNull
    public final ContactService contact;
    @NonNull
    public final GroupService group;
    @NonNull
    public final DistributionListService distributionList;
    @NonNull
    public final ConversationService conversation;
    @NonNull
    public final ConversationTagService conversationTag;
    @NonNull
    public final MessageService message;
    @NonNull
    public final NotificationService notification;
    @NonNull
    public final ContactModelFactory contactModelFactory;
    @NonNull
    public final WebClientSessionModelFactory webClientSessionModelFactory;
    @NonNull
    public final BlockedIdentitiesService blockedIdentitiesService;
    @NonNull
    public final PreferenceService preference;
    @NonNull
    public final UserService user;
    @NonNull
    public final ConversationCategoryService conversationCategoryService;
    @NonNull
    public final FileService file;
    @NonNull
    public final SynchronizeContactsService synchronizeContacts;
    @NonNull
    public final LicenseService license;
    @NonNull
    public final SessionWakeUpService sessionWakeUp;
    @NonNull
    public final WakeLockService wakeLock;
    @NonNull
    public final BatteryStatusService batteryStatus;
    @NonNull
    public final APIConnector apiConnector;
    @NonNull
    public final ContactModelRepository contactModelRepository;
    @NonNull
    public final GroupModelRepository groupModelRepository;
    @NonNull
    public final GroupFlowDispatcher groupFlowDispatcher;
    @NonNull
    public final AppRestrictions appRestrictions;

    public ServicesContainer(
        @NonNull final Context appContext,
        @NonNull final LifetimeService lifetime,
        @NonNull final ContactService contact,
        @NonNull final GroupService group,
        @NonNull final DistributionListService distributionList,
        @NonNull final ConversationService conversation,
        @NonNull final ConversationTagService conversationTag,
        @NonNull final MessageService message,
        @NonNull final NotificationService notification,
        @NonNull final ContactModelFactory contactModelFactory,
        @NonNull final WebClientSessionModelFactory webClientSessionModelFactory,
        @NonNull final BlockedIdentitiesService blockedIdentitiesService,
        @NonNull final PreferenceService preference,
        @NonNull final UserService user,
        @NonNull final ConversationCategoryService conversationCategoryService,
        @NonNull final FileService file,
        @NonNull final SynchronizeContactsService synchronizeContacts,
        @NonNull final LicenseService license,
        @NonNull final APIConnector apiConnector,
        @NonNull final ContactModelRepository contactModelRepository,
        @NonNull final GroupModelRepository groupModelRepository,
        @NonNull final GroupFlowDispatcher groupFlowDispatcher,
        @NonNull final AppRestrictions appRestrictions
    ) {
        this.appContext = appContext;
        this.lifetime = lifetime;
        this.contact = contact;
        this.group = group;
        this.distributionList = distributionList;
        this.conversation = conversation;
        this.conversationTag = conversationTag;
        this.message = message;
        this.notification = notification;
        this.contactModelFactory = contactModelFactory;
        this.webClientSessionModelFactory = webClientSessionModelFactory;
        this.blockedIdentitiesService = blockedIdentitiesService;
        this.preference = preference;
        this.user = user;
        this.conversationCategoryService = conversationCategoryService;
        this.file = file;
        this.synchronizeContacts = synchronizeContacts;
        this.license = license;
        this.sessionWakeUp = SessionWakeUpServiceImpl.getInstance();
        this.apiConnector = apiConnector;
        this.contactModelRepository = contactModelRepository;
        this.groupModelRepository = groupModelRepository;
        this.groupFlowDispatcher = groupFlowDispatcher;
        this.appRestrictions = appRestrictions;

        // Initialize wakelock service
        this.wakeLock = new WakeLockServiceImpl(appContext, lifetime);

        // Initialize battery status service
        this.batteryStatus = new BatteryStatusServiceImpl(appContext);
    }
}

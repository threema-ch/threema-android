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

package ch.threema.app.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.format.DateUtils;
import android.widget.ImageView;

import com.bumptech.glide.RequestManager;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.asynctasks.AddOrUpdateWorkIdentityBackgroundTask;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.debug.AndroidContactSyncLogger;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.routines.UpdateBusinessAvatarRoutine;
import ch.threema.app.routines.UpdateFeatureLevelRoutine;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.stores.DatabaseContactStore;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.tasks.TaskCreator;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.SynchronizeContactsUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.models.ModelDeletedException;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.models.BasicContact;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.ReadReceiptPolicy;
import ch.threema.domain.models.TypingIndicatorPolicy;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.taskmanager.ActiveTaskCodec;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ContactModel.AcquaintanceLevel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ValidationMessage;
import ch.threema.storage.models.access.AccessModel;
import java8.util.function.Consumer;

import static ch.threema.app.glide.AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR;

public class ContactServiceImpl implements ContactService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ContactServiceImpl");

    private static final int TYPING_RECEIVE_TIMEOUT = (int) DateUtils.MINUTE_IN_MILLIS;

    private final Context context;
    private final AvatarCacheService avatarCacheService;
    private final DatabaseContactStore contactStore;
    private final DatabaseService databaseService;
    private final UserService userService;
    private final IdentityStore identityStore;
    @NonNull
    private final PreferenceService preferenceService;
    // NOTE: The contact model cache will become unnecessary once everything uses the new data
    // layer, since that data layer has caching built-in.
    private final Map<String, ContactModel> contactModelCache;
    @NonNull
    private final BlockedIdentitiesService blockedIdentitiesService;
    private final IdListService profilePicRecipientsService;
    private final FileService fileService;
    private final ApiService apiService;
    private final LicenseService licenseService;
    private final APIConnector apiConnector;
    @NonNull
    private final TaskCreator taskCreator;
    @NonNull
    private final MultiDeviceManager multiDeviceManager;
    private final Timer typingTimer;
    private final Map<String, TimerTask> typingTimerTasks;

    @NonNull
    private final ContactModelRepository contactModelRepository;

    private final List<String> typingIdentities = new ArrayList<>();

    private ContactModel me;

    public final static byte[] THREEMA_PUBLIC_KEY = new byte[]{ // *THREEMA
        58, 56, 101, 12, 104, 20, 53, -67, 31, -72, 73, -114, 33, 58, 41, 25,
        -80, -109, -120, -11, -128, 58, -92, 70, 64, -32, -9, 6, 50, 106, -122, 92,
    };

    public final static byte[] SUPPORT_PUBLIC_KEY = new byte[]{ // *SUPPORT
        15, -108, 77, 24, 50, 75, 33, 50, -58, 29, -114, 64, -81, -50, 96, -96,
        -21, -41, 1, -69, 17, -24, -101, -23, 73, 114, -44, 34, -98, -108, 114, 42,
    };

    public final static byte[] MY_DATA_PUBLIC_KEY = new byte[]{ // *MY3DATA
        59, 1, -123, 79, 36, 115, 110, 45, 13, 45, -61, -121, -22, -14, -64, 39,
        60, 80, 73, 5, 33, 71, 19, 35, 105, -65, 57, 96, -48, -96, -65, 2
    };

    // These are public keys of identities that will be immediately trusted (three green dots)
    public final static byte[][] TRUSTED_PUBLIC_KEYS = {
        THREEMA_PUBLIC_KEY,
        SUPPORT_PUBLIC_KEY,
        MY_DATA_PUBLIC_KEY,
    };

    public ContactServiceImpl(
        Context context,
        DatabaseContactStore contactStore,
        AvatarCacheService avatarCacheService,
        DatabaseService databaseService,
        UserService userService,
        IdentityStore identityStore,
        @NonNull PreferenceService preferenceService,
        @NonNull BlockedIdentitiesService blockedIdentitiesService,
        IdListService profilePicRecipientsService,
        FileService fileService,
        CacheService cacheService,
        ApiService apiService,
        LicenseService licenseService,
        APIConnector apiConnector,
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull TaskCreator taskCreator,
        @NonNull MultiDeviceManager multiDeviceManager
    ) {

        this.context = context;
        this.avatarCacheService = avatarCacheService;
        this.contactStore = contactStore;
        this.databaseService = databaseService;
        this.userService = userService;
        this.identityStore = identityStore;
        this.preferenceService = preferenceService;
        this.blockedIdentitiesService = blockedIdentitiesService;
        this.profilePicRecipientsService = profilePicRecipientsService;
        this.fileService = fileService;
        this.apiService = apiService;
        this.licenseService = licenseService;
        this.apiConnector = apiConnector;
        this.contactModelRepository = contactModelRepository;
        this.taskCreator = taskCreator;
        this.multiDeviceManager = multiDeviceManager;
        this.typingTimer = new Timer();
        this.typingTimerTasks = new HashMap<>();
        this.contactModelCache = cacheService.getContactModelCache();
    }

    @Override
    @NonNull
    public ContactModel getMe() {
        if (this.me == null && this.userService.getIdentity() != null) {
            this.me = ContactModel.create(
                this.userService.getIdentity(),
                this.userService.getPublicKey()
            );
            this.me.setPublicNickName(this.userService.getPublicNickname());
            this.me.setState(IdentityState.ACTIVE);
            this.me.setFirstName(context.getString(R.string.me_myself_and_i));
            this.me.verificationLevel = VerificationLevel.FULLY_VERIFIED;
            this.me.setFeatureMask(-1);
        }

        return this.me;
    }

    @Override
    @NonNull
    public List<ContactModel> getAllDisplayed(@NonNull ContactSelection contactSelection) {
        return this.find(new Filter() {
            @Override
            public IdentityState[] states() {
                if (preferenceService.showInactiveContacts()) {
                    switch (contactSelection) {
                        case EXCLUDE_INVALID:
                            return new IdentityState[]{
                                IdentityState.ACTIVE,
                                IdentityState.INACTIVE,
                            };
                        case INCLUDE_INVALID:
                        default:
                            return null;
                    }
                } else {
                    return new IdentityState[]{IdentityState.ACTIVE};
                }
            }

            @Override
            public Long requiredFeature() {
                return null;
            }

            @Override
            public Boolean fetchMissingFeatureLevel() {
                return null;
            }

            @Override
            public Boolean includeMyself() {
                return false;
            }

            @Override
            public Boolean includeHidden() {
                return false;
            }

            @Override
            public Boolean onlyWithReceiptSettings() {
                return false;
            }
        });
    }

    @Override
    @NonNull
    public List<ContactModel> getAll() {
        return find(null);
    }

    @Override
    @NonNull
    public List<ContactModel> find(Filter filter) {
        ContactModelFactory contactModelFactory = this.databaseService.getContactModelFactory();
        // TODO(ANDR-XXXX): move this to database factory!
        QueryBuilder queryBuilder = new QueryBuilder();
        List<String> placeholders = new ArrayList<>();

        List<ContactModel> result;
        if (filter != null) {
            IdentityState[] filterStates = filter.states();
            if (filterStates != null && filterStates.length > 0) {

                //dirty, add placeholder should be added to makePlaceholders
                queryBuilder.appendWhere(ContactModel.COLUMN_STATE + " IN (" + DatabaseUtil.makePlaceholders(filterStates.length) + ")");
                for (IdentityState s : filterStates) {
                    placeholders.add(s.toString());
                }
            }

            if (!filter.includeHidden()) {
                queryBuilder.appendWhere(ContactModel.COLUMN_ACQUAINTANCE_LEVEL + "=0");
            }

            if (!filter.includeMyself()) {
                queryBuilder.appendWhere(ContactModel.COLUMN_IDENTITY + "!=?");
                placeholders.add(getMe().getIdentity());
            }

            if (filter.onlyWithReceiptSettings()) {
                queryBuilder.appendWhere(ContactModel.COLUMN_TYPING_INDICATORS + " !=0 OR " + ContactModel.COLUMN_READ_RECEIPTS + " !=0");
            }

            result = contactModelFactory.convert
                (
                    queryBuilder,
                    placeholders.toArray(new String[0]),
                    null
                );
        } else {
            result = contactModelFactory.convert
                (
                    queryBuilder,
                    placeholders.toArray(new String[0]),
                    null
                );
        }

        // sort
        final boolean sortOrderFirstName = preferenceService.isContactListSortingFirstName();

        Collections.sort(result, ContactUtil.getContactComparator(sortOrderFirstName));

        if (filter != null) {

            final Long feature = filter.requiredFeature();

            //update feature level routine call
            if (feature != null) {
                if (filter.fetchMissingFeatureLevel()) {
                    //do not filtering with sql
                    UpdateFeatureLevelRoutine routine = new UpdateFeatureLevelRoutine(
                        contactModelRepository,
                        userService,
                        this.apiConnector,
                        result
                            .stream()
                            .filter(Objects::nonNull)
                            .filter(model -> !ThreemaFeature.hasFeature(model.getFeatureMask(), feature))
                            .map(Contact::getIdentity)
                            .collect(Collectors.toList())
                    );
                    routine.run();
                }

                // Filter the result by the required feature
                result = result
                    .stream()
                    .map(outdatedModel -> getByIdentity(outdatedModel.getIdentity()))
                    .filter(model -> model != null && ThreemaFeature.hasFeature(model.getFeatureMask(), feature))
                    .collect(Collectors.toList());
            }

        }

        for (int n = 0; n < result.size(); n++) {
            synchronized (this.contactModelCache) {
                String identity = result.get(n).getIdentity();
                if (this.contactModelCache.containsKey(identity)) {
                    //replace selected model with the cached one
                    //but do not cache the result
                    result.set(n, this.contactModelCache.get(identity));
                }
            }
        }
        return result;
    }

    @Override
    @Nullable
    public ContactModel getByLookupKey(String lookupKey) {
        if (lookupKey == null) {
            return null;
        }

        return this.contactStore.getContactModelForLookupKey(lookupKey);
    }

    @Override
    @Nullable
    public ContactModel getByIdentity(@Nullable String identity) {
        if (identity == null) {
            return null;
        }

        //return me object
        if (this.getMe() != null && this.getMe().getIdentity().equals(identity)) {
            return this.me;
        }

        synchronized (this.contactModelCache) {
            if (this.contactModelCache.containsKey(identity)) {
                return this.contactModelCache.get(identity);
            }
        }
        return this.cache(this.contactStore.getContactForIdentity(identity));
    }

    private ContactModel cache(ContactModel contactModel) {
        if (contactModel != null) {
            this.contactModelCache.put(contactModel.getIdentity(), contactModel);
        }
        return contactModel;
    }

    @Override
    public List<ContactModel> getByIdentities(String[] identities) {
        List<ContactModel> models = new ArrayList<>();
        for (String s : identities) {
            ContactModel model = this.getByIdentity(s);
            if (model != null) {
                models.add(model);
            }
        }

        return models;
    }

    @Override
    public List<ContactModel> getByIdentities(List<String> identities) {
        List<ContactModel> models = new ArrayList<>();
        for (String s : identities) {
            ContactModel model = this.getByIdentity(s);
            if (model != null) {
                models.add(model);
            }
        }

        return models;
    }

    @Override
    @NonNull
    public List<ContactModel> getAllDisplayedWork(@NonNull ContactSelection selection) {
        return Functional.filter(this.getAllDisplayed(selection), (IPredicateNonNull<ContactModel>) ContactModel::isWorkVerified);
    }

    @Override
    @NonNull
    public List<ContactModel> getAllWork() {
        return Functional.filter(this.getAll(), (IPredicateNonNull<ContactModel>) ContactModel::isWorkVerified);
    }

    @Override
    public int countIsWork() {
        int count = 0;
        Cursor c = this.databaseService.getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM contacts " +
                "WHERE " + ContactModel.COLUMN_IS_WORK + " = 1 " +
                "AND " + ContactModel.COLUMN_ACQUAINTANCE_LEVEL + " = 0", null);

        if (c != null) {
            if (c.moveToFirst()) {
                count = c.getInt(0);
            }
            c.close();
        }
        return count;
    }

    @Override
    public List<ContactModel> getCanReceiveProfilePics() {
        return Functional.filter(
            this.find(new Filter() {
                @Override
                public IdentityState[] states() {
                    if (preferenceService.showInactiveContacts()) {
                        return null;
                    }
                    return new IdentityState[]{IdentityState.ACTIVE};
                }

                @Override
                public Long requiredFeature() {
                    return null;
                }

                @Override
                public Boolean fetchMissingFeatureLevel() {
                    return null;
                }

                @Override
                public Boolean includeMyself() {
                    return false;
                }

                @Override
                public Boolean includeHidden() {
                    return false;
                }

                @Override
                public Boolean onlyWithReceiptSettings() {
                    return false;
                }
            }),
            (IPredicateNonNull<ContactModel>) type -> !ContactUtil.isEchoEchoOrGatewayContact(type)
        );
    }

    @Override
    @Nullable
    public List<String> getSynchronizedIdentities() {
        Cursor c = this.databaseService.getReadableDatabase().rawQuery("" +
                "SELECT identity FROM contacts " +
                "WHERE androidContactId IS NOT NULL AND androidContactId != ?",
            new String[]{""});

        if (c != null) {
            List<String> identities = new ArrayList<>();
            while (c.moveToNext()) {
                identities.add(c.getString(0));
            }
            c.close();
            return identities;
        }

        return null;
    }

    @Override
    @Nullable
    public List<String> getIdentitiesByVerificationLevel(VerificationLevel verificationLevel) {
        Cursor c = this.databaseService.getReadableDatabase().rawQuery("" +
                "SELECT identity FROM contacts " +
                "WHERE verificationLevel = ?",
            new String[]{String.valueOf(verificationLevel.getCode())});

        if (c != null) {
            List<String> identities = new ArrayList<>();
            while (c.moveToNext()) {
                identities.add(c.getString(0));
            }
            c.close();
            return identities;
        }

        return null;
    }

    @Override
    public void setIsTyping(final String identity, final boolean isTyping) {
        // cancel old timer task
        synchronized (typingTimerTasks) {
            TimerTask oldTimerTask = typingTimerTasks.get(identity);
            if (oldTimerTask != null) {
                oldTimerTask.cancel();
                typingTimerTasks.remove(identity);
            }
        }

        //get the cached model
        final ContactModel contact = this.getByIdentity(identity);
        synchronized (this.typingIdentities) {
            boolean contains = this.typingIdentities.contains(identity);
            if (isTyping) {
                if (!contains) {
                    this.typingIdentities.add(identity);
                }
            } else {
                if (contains) {
                    this.typingIdentities.remove(identity);
                }
            }
        }

        ListenerManager.contactTypingListeners.handle(listener -> listener.onContactIsTyping(contact, isTyping));

        // schedule a new timer task to reset typing state after timeout if necessary
        if (isTyping) {
            synchronized (typingTimerTasks) {
                TimerTask newTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        synchronized (typingIdentities) {
                            typingIdentities.remove(identity);
                        }

                        ListenerManager.contactTypingListeners.handle(listener -> listener.onContactIsTyping(contact, false));

                        synchronized (typingTimerTasks) {
                            typingTimerTasks.remove(identity);
                        }
                    }
                };

                typingTimerTasks.put(identity, newTimerTask);
                typingTimer.schedule(newTimerTask, TYPING_RECEIVE_TIMEOUT);
            }
        }
    }

    @Override
    public boolean isTyping(String identity) {
        synchronized (this.typingIdentities) {
            return this.typingIdentities.contains(identity);
        }
    }

    @Override
    public void sendTypingIndicator(String toIdentity, boolean isTyping) {
        ContactModel contactModel = getByIdentity(toIdentity);
        if (contactModel == null) {
            logger.error("Cannot send typing indicator");
            return;
        }

        boolean sendTypingIndicator;
        switch (contactModel.getTypingIndicators()) {
            case ContactModel.SEND:
                sendTypingIndicator = true;
                break;
            case ContactModel.DONT_SEND:
                sendTypingIndicator = false;
                break;
            default:
                sendTypingIndicator = preferenceService.isTypingIndicatorEnabled();
                break;
        }

        if (!sendTypingIndicator) {
            return;
        }

        try {
            createReceiver(contactModel).sendTypingIndicatorMessage(isTyping);
        } catch (ThreemaException e) {
            logger.error("Could not send typing indicator", e);
        }
    }

    @Override
    public void setAcquaintanceLevel(
        @NonNull String identity,
        @NonNull AcquaintanceLevel acquaintanceLevel
    ) {
        final ch.threema.data.models.ContactModel contactModel =
            contactModelRepository.getByIdentity(identity);

        if (contactModel != null) {
            try {
                contactModel.setAcquaintanceLevelFromLocal(acquaintanceLevel);
            } catch (ModelDeletedException e) {
                logger.warn("Could not set acquaintance level because model has been deleted", e);
            }
        }
    }

    @Override
    public void setIsArchived(
        @NonNull String identity,
        boolean isArchived,
        @NonNull TriggerSource triggerSource
    ) {
        final ch.threema.data.models.ContactModel contactModel =
            contactModelRepository.getByIdentity(identity);
        if (contactModel == null) {
            logger.warn(
                "Cannot set isArchived={} for identity '{}' because contact model is null",
                isArchived,
                identity
            );
            return;
        }

        try {
            switch (triggerSource) {
                case LOCAL:
                case REMOTE:
                    contactModel.setIsArchivedFromLocalOrRemote(isArchived);
                    break;
                case SYNC:
                    contactModel.setIsArchivedFromSync(isArchived);
                    break;
            }
        } catch (ModelDeletedException e) {
            logger.warn("Could not set isArchived={} because model has been deleted", isArchived, e);
        }
    }

    @Override
    public void bumpLastUpdate(@NonNull String identity) {
        logger.info("Bump last update for contact with identity {}", identity);
        if (getByIdentity(identity) != null) {
            Date lastUpdate = new Date();
            invalidateCache(identity);
            databaseService.getContactModelFactory().setLastUpdate(identity, lastUpdate);
            ListenerManager.contactListeners.handle(listener -> listener.onModified(identity));
        } else {
            logger.warn(
                "Could not bump last update because the contact with identity {} is null",
                identity
            );
        }
    }

    @Nullable
    @Override
    public Date getLastUpdate(@NonNull String identity) {
        ContactModel contactModel = getByIdentity(identity);
        if (contactModel != null) {
            return contactModel.getLastUpdate();
        } else {
            return null;
        }
    }

    @Override
    public void clearLastUpdate(@NonNull String identity) {
        if (getByIdentity(identity) != null) {
            invalidateCache(identity);
            databaseService.getContactModelFactory().setLastUpdate(identity, null);
            ListenerManager.contactListeners.handle(listener -> listener.onModified(identity));
        }
    }

    @Override
    @Deprecated
    public void save(@NonNull ContactModel contactModel) {
        logger.info("Saving old contact model of contact {}", contactModel.getIdentity());

        for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
            logger.info("{}", stackTraceElement);
        }

        this.contactStore.addContact(contactModel);
    }

    @NonNull
    @Override
    public AccessModel getAccess(@Nullable String identity) {
        if (identity == null) {
            return new AccessModel() {
                @Override
                public boolean canDelete() {
                    return false;
                }

                @Override
                public ValidationMessage[] canNotDeleteReasons() {
                    return new ValidationMessage[]{
                        new ValidationMessage(
                            context.getString(R.string.can_not_delete_contact),
                            context.getString(R.string.can_not_delete_not_valid)
                        )
                    };
                }
            };
        } else {
            boolean isInGroup = false;
            Cursor c = this.databaseService.getReadableDatabase().rawQuery(
                DatabaseUtil.IS_GROUP_MEMBER_QUERY,
                identity
            );

            if (c != null) {
                if (c.moveToFirst()) {
                    isInGroup = c.getInt(0) == 1;
                }
                c.close();
            }

            if (isInGroup) {
                return new AccessModel() {
                    @Override
                    public boolean canDelete() {
                        return false;
                    }

                    @Override
                    public ValidationMessage[] canNotDeleteReasons() {
                        return new ValidationMessage[]{
                            new ValidationMessage(
                                context.getString(R.string.can_not_delete_contact),
                                context.getString(R.string.can_not_delete_contact_until_in_group)
                            )
                        };
                    }
                };
            }
        }

        return new AccessModel() {
            @Override
            public boolean canDelete() {
                return true;
            }

            @Override
            public ValidationMessage[] canNotDeleteReasons() {
                return new ValidationMessage[0];
            }
        };
    }

    @AnyThread
    @Override
    public Bitmap getAvatar(@Nullable ContactModel contact, @NonNull AvatarOptions options) {
        if (contact == null) {
            return null;
        }

        // Check whether we should update the business avatar
        ch.threema.data.models.ContactModel contactModel = contactModelRepository.getByIdentity(contact.getIdentity());
        if (contactModel != null) {
            ContactModelData data = contactModel.getData().getValue();
            //check if a business avatar update is necessary
            if (data != null && data.isGatewayContact() && data.isAvatarExpired()) {
                //simple start
                UpdateBusinessAvatarRoutine.startUpdate(
                    contactModel,
                    this.fileService,
                    this,
                    apiService
                );
            }
        }
        // Note that we should not abort if no new contact model can be found as the new model does
        // not exist for the user itself whereas the old model may refer to the user. Therefore, we
        // may still get an avatar for the provided (old) model.

        // If the custom avatar is requested without default fallback and there is no avatar for
        // this contact, we can return null directly. Important: This is necessary to prevent glide
        // from logging an unnecessary error stack trace.
        if (options.defaultAvatarPolicy == CUSTOM_AVATAR && !hasAvatarOrContactPhoto(contact)) {
            return null;
        }

        return this.avatarCacheService.getContactAvatar(contact, options);
    }

    private boolean hasAvatarOrContactPhoto(@Nullable ContactModel contact) {
        if (contact == null) {
            return false;
        }

        return fileService.hasUserDefinedProfilePicture(contact.getIdentity()) || fileService.hasContactDefinedProfilePicture(contact.getIdentity());
    }

    @Override
    public @ColorInt int getAvatarColor(@Nullable ContactModel contact) {
        if (this.preferenceService.isDefaultContactPictureColored() && contact != null) {
            return contact.getThemedColor(context);
        }
        return ColorUtil.getInstance().getCurrentThemeGray(this.context);
    }

    @Override
    public @ColorInt int getAvatarColor(@Nullable ch.threema.data.models.ContactModel contactModel) {
        ContactModelData contactModelData = contactModel != null ? contactModel.getData().getValue() : null;
        if (this.preferenceService.isDefaultContactPictureColored() && contactModelData != null) {
            return contactModelData.getThemedColor(context);
        }
        return ColorUtil.getInstance().getCurrentThemeGray(this.context);
    }

    @AnyThread
    @Override
    public void loadAvatarIntoImage(
        @NonNull ContactModel model,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    ) {
        avatarCacheService.loadContactAvatarIntoImage(model, imageView, options, requestManager);
    }

    @Override
    @NonNull
    public ContactMessageReceiver createReceiver(ContactModel contact) {
        // Note that at this point we can assume that the service manager exists, as the contact
        // service is obviously created.
        ServiceManager serviceManager = ThreemaApplication.requireServiceManager();

        return new ContactMessageReceiver(
            contact,
            this,
            serviceManager,
            this.databaseService,
            this.identityStore,
            this.blockedIdentitiesService,
            serviceManager.getModelRepositories().getContacts()
        );
    }

    @Override
    @Nullable
    public ContactMessageReceiver createReceiver(@NonNull ch.threema.data.models.ContactModel contact) {
        return createReceiver(contact.getIdentity());
    }

    @Override
    @Nullable
    public ContactMessageReceiver createReceiver(@NonNull String identity) {
        ContactModel contactModel = getByIdentity(identity);
        if (contactModel != null) {
            return createReceiver(contactModel);
        } else {
            return null;
        }
    }

    @Override
    @WorkerThread
    public boolean updateAllContactNamesFromAndroidContacts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(ThreemaApplication.getAppContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        AndroidContactSyncLogger androidContactSyncLogger = new AndroidContactSyncLogger();

        this.contactModelRepository.getAll()
            .stream()
            .filter(contactModel -> {
                ContactModelData contactModelData = contactModel.getData().getValue();
                return contactModelData != null && contactModelData.isLinkedToAndroidContact();
            })
            .forEach(contactModel -> {
                try {
                    AndroidContactUtil.getInstance().updateNameByAndroidContact(contactModel, androidContactSyncLogger);
                } catch (ThreemaException e) {
                    logger.error("Unable to update contact name", e);
                }
            });

        androidContactSyncLogger.logDuplicates();

        return true;
    }

    @Override
    public void removeAllSystemContactLinks() {
        this.contactModelRepository.getAll()
            .stream()
            .filter(contactModel -> {
                ContactModelData contactModelData = contactModel.getData().getValue();
                return contactModelData != null && contactModelData.isLinkedToAndroidContact();
            })
            .forEach(ch.threema.data.models.ContactModel::removeAndroidContactLink);
    }

    @Override
    public boolean setUserDefinedProfilePicture(
        @Nullable final ContactModel contactModel,
        @Nullable File temporaryAvatarFile,
        @NonNull TriggerSource triggerSource
    ) {
        if (contactModel != null && temporaryAvatarFile != null) {
            if (this.fileService.writeUserDefinedProfilePicture(contactModel.getIdentity(), temporaryAvatarFile)) {
                if (triggerSource != TriggerSource.SYNC && multiDeviceManager.isMultiDeviceActive()) {
                    taskCreator.scheduleUserDefinedProfilePictureUpdate(contactModel.getIdentity());
                }
                return this.onUserDefinedProfilePictureSet(contactModel);
            }
        }
        return false;
    }

    @Override
    public boolean setUserDefinedProfilePicture(
        @NonNull String identity,
        @Nullable File temporaryAvatarFile,
        @NonNull TriggerSource triggerSource
    ) {
        ContactModel contactModel = getByIdentity(identity);
        return setUserDefinedProfilePicture(contactModel, temporaryAvatarFile, triggerSource);
    }

    @Override
    public boolean setUserDefinedProfilePicture(
        @NonNull final String identity,
        @Nullable byte[] avatar,
        @NonNull TriggerSource triggerSource
    ) throws IOException, MasterKeyLockedException {
        ContactModel contactModel = getByIdentity(identity);
        if (contactModel == null) {
            logger.error("Cannot set user defined profile for unknown identity {}", identity);
            return false;
        }

        if (avatar == null) {
            logger.error("Cannot set avatar that is null for identity {}", identity);
            return false;
        }

        if (this.fileService.writeUserDefinedProfilePicture(contactModel.getIdentity(), avatar)) {
            if (triggerSource != TriggerSource.SYNC && multiDeviceManager.isMultiDeviceActive()) {
                taskCreator.scheduleUserDefinedProfilePictureUpdate(contactModel.getIdentity());
            }
            return this.onUserDefinedProfilePictureSet(contactModel);
        }
        return false;
    }

    private boolean onUserDefinedProfilePictureSet(final ContactModel contactModel) {
        if (this.userService.isMe(contactModel.getIdentity())) {
            logger.error("The users profile picture must not be set via contact service");
        } else {
            ListenerManager.contactListeners.handle(listener -> listener.onAvatarChanged(contactModel.getIdentity()));
            ShortcutUtil.updateShareTargetShortcut(createReceiver(contactModel));
        }

        return true;
    }

    @Override
    public boolean removeUserDefinedProfilePicture(
        @NonNull final String identity,
        @NonNull TriggerSource triggerSource
    ) {
        if (userService.isMe(identity)) {
            logger.error("The user's profile picture cannot be removed using the contact service");
            return false;
        }

        if (this.fileService.removeUserDefinedProfilePicture(identity)) {
            if (triggerSource != TriggerSource.SYNC && multiDeviceManager.isMultiDeviceActive()) {
                taskCreator.scheduleUserDefinedProfilePictureUpdate(identity);
            }
            ListenerManager.contactListeners.handle(listener -> listener.onAvatarChanged(identity));
            return true;
        }

        return false;
    }

    @Override
    @NonNull
    public ProfilePictureSharePolicy getProfilePictureSharePolicy() {
        ProfilePictureSharePolicy.Policy policy;

        switch (preferenceService.getProfilePicRelease()) {
            case PreferenceService.PROFILEPIC_RELEASE_EVERYONE:
                policy = ProfilePictureSharePolicy.Policy.EVERYONE;
                break;
            case PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST:
                policy = ProfilePictureSharePolicy.Policy.ALLOW_LIST;
                break;
            default:
                policy = ProfilePictureSharePolicy.Policy.NOBODY;
                break;
        }

        List<String> allowedIdentities = policy == ProfilePictureSharePolicy.Policy.ALLOW_LIST
            ? Arrays.asList(profilePicRecipientsService.getAll())
            : Collections.emptyList();

        return new ProfilePictureSharePolicy(policy, allowedIdentities);
    }

    @Override
    public boolean isContactAllowedToReceiveProfilePicture(@NonNull String identity) {
        int profilePicRelease = preferenceService.getProfilePicRelease();
        return profilePicRelease == PreferenceService.PROFILEPIC_RELEASE_EVERYONE ||
            (profilePicRelease == PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST && profilePicRecipientsService.has(identity));
    }

    @Override
    public boolean showBadge(@Nullable ContactModel contactModel) {
        if (contactModel != null) {
            if (ConfigUtils.isWorkBuild()) {
                if (userService.isMe(contactModel.getIdentity())) {
                    return false;
                }
                return contactModel.getIdentityType() == IdentityType.NORMAL && !ContactUtil.isEchoEchoOrGatewayContact(contactModel);
            } else {
                return contactModel.getIdentityType() == IdentityType.WORK;
            }
        }
        return false;
    }

    @Override
    public boolean showBadge(@NonNull ContactModelData contactModelData) {
        if (ConfigUtils.isWorkBuild()) {
            if (userService.isMe(contactModelData.identity)) {
                return false;
            }
            return contactModelData.identityType == IdentityType.NORMAL
                && !ContactUtil.isEchoEchoOrGatewayContact(contactModelData.identity);
        } else {
            return contactModelData.identityType == IdentityType.WORK;
        }
    }

    /**
     * Get Android contact lookup key Uri in String representation to be used for Notification.Builder.addPerson()
     *
     * @param contactModel ContactModel to get Uri for
     * @return Uri of Android contact as a string or null if there's no linked contact or permission to access contacts has not been granted
     */
    @Override
    public @Nullable String getAndroidContactLookupUriString(ContactModel contactModel) {
        String contactLookupUri = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(ThreemaApplication.getAppContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                if (contactModel != null) {
                    final String androidContactLookupKey = contactModel.getAndroidContactLookupKey();
                    if (androidContactLookupKey != null) {
                        Uri lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, androidContactLookupKey);
                        if (lookupUri != null) {
                            contactLookupUri = lookupUri.toString();
                        }
                    }
                }
            }
        }
        return contactLookupUri;
    }

    @Override
    public void invalidateCache(@NonNull String identity) {
        synchronized (this.contactModelCache) {
            this.contactModelCache.remove(identity);
        }
    }

    @Override
    @WorkerThread
    public void fetchAndCacheContact(@NonNull String identity) throws APIConnector.HttpConnectionException, APIConnector.NetworkException, MissingPublicKeyException {
        // Check if the contact is cached or stored locally (or a special contact)
        if (contactStore.getContactForIdentityIncludingCache(identity) != null) {
            return;
        }

        // Check if the identity is a work contact that should be known
        if (ConfigUtils.isWorkBuild()) {
            fetchAndCreateWorkContact(identity);
            if (contactStore.getContactForIdentity(identity) != null) {
                return;
            }
        }

        // Check if contact is known after contact synchronization (if enabled)
        if (preferenceService.isSyncContacts()) {
            // Synchronize contact
            try {
                SynchronizeContactsUtil.startDirectly(identity);
            } catch (SecurityException exception) {
                logger.error("Could not check whether the identity is a known android contact");
                // We still continue because a missing contacts permission should not prevent
                // fetching and caching a new contact.
            }

            // Check again locally
            if (contactStore.getContactForIdentity(identity) != null) {
                return;
            }
        }

        try {
            // Otherwise try to fetch the identity
            BasicContact contactModel = fetchPublicKeyForIdentity(identity);
            if (contactModel != null) {
                contactStore.addCachedContact(contactModel);
            }
        } catch (APIConnector.HttpConnectionException e) {
            if (e.getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                logger.warn("Identity fetch for identity '{}' returned 404", identity);
                throw new MissingPublicKeyException("No public key found");
            } else {
                throw e;
            }
        }
    }

    /**
     * Try to fetch a contact from work api and add it to the contact database. Note that this
     * method does not throw any exceptions when the connection to the server could not be
     * established.
     *
     * @param identity the identity of the contact that might be a work contact
     */
    @WorkerThread
    private void fetchAndCreateWorkContact(@NonNull String identity) {
        new AddOrUpdateWorkIdentityBackgroundTask(
            identity,
            userService.getIdentity(),
            licenseService,
            apiConnector,
            contactModelRepository
        ).runSynchronously();
    }

    @Override
    @WorkerThread
    public void resetReceiptsSettings() {
        List<ContactModel> contactModels = find(new Filter() {
            @Override
            public IdentityState[] states() {
                return new IdentityState[]{IdentityState.ACTIVE, IdentityState.INACTIVE};
            }

            @Override
            public Long requiredFeature() {
                return null;
            }

            @Override
            public Boolean fetchMissingFeatureLevel() {
                return null;
            }

            @Override
            public Boolean includeMyself() {
                return false;
            }

            @Override
            public Boolean includeHidden() {
                return true;
            }

            @Override
            public Boolean onlyWithReceiptSettings() {
                return true;
            }
        });

        contactModels
            .stream()
            .map(contactModel -> contactModelRepository.getByIdentity(contactModel.getIdentity()))
            .forEach(contactModel -> {
                if (contactModel != null) {
                    contactModel.setReadReceiptPolicyFromLocal(ReadReceiptPolicy.DEFAULT);
                    contactModel.setTypingIndicatorPolicyFromLocal(TypingIndicatorPolicy.DEFAULT);
                }
            });
    }

    @Override
    @UiThread
    public void reportSpam(@NonNull final String identity, @Nullable Consumer<Void> onSuccess, @Nullable Consumer<String> onFailure) {
        new Thread(() -> {
            try {
                ch.threema.data.models.ContactModel spammerContactModel = contactModelRepository.getByIdentity(identity);
                if (spammerContactModel == null) {
                    logger.warn("No contact with identity {} found", identity);
                    return;
                }
                ContactModelData contactModelData = spammerContactModel.getData().getValue();
                if (contactModelData == null) {
                    logger.warn("Contact model data for identity {} is null", identity);
                    return;
                }

                apiConnector.reportJunk(identityStore, identity, contactModelData.nickname);

                // Note: This is semantically wrong. Once we support multi-device, we probably
                //       need to adapt the logic. Protocol discussions are ongoing.
                spammerContactModel.setAcquaintanceLevelFromLocal(AcquaintanceLevel.GROUP);

                if (onSuccess != null) {
                    RuntimeUtil.runOnUiThread(() -> onSuccess.accept(null));
                }
            } catch (Exception e) {
                logger.error("Error reporting spam", e);
                if (onFailure != null) {
                    RuntimeUtil.runOnUiThread(() -> onFailure.accept(e.getMessage()));
                }
            }
        }).start();
    }

    @Nullable
    @Override
    public ForwardSecuritySessionState getForwardSecurityState(
        @NonNull ContactModel contactModel,
        @NonNull ActiveTaskCodec handle
    ) {
        if (!ThreemaFeature.canForwardSecurity(contactModel.getFeatureMask())) {
            return ForwardSecuritySessionState.unsupportedByRemote();
        }
        try {
            DHSession session = ThreemaApplication.requireServiceManager().getDHSessionStore()
                .getBestDHSession(
                    userService.getIdentity(),
                    contactModel.getIdentity(),
                    handle
                );
            if (session == null) {
                return ForwardSecuritySessionState.noSession();
            }
            DHSession.State dhState = session.getState();
            DHSession.DHVersions dhVersions = session.getCurrent4DHVersions();
            return ForwardSecuritySessionState.fromDHState(dhState, dhVersions);
        } catch (Exception e) {
            logger.error("Could not get forward security state", e);
            return null;
        }
    }

    /**
     * Fetch a public key for an identity and return it in a contact model.
     *
     * @param identity Identity to add a contact for
     * @return the contact model of the identity in case of success, null otherwise
     * @throws ch.threema.domain.protocol.api.APIConnector.HttpConnectionException when the identity cannot be fetched
     * @throws ch.threema.domain.protocol.api.APIConnector.NetworkException        when the identity cannot be fetched
     */
    @WorkerThread
    private @Nullable BasicContact fetchPublicKeyForIdentity(@NonNull String identity) throws APIConnector.HttpConnectionException, APIConnector.NetworkException {
        ContactModel contactModel = contactStore.getContactForIdentity(identity);
        if (contactModel != null) {
            return contactModel.toBasicContact();
        }

        APIConnector.FetchIdentityResult result;
        try {
            result = this.apiConnector.fetchIdentity(identity);

            if (result == null || result.publicKey == null) {
                return null;
            }
        } catch (ThreemaException e) {
            logger.error("Fetch failed: ", e);
            throw new APIConnector.NetworkException(e);
        }

        IdentityType identityType;
        switch (result.type) {
            case 0:
                identityType = IdentityType.NORMAL;
                break;
            case 1:
                identityType = IdentityType.WORK;
                break;
            default:
                logger.warn("Identity fetch returned invalid identity type: {}", result.type);
                identityType = IdentityType.NORMAL;
        }
        IdentityState identityState;
        if (result.state == IdentityState.ACTIVE.getValue()) {
            identityState = IdentityState.ACTIVE;
        } else if (result.state == IdentityState.INACTIVE.getValue()) {
            identityState = IdentityState.INACTIVE;
        } else if (result.state == IdentityState.INVALID.getValue()) {
            identityState = IdentityState.INVALID;
        } else {
            logger.warn("Identity fetch returned invalid identity state: {}", result.state);
            identityState = IdentityState.ACTIVE;
        }

        return BasicContact.javaCreate(
            result.identity,
            result.publicKey,
            result.featureMask,
            identityState,
            identityType
        );
    }

    @Override
    public void resetAllNotificationTriggerPolicyOverrideFromLocal() {
        contactModelRepository.getAll().stream().forEach(
            contactModel -> contactModel.setNotificationTriggerPolicyOverrideFromLocal(null)
        );
    }

    @Override
    @NonNull
    public Set<String> getRemovedContacts() {
        /*
            SELECT identity FROM contacts AS co WHERE acquaintanceLevel = 1 AND (
                NOT EXISTS (
                    SELECT 1 FROM group_member AS gm WHERE gm.identity = co.identity
                ) AND NOT EXISTS (
                    SELECT 1 FROM m_group AS g WHERE g.creatorIdentity = co.identity
                )
            );
         */
        final @NonNull String query = "SELECT " + ContactModel.COLUMN_IDENTITY + " FROM " + ContactModel.TABLE + " AS co WHERE "
            + ContactModel.COLUMN_ACQUAINTANCE_LEVEL + " = " + AcquaintanceLevel.GROUP.ordinal() + " AND ( "
            + "NOT EXISTS ("
            + " SELECT 1 FROM " + GroupMemberModel.TABLE + " AS gm WHERE"
            + " gm." + GroupMemberModel.COLUMN_IDENTITY + " = co." + ContactModel.COLUMN_IDENTITY
            + " ) AND NOT EXISTS ("
            + " SELECT 1 FROM " + GroupModel.TABLE + " AS g WHERE"
            + " g." + GroupModel.COLUMN_CREATOR_IDENTITY + " = co." + ContactModel.COLUMN_IDENTITY
            + " )"
            + ");";
        final @Nullable Cursor cursor = this.databaseService
            .getReadableDatabase()
            .rawQuery(query);
        if (cursor == null) {
            logger.error("Failed to query for deleted contacts");
            return new HashSet<>();
        }
        try (cursor) {
            final @NonNull Set<String> identities = new HashSet<>();
            while (cursor.moveToNext()) {
                identities.add(cursor.getString(cursor.getColumnIndexOrThrow(ContactModel.COLUMN_IDENTITY)));
            }
            return identities;
        } catch (Exception exception) {
            logger.error("Failed to query for deleted contacts", exception);
            return new HashSet<>();
        }
    }
}

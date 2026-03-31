package ch.threema.app.services;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.RequestManager;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import ch.threema.app.AppConstants;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.GroupDetailActivity;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.avatarcache.AvatarCacheService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.tasks.OutgoingGroupSyncRequestTask;
import ch.threema.app.utils.ConversationUtil;
import ch.threema.app.utils.GroupFeatureSupport;
import ch.threema.app.utils.GroupUtil;
import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.data.datatypes.IdColor;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.models.GroupIdentity;
import ch.threema.data.models.GroupModel;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.models.ModelDeletedException;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.UserState;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.ChunkedSequence;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.factories.GroupMessageModelFactory;
import ch.threema.storage.factories.RejectedGroupMessageFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.group.GroupMemberModel;
import ch.threema.storage.models.group.GroupMessageModel;
import ch.threema.storage.models.group.GroupModelOld;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.access.Access;
import ch.threema.storage.models.access.GroupAccessModel;

import static ch.threema.app.utils.GroupUtil.getUniqueIdString;

public class GroupServiceImpl implements GroupService {
    private static final Logger logger = getThreemaLogger("GroupServiceImpl");

    private final Context context;

    // Services
    private final @NonNull ServiceManager serviceManager;
    private final @NonNull UserService userService;
    private final @NonNull ContactService contactService;
    private final @NonNull DatabaseService databaseService;
    private final @NonNull AvatarCacheService avatarCacheService;
    private final @NonNull FileService fileService;
    private final @NonNull WallpaperService wallpaperService;
    private final @NonNull ConversationCategoryService conversationCategoryService;
    private final @NonNull RingtoneService ringtoneService;
    private final @NonNull ConversationTagService conversationTagService;
    private final @NonNull PreferenceService preferenceService;

    // Model repositories
    @NonNull
    private final ContactModelRepository contactModelRepository;
    @NonNull
    private final GroupModelRepository groupModelRepository;

    // TODO(ANDR-3755): Consolidate this cache
    private final SparseArrayCompat<Map<String, IdColor>> groupMemberColorCache;
    private final SparseArrayCompat<GroupModelOld> groupModelCache;
    private final SparseArrayCompat<String[]> groupIdentityCache;
    private @Nullable TaskManager taskManager = null;

    public GroupServiceImpl(
        @NonNull Context context,
        @NonNull CacheService cacheService,
        @NonNull UserService userService,
        @NonNull ContactService contactService,
        @NonNull DatabaseService databaseService,
        @NonNull AvatarCacheService avatarCacheService,
        @NonNull FileService fileService,
        @NonNull WallpaperService wallpaperService,
        @NonNull ConversationCategoryService conversationCategoryService,
        @NonNull RingtoneService ringtoneService,
        @NonNull ConversationTagService conversationTagService,
        @NonNull PreferenceService preferenceService,
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull GroupModelRepository groupModelRepository,
        @NonNull ServiceManager serviceManager
    ) {
        this.context = context;

        this.userService = userService;
        this.contactService = contactService;
        this.databaseService = databaseService;
        this.avatarCacheService = avatarCacheService;
        this.fileService = fileService;
        this.wallpaperService = wallpaperService;
        this.conversationCategoryService = conversationCategoryService;
        this.ringtoneService = ringtoneService;
        this.preferenceService = preferenceService;
        this.conversationTagService = conversationTagService;
        this.serviceManager = serviceManager;

        this.contactModelRepository = contactModelRepository;
        this.groupModelRepository = groupModelRepository;

        this.groupModelCache = cacheService.getGroupModelCache();
        this.groupIdentityCache = cacheService.getGroupIdentityCache();
        this.groupMemberColorCache = cacheService.getGroupMemberColorCache();
    }

    @Override
    @NonNull
    public List<GroupModelOld> getAll() {
        return this.getAll(null);
    }

    @Override
    @NonNull
    public List<GroupModelOld> getAll(GroupFilter filter) {
        List<GroupModelOld> res = new ArrayList<>(this.databaseService.getGroupModelFactory().filter(filter));

        if (filter != null && !filter.includeLeftGroups()) {
            Iterator<GroupModelOld> iterator = res.iterator();
            while (iterator.hasNext()) {
                GroupModelOld groupModel = iterator.next();
                if (!isGroupMember(groupModel)) {
                    iterator.remove();
                }
            }
        }

        for (GroupModelOld m : res) {
            this.cache(m);
        }

        return res;
    }

    /**
     * Adds the group model to the cache if it is not present yet
     */
    @Nullable
    private GroupModelOld cache(@Nullable GroupModelOld groupModel) {
        if (groupModel == null) {
            return null;
        }
        synchronized (this.groupModelCache) {
            final @Nullable GroupModelOld existingGroupModel = groupModelCache.get(groupModel.getId());
            if (existingGroupModel != null) {
                return existingGroupModel;
            }
            groupModelCache.put(groupModel.getId(), groupModel);
            return groupModel;
        }
    }

    @Override
    public void removeGroupBelongings(
        @NonNull GroupModel groupModel,
        @NonNull TriggerSource triggerSource
    ) {
        // Obtain some services through service manager
        //
        // Note: We cannot put these services in the constructor due to circular dependencies.
        // TODO(ANDR-2463): Dissolve circular dependencies
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            logger.error("Missing serviceManager, cannot remove group");
            return;
        }
        final ConversationService conversationService;
        final BallotService ballotService;
        try {
            conversationService = serviceManager.getConversationService();
            ballotService = serviceManager.getBallotService();
        } catch (ThreemaException e) {
            logger.error("Could not obtain services when removing group", e);
            return;
        }

        GroupMessageReceiver messageReceiver = createReceiver(groupModel);

        // Delete polls
        ballotService.remove(messageReceiver);

        // Remove all files that belong to messages of this group
        try (ChunkedSequence<GroupMessageModel> messageModels =
                 databaseService.getGroupMessageModelFactory().getByGroupId(groupModel.getDatabaseId())) {
            for (GroupMessageModel messageModel : messageModels) {
                this.fileService.removeMessageFiles(messageModel, true);
            }
        }

        // Remove avatar
        this.fileService.removeGroupProfilePicture(groupModel);

        // Remove chat settings (e.g. wallpaper or custom ringtones)
        String uniqueIdString = getUniqueIdString(groupModel);
        this.wallpaperService.removeWallpaper(uniqueIdString);
        this.ringtoneService.removeCustomRingtone(uniqueIdString);
        this.conversationCategoryService.persistDefaultChat(uniqueIdString);
        ShortcutUtil.deleteShareTargetShortcut(uniqueIdString);
        ShortcutUtil.deletePinnedShortcut(uniqueIdString);
        this.conversationTagService.removeAll(ConversationUtil.getGroupConversationUid(groupModel.getDatabaseId()), triggerSource);
        serviceManager.getNotificationService().cancel(messageReceiver);

        // Remove conversation
        GroupModelOld oldGroupModel = getById((int) groupModel.getDatabaseId());
        if (oldGroupModel != null) {
            conversationService.removeFromCache(oldGroupModel);
        } else {
            logger.error("Old group model is null. Cannot be removed from conversation service.");
        }

        this.groupModelCache.remove((int) groupModel.getDatabaseId());

        this.resetIdentityCache((int) groupModel.getDatabaseId());
    }

    /**
     * Note: This action is not reflected! This should therefore only be used for testing or in
     * situations where the change does not need to be reflected.
     */
    private void remove(@NonNull final GroupModelOld groupModel) {
        // Obtain some services through service manager
        //
        // Note: We cannot put these services in the constructor due to circular dependencies.
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            logger.error("Missing serviceManager, cannot remove group");
            return;
        }
        final ConversationService conversationService;
        final BallotService ballotService;
        try {
            conversationService = serviceManager.getConversationService();
            ballotService = serviceManager.getBallotService();
        } catch (ThreemaException e) {
            logger.error("Could not obtain services when removing group", e);
            return;
        }

        // Delete polls
        ballotService.remove(createReceiver(groupModel));

        // Remove all messages
        final GroupMessageModelFactory groupMessageModelFactory = databaseService.getGroupMessageModelFactory();
        try (ChunkedSequence<GroupMessageModel> messageModels = groupMessageModelFactory.getByGroupId(groupModel.getId())) {
            for (GroupMessageModel messageModel : messageModels) {
                this.fileService.removeMessageFiles(messageModel, true);
            }
        }
        groupMessageModelFactory.deleteByGroupId(groupModel.getId());

        // Remove avatar
        fileService.removeGroupProfilePicture(groupModel.getGroupIdentity(), groupModel.getId());

        // Remove chat settings (e.g. wallpaper or custom ringtones)
        String uniqueIdString = getUniqueIdString(groupModel);
        this.wallpaperService.removeWallpaper(uniqueIdString);
        this.ringtoneService.removeCustomRingtone(uniqueIdString);
        this.conversationCategoryService.persistDefaultChat(uniqueIdString);
        ShortcutUtil.deleteShareTargetShortcut(uniqueIdString);
        ShortcutUtil.deletePinnedShortcut(uniqueIdString);
        this.conversationTagService.removeAll(ConversationUtil.getGroupConversationUid(groupModel.getId()), TriggerSource.LOCAL);

        // Remove conversation
        conversationService.removeFromCache(groupModel);

        // Delete group members
        this.databaseService.getGroupMemberModelFactory().deleteByGroupId(groupModel.getId());

        // Delete group fully from database
        this.databaseService.getGroupModelFactory().delete(groupModel);

        synchronized (this.groupModelCache) {
            this.groupModelCache.remove(groupModel.getId());
        }

        this.resetIdentityCache(groupModel.getId());
    }

    @Override
    public void removeAll() {
        for (GroupModelOld g : this.getAll()) {
            this.remove(g);
        }
        //remove last request sync table

        this.databaseService.getOutgoingGroupSyncRequestLogModelFactory().deleteAll();
        this.databaseService.getIncomingGroupSyncRequestLogModelFactory().deleteAll();
    }


    @Override
    @Nullable
    public GroupModelOld getByGroupMessage(@NonNull final AbstractGroupMessage message) {
        return getByApiGroupIdAndCreator(message.getApiGroupId(), message.getGroupCreator());
    }

    @Override
    public void scheduleSyncRequest(@NonNull String groupCreator, @NonNull GroupId groupId) {
        getTaskManager().schedule(new OutgoingGroupSyncRequestTask(
                groupId, groupCreator, null
            )
        );
    }

    @Override
    @Nullable
    public GroupModelOld getByApiGroupIdAndCreator(@NonNull GroupId apiGroupId, @NonNull String creatorIdentity) {
        synchronized (this.groupModelCache) {
            @Nullable GroupModelOld model = select(
                this.groupModelCache,
                type -> apiGroupId.toString().equals(type.getApiGroupId().toString()) && creatorIdentity.equals(type.getCreatorIdentity())
            );
            if (model == null) {
                model = this.databaseService.getGroupModelFactory().getByApiGroupIdAndCreator(
                    apiGroupId.toString(),
                    creatorIdentity
                );

                if (model != null) {
                    return this.cache(model);
                }
            } else {
                return model;
            }

            return null;
        }
    }

    private static <T> T select(SparseArrayCompat<T> target, Predicate<T> predicate) {
        for (int n = 0; n < target.size(); n++) {
            int key = target.keyAt(n);
            T object = target.get(key);
            if (object != null && predicate.test(object)) {
                return object;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public GroupModelOld getByGroupIdentity(@NonNull GroupIdentity groupIdentity) {
        return getByApiGroupIdAndCreator(
            new GroupId(groupIdentity.getGroupId()),
            groupIdentity.getCreatorIdentity()
        );
    }

    @NonNull
    @Override
    public Intent getGroupDetailIntent(long groupDatabaseId, @NonNull Activity activity) {
        Intent intent = new Intent(activity, GroupDetailActivity.class);
        intent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, groupDatabaseId);
        return intent;
    }

    @NonNull
    @Override
    public Intent getGroupDetailIntent(@NonNull GroupModelOld groupModel, @NonNull Activity activity) {
        Intent intent = new Intent(activity, GroupDetailActivity.class);
        intent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, (long) groupModel.getId());
        return intent;
    }

    @NonNull
    @Override
    public Intent getGroupDetailIntent(@NonNull GroupModel groupModel, @NonNull Activity activity) {
        Intent intent = new Intent(activity, GroupDetailActivity.class);
        intent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, groupModel.getDatabaseId());
        return intent;
    }

    @Override
    public @Nullable GroupModelOld getById(int groupId) {
        synchronized (this.groupModelCache) {
            GroupModelOld existingGroupModel = groupModelCache.get(groupId);
            if (existingGroupModel != null) {
                return existingGroupModel;
            }
            return this.cache(this.databaseService.getGroupModelFactory().getById(groupId));
        }
    }

    @Override
    @NonNull
    public List<GroupModelOld> getByIds(@NonNull List<Integer> groupIds) {
        final @NonNull List<GroupModelOld> results = new ArrayList<>();
        if (groupIds.isEmpty()) {
            return results;
        }

        synchronized (groupModelCache) {
            final @NonNull List<Integer> groupIdsMissingInCache = new ArrayList<>();

            // try to find all in cache
            for (Integer groupId : groupIds) {
                final @Nullable GroupModelOld groupModel = groupModelCache.get(groupId);
                if (groupModel != null) {
                    results.add(groupModel);
                } else {
                    groupIdsMissingInCache.add(groupId);
                }
            }

            // happy case: all were found in cache
            if (groupIdsMissingInCache.isEmpty()) {
                return results;
            }

            // try to read all missing models from database
            final @NonNull List<GroupModelOld> groupModelsFromDb = databaseService.getGroupModelFactory().getByIds(groupIdsMissingInCache);
            results.addAll(groupModelsFromDb);
            for (GroupModelOld groupModel : groupModelsFromDb) {
                groupModelCache.put(groupModel.getId(), groupModel);
            }
        }
        return results;
    }

    @Override
    public void runRejectedMessagesRefreshSteps(@NonNull GroupModel groupModel) {
        RejectedGroupMessageFactory rejectedGroupMessageFactory = databaseService.getRejectedGroupMessageFactory();
        GroupMessageModelFactory groupMessageModelFactory = databaseService.getGroupMessageModelFactory();
        List<GroupMessageModel> updatedMessages;

        GroupModelData data = groupModel.liveData().getValue();

        if (data == null || !data.isMember()) {
            // If the group is marked as left, remove all reject marks and receivers requiring a re-send
            rejectedGroupMessageFactory.removeAllMessageRejectsInGroup(groupModel);

            updatedMessages = groupMessageModelFactory.getAllRejectedMessagesInGroup(groupModel);
        } else {
            // If the group is not marked left, we remove all ex-members from the reject table that
            // have rejected a message in this group. Message models that do not have any rejecting
            // member anymore after this cleanup will be updated.
            updatedMessages = new ArrayList<>();
            Set<String> members = groupModel.getRecipients();

            List<GroupMessageModel> allRejectedMessages = groupMessageModelFactory.getAllRejectedMessagesInGroup(groupModel);
            for (GroupMessageModel rejectedMessage : allRejectedMessages) {
                // Try to get the message id of the group message
                MessageId rejectedMessageId = rejectedMessage.getMessageId();
                if (rejectedMessageId == null) {
                    logger.error("message id from rejected message was unexpectedly null");
                    continue;
                }

                // Initialize the set with all identities that have rejected the message
                Set<String> rejectedNonMembers = rejectedGroupMessageFactory.getMessageRejects(
                    rejectedMessageId,
                    groupModel
                );

                // Remove all group members from the rejected receivers so that only rejects from
                // left group members are contained in this set
                boolean messageHasRejectingGroupMember = rejectedNonMembers.removeAll(members);

                // Remove ex-members from the reject list as they should not receive re-sends
                for (String receiver : rejectedNonMembers) {
                    rejectedGroupMessageFactory.removeMessageRejectByGroupAndIdentity(groupModel, receiver);
                }

                // If there are no rejected identities left, update the message state
                if (!messageHasRejectingGroupMember) {
                    updatedMessages.add(rejectedMessage);
                }
            }
        }

        // Update the state for each of the messages
        for (GroupMessageModel message : updatedMessages) {
            message.setState(MessageState.SENT);
            groupMessageModelFactory.update(message);
        }

        ListenerManager.messageListeners.handle(
            listener -> listener.onModified(new ArrayList<>(updatedMessages))
        );
    }

    @Override
    public void resetCache(int groupModelId) {
        synchronized (groupModelCache) {
            groupModelCache.remove(groupModelId);
        }
        synchronized (groupIdentityCache) {
            groupIdentityCache.remove(groupModelId);
        }
        synchronized (groupMemberColorCache) {
            groupMemberColorCache.remove(groupModelId);
        }
    }

    @Override
    public void removeFromCache(@NonNull GroupIdentity groupIdentity) {
        GroupModelOld groupModel = getByGroupIdentity(groupIdentity);
        if (groupModel != null) {
            resetCache(groupModel.getId());
        }
    }

    /**
     * remove the cache entry of the identities
     */
    private void resetIdentityCache(int groupModelId) {
        synchronized (this.groupIdentityCache) {
            this.groupIdentityCache.remove(groupModelId);
        }

        synchronized (this.groupMemberColorCache) {
            this.groupMemberColorCache.remove(groupModelId);
        }
    }

    @NonNull
    @Override
    public Set<String> getMembersWithoutUser(@NonNull GroupModelOld groupModel) {
        Set<String> otherMembers = new HashSet<>(Arrays.asList(getGroupMemberIdentities(groupModel)));
        otherMembers.remove(userService.getIdentity());
        return otherMembers;
    }

    @NonNull
    @Override
    public String[] getGroupMemberIdentities(@NonNull GroupModelOld oldGroupModel) {
        GroupModel groupModel = groupModelRepository.getByGroupIdentity(oldGroupModel.getGroupIdentity());
        if (groupModel == null) {
            return new String[]{};
        }
        GroupModelData groupModelData = groupModel.getData();
        if (groupModelData == null) {
            return new String[]{};
        }
        String myIdentity = userService.getIdentity();
        if (myIdentity == null) {
            logger.error("Cannot get group member identities if no identity is available");
            return new String[]{};
        }

        Set<String> identities = groupModelData.getAllMembers(myIdentity);
        return identities.toArray(new String[]{});
    }

    @Override
    public boolean isGroupMember(@NonNull GroupModelOld groupModel) {
        return groupModel.getUserState() == UserState.MEMBER;
    }

    @Override
    public boolean isGroupMember(@NonNull GroupModelOld groupModel, @Nullable String identity) {
        if (!TestUtil.isEmptyOrNull(identity)) {
            if (identity.equals(userService.getIdentity())) {
                return isGroupMember(groupModel);
            }

            for (String existingIdentity : this.getGroupMemberIdentities(groupModel)) {
                if (TestUtil.compare(existingIdentity, identity)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get the group member models of the given group. Note that the user is not part of this list.
     */
    @NonNull
    private List<GroupMemberModel> getGroupMemberModels(@NonNull GroupModelOld groupModel) {
        List<GroupMemberModel> groupMemberModels = databaseService
            .getGroupMemberModelFactory()
            .getByGroupId(groupModel.getId());

        // Remove own identity. Note that the user's identity should never be stored as member.
        // This is just a check to ensure correct behavior even if the member list is wrong.
        String myIdentity = userService.getIdentity();
        if (groupMemberModels.removeIf(
            groupMemberModel -> myIdentity.equals(groupMemberModel.getIdentity())
        )) {
            logger.warn("User is contained as member in group");
        }

        return groupMemberModels;
    }

    @Override
    @NonNull
    public Collection<ContactModel> getMembers(@NonNull GroupModelOld groupModel) {
        return this.contactService.getByIdentities(
            Arrays.asList(getGroupMemberIdentities(groupModel))
        );
    }

    @Override
    @NonNull
    public Collection<ContactModel> getMembers(@NonNull GroupModelData groupModelData) {
        LinkedList<String> members = new LinkedList<>(groupModelData.otherMembers);
        String myIdentity = userService.getIdentity();
        if (groupModelData.isMember()) {
            members.addFirst(myIdentity);
        }
        String creatorIdentity = groupModelData.groupIdentity.getCreatorIdentity();
        if (!creatorIdentity.equals(myIdentity)) {
            members.add(creatorIdentity);
        }
        return this.contactService.getByIdentities(new ArrayList<>(members));
    }

    @Override
    @NonNull
    public String getMembersString(@Nullable GroupModelOld groupModel) {
        if (groupModel == null) {
            return "";
        }
        // Add display names or nickname of members
        Collection<ContactModel> contacts = this.getMembers(groupModel);
        List<String> names = new ArrayList<>(contacts.size());
        final @NonNull ContactNameFormat contactNameFormat = preferenceService.getContactNameFormat();
        for (ContactModel contactModel : contacts) {
            names.add(NameUtil.getContactDisplayNameOrNickname(contactModel, true, contactNameFormat));
        }
        return TextUtils.join(", ", names);
    }

    @NonNull
    @Override
    public String getMembersString(@Nullable GroupModel groupModel) {
        if (groupModel == null) {
            return "";
        }

        GroupModelData groupModelData = groupModel.getData();
        if (groupModelData == null) {
            logger.warn("Cannot get member string: Group model already deleted");
            return "";
        }

        // Add display names or nickname of members
        Collection<ContactModel> contacts = this.getMembers(groupModelData);
        List<String> names = new ArrayList<>(contacts.size());
        final @NonNull ContactNameFormat contactNameFormat = preferenceService.getContactNameFormat();
        for (ContactModel contactModel : contacts) {
            names.add(NameUtil.getContactDisplayNameOrNickname(contactModel, true, contactNameFormat));
        }
        return TextUtils.join(", ", names);
    }

    @Override
    @Nullable
    public GroupMessageReceiver createReceiver(@NonNull GroupIdentity groupIdentity) {
        final @Nullable GroupModelOld groupModel = getByGroupIdentity(groupIdentity);
        if (groupModel != null) {
            return createReceiver(groupModel);
        } else {
            return null;
        }
    }

    @Override
    @NonNull
    public GroupMessageReceiver createReceiver(@NonNull GroupModelOld groupModel) {
        return new GroupMessageReceiver(
            groupModel,
            this,
            this.databaseService,
            this.userService,
            this.contactModelRepository,
            this.groupModelRepository,
            this.serviceManager
        );
    }

    @Nullable
    @Override
    public GroupMessageReceiver createReceiver(@NonNull GroupModel groupModel) {
        GroupIdentity groupIdentity = groupModel.getGroupIdentity();
        GroupModelOld legacyGroupModel = getByApiGroupIdAndCreator(
            new GroupId(groupIdentity.getGroupId()),
            groupIdentity.getCreatorIdentity()
        );
        if (legacyGroupModel == null) {
            logger.error("Could not load legacy group model");
            return null;
        }
        return createReceiver(legacyGroupModel);
    }

    @AnyThread
    @Nullable
    @Override
    public Bitmap getAvatar(@Nullable GroupModelOld groupModel, @NonNull AvatarOptions options) {
        if (groupModel == null) {
            return null;
        }

        // If the custom avatar is requested without default fallback and there is no avatar for
        // this group, we can return null directly. Important: This is necessary to prevent glide
        // from logging an unnecessary error stack trace.
        if (options.defaultAvatarPolicy == AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR
            && !fileService.hasGroupProfilePicture(groupModel.getId())) {
            return null;
        }

        return avatarCacheService.getGroupAvatar(groupModel.getGroupIdentity(), options);
    }

    @Nullable
    @Override
    public Bitmap getAvatar(@Nullable GroupModel groupModel, @NonNull AvatarOptions options) {
        if (groupModel == null) {
            return null;
        }
        GroupModelOld oldGroupModel = getByGroupIdentity(groupModel.getGroupIdentity());
        if (oldGroupModel == null) {
            logger.error("Could not get group avatar because the old group model could not be found");
            return null;
        }
        return getAvatar(oldGroupModel, options);
    }

    @Override
    public int getGroupProfilePictureColor(@Nullable GroupModel groupModel) {
        if (groupModel == null) {
            return IdColor.invalid().getThemedColor(this.context);
        }

        GroupModelData groupModelData = groupModel.getData();
        if (groupModelData == null) {
            return IdColor.invalid().getThemedColor(this.context);
        }

        return groupModelData.getIdColor().getThemedColor(context);
    }

    @Override
    public void loadAvatarIntoImageView(
        @NonNull GroupModel groupModel,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    ) {
        GroupModelOld oldGroupModel = getByGroupIdentity(groupModel.getGroupIdentity());
        if (oldGroupModel == null) {
            logger.error("Could load group avatar because the old group model could not be found");
            return;
        }
        loadAvatarIntoImage(oldGroupModel, imageView, options, requestManager);
    }

    @AnyThread
    @Override
    public void loadAvatarIntoImage(
        @NonNull GroupModelOld groupModel,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    ) {
        avatarCacheService.loadGroupAvatarIntoImage(groupModel.getGroupIdentity(), imageView, options, requestManager);
    }

    @Override
    public @ColorInt int getAvatarColor(@Nullable GroupModelOld group) {
        return group != null
            ? group.getIdColor().getThemedColor(context)
            : IdColor.invalid().getThemedColor(this.context);
    }

    @Override
    public boolean isGroupCreator(GroupModelOld groupModel) {
        return groupModel != null
            && this.userService.getIdentity() != null
            && this.userService.isMe(groupModel.getCreatorIdentity());
    }

    @Override
    public int countMembers(@NonNull GroupModelOld oldGroupModel) {
        GroupModel groupModel = groupModelRepository.getByGroupIdentity(oldGroupModel.getGroupIdentity());
        if (groupModel == null) {
            return 0;
        }
        GroupModelData groupModelData = groupModel.getData();
        if (groupModelData == null) {
            return 0;
        }

        String myIdentity = userService.getIdentity();
        if (myIdentity == null) {
            logger.error("Cannot determine group member count if no identity exists.");
            return 0;
        }

        return groupModelData.getAllMembers(myIdentity).size();
    }

    @Override
    public boolean isNotesGroup(@NonNull GroupModelOld groupModel) {
        return
            isGroupCreator(groupModel) &&
                countMembers(groupModel) == 1;
    }

    @Override
    public int countMembersWithoutUser(@NonNull GroupModelOld oldGroupModel) {
        GroupModel groupModel = groupModelRepository.getByGroupIdentity(oldGroupModel.getGroupIdentity());
        if (groupModel == null) {
            return 0;
        }

        return groupModel.getRecipients().size();
    }

    @Override
    @NonNull
    public Map<String, IdColor> getGroupMemberIDColors(@NonNull GroupModel model) {
        long groupDatabaseId = model.getDatabaseId();
        Map<String, IdColor> colors = this.groupMemberColorCache.get((int) groupDatabaseId);
        if (colors == null || colors.isEmpty()) {
            colors = this.databaseService.getGroupMemberModelFactory().getIDColors(groupDatabaseId);
            this.groupMemberColorCache.put((int) groupDatabaseId, colors);
        }

        return colors;
    }

    @Override
    @NonNull
    public List<GroupModelOld> getGroupsByIdentity(@Nullable String identity) {
        final @NonNull List<GroupModelOld> results = new ArrayList<>();
        if (identity == null || identity.isEmpty() || groupModelCache == null) {
            return results;
        }

        identity = identity.toUpperCase();

        final @NonNull List<Integer> groupIds = this.databaseService.getGroupMemberModelFactory().getGroupIdsByIdentity(identity);
        final @NonNull List<Integer> groupIdsMissingInCache = new ArrayList<>();

        synchronized (this.groupModelCache) {
            for (int groupId : groupIds) {
                final @Nullable GroupModelOld groupModelCached = this.groupModelCache.get(groupId);
                if (groupModelCached != null) {
                    results.add(groupModelCached);
                } else {
                    groupIdsMissingInCache.add(groupId);
                }
            }
        }

        if (!groupIdsMissingInCache.isEmpty()) {
            final @NonNull List<GroupModelOld> groups = this.databaseService.getGroupModelFactory().getByIds(groupIdsMissingInCache);
            for (GroupModelOld groupModel : groups) {
                final @Nullable GroupModelOld groupModelCached = cache(groupModel);
                if (groupModelCached != null) {
                    results.add(groupModelCached);
                }
            }
        }

        return results;
    }

    @Override
    public GroupAccessModel getAccess(@Nullable GroupModelOld groupModel, boolean allowEmpty) {
        GroupAccessModel groupAccessModel = new GroupAccessModel();
        if (groupModel != null) {
            // Don't allow to send and receive messages in left groups
            if (!isGroupMember(groupModel)) {
                groupAccessModel.setCanReceiveMessageAccess(new Access(
                    false,
                    R.string.you_are_not_a_member_of_this_group
                ));

                groupAccessModel.setCanSendMessageAccess(new Access(
                    false,
                    R.string.you_are_not_a_member_of_this_group
                ));
            } else if (countMembersWithoutUser(groupModel) <= 0 && !allowEmpty) {
                // Don't allow sending in empty groups (except allowEmpty is true)
                groupAccessModel.setCanReceiveMessageAccess(new Access(
                    false,
                    R.string.can_not_send_no_group_members
                ));

                groupAccessModel.setCanSendMessageAccess(new Access(
                    false,
                    R.string.can_not_send_no_group_members
                ));
            }
        }

        return groupAccessModel;
    }

    @Override
    public void setIsArchived(
        @NonNull String groupCreatorIdentity,
        @NonNull GroupId groupId,
        boolean isArchived,
        @NonNull TriggerSource triggerSource
    ) {
        GroupModel groupModel = groupModelRepository
            .getByCreatorIdentityAndId(groupCreatorIdentity, groupId);
        if (groupModel == null) {
            logger.warn("Cannot set isArchived={} for group because its model is null", isArchived);
            return;
        }

        try {
            switch (triggerSource) {
                case LOCAL:
                case REMOTE:
                    groupModel.setIsArchivedFromLocalOrRemote(isArchived);
                    break;
                case SYNC:
                    groupModel.setIsArchivedFromSync(isArchived);
                    break;
            }
        } catch (ModelDeletedException e) {
            logger.warn("Could not set isArchived={} because model has been deleted", isArchived, e);
        }
    }

    @Override
    public void bumpLastUpdate(@NonNull GroupModelOld groupModel) {
        GroupIdentity groupIdentity = new GroupIdentity(
            groupModel.getCreatorIdentity(),
            groupModel.getApiGroupId().toLong()
        );
        this.databaseService.getGroupModelFactory().setLastUpdate(groupIdentity, new Date());
        ListenerManager.groupListeners.handle(listener -> listener.onUpdate(groupIdentity));
    }

    @Override
    public boolean isFull(final GroupModelOld groupModel) {
        return this.countMembers(groupModel) >= BuildConfig.MAX_GROUP_SIZE;
    }

    @Override
    public void removeGroupMessageState(@NonNull GroupMessageModel messageModel, @NonNull String identityToRemove) {
        GroupModelOld groupModel = getById(messageModel.getGroupId());
        if (groupModel != null) {
            if (isGroupMember(groupModel, identityToRemove)) {
                Map<String, Object> groupMessageStates = messageModel.getGroupMessageStates();
                if (groupMessageStates != null) {
                    groupMessageStates.remove(identityToRemove);
                    messageModel.setGroupMessageStates(groupMessageStates);
                }
            } else {
                logger.debug("Received state change for non-member {}", identityToRemove);
            }
        } else {
            logger.debug("Received state change for non existent group {}", messageModel.getGroupId());
        }
    }

    @NonNull
    private TaskManager getTaskManager() {
        if (taskManager == null) {
            taskManager = serviceManager.getTaskManager();
        }

        return taskManager;
    }

    @Override
    public GroupFeatureSupport getFeatureSupport(@NonNull GroupModelData groupModelData, @ThreemaFeature.Feature long feature) {
        Collection<ContactModel> members = getMembers(groupModelData);

        // Remove the group creator from the list if the group creator doesn't receive any messages anyways (gateway)
        if (!GroupUtil.shouldSendMessagesToCreator(groupModelData.groupIdentity.getCreatorIdentity(), groupModelData.name)) {
            members.removeIf(member -> groupModelData.groupIdentity.getCreatorIdentity().equals(member.getIdentity()));
        }

        // Remove the user from the list
        members.removeIf(member -> userService.isMe(member.getIdentity()));

        return new GroupFeatureSupport(
            feature,
            new ArrayList<>(members)
        );
    }

    @Override
    public void resetAllNotificationTriggerPolicyOverrideFromLocal() {
        groupModelRepository.getAll().stream().forEach(
            dbGroup -> dbGroup.setNotificationTriggerPolicyOverrideFromLocal(null)
        );
    }
}

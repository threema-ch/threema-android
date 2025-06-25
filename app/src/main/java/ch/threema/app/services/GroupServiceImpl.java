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
import ch.threema.app.collections.Functional;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.tasks.OutgoingGroupSyncRequestTask;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConversationUtil;
import ch.threema.app.utils.GroupFeatureSupport;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupIdentity;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.models.ModelDeletedException;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.factories.GroupMessageModelFactory;
import ch.threema.storage.factories.IncomingGroupJoinRequestModelFactory;
import ch.threema.storage.factories.RejectedGroupMessageFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.access.Access;
import ch.threema.storage.models.access.GroupAccessModel;
import ch.threema.storage.models.group.GroupInviteModel;

import static ch.threema.app.utils.GroupUtil.getUniqueIdString;

public class GroupServiceImpl implements GroupService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupServiceImpl");

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

    // Model repositories
    @NonNull
    private final ContactModelRepository contactModelRepository;
    @NonNull
    private final GroupModelRepository groupModelRepository;

    // TODO(ANDR-3755): Consolidate this cache
    private final SparseArrayCompat<Map<String, Integer>> groupMemberColorCache;
    private final SparseArrayCompat<GroupModel> groupModelCache;
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
    public List<GroupModel> getAll() {
        return this.getAll(null);
    }

    @Override
    @NonNull
    public List<GroupModel> getAll(GroupFilter filter) {
        List<GroupModel> res = new ArrayList<>(this.databaseService.getGroupModelFactory().filter(filter));

        if (filter != null && !filter.includeLeftGroups()) {
            Iterator<GroupModel> iterator = res.iterator();
            while (iterator.hasNext()) {
                GroupModel groupModel = iterator.next();
                if (!isGroupMember(groupModel)) {
                    iterator.remove();
                }
            }
        }

        for (GroupModel m : res) {
            this.cache(m);
        }

        return res;
    }

    private GroupModel cache(GroupModel groupModel) {
        if (groupModel == null) {
            return null;
        }

        synchronized (this.groupModelCache) {
            GroupModel existingGroupModel = groupModelCache.get(groupModel.getId());
            if (existingGroupModel != null) {
                return existingGroupModel;
            }

            groupModelCache.put(groupModel.getId(), groupModel);
            return groupModel;
        }
    }

    @Override
    public void removeGroupBelongings(
        @NonNull ch.threema.data.models.GroupModel groupModel,
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
        for (GroupMessageModel messageModel : this.databaseService.getGroupMessageModelFactory().getByGroupIdUnsorted(groupModel.getDatabaseId())) {
            this.fileService.removeMessageFiles(messageModel, true);
        }

        // Remove avatar
        this.fileService.removeGroupAvatar(groupModel);

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
        GroupModel oldGroupModel = getById((int) groupModel.getDatabaseId());
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
    private void remove(@NonNull final GroupModel groupModel) {
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

        // Remove all group invite links and requests
        final GroupInviteModelFactory groupInviteModelFactory = this.databaseService.getGroupInviteModelFactory();
        final IncomingGroupJoinRequestModelFactory incomingGroupJoinRequestModelFactory = this.databaseService.getIncomingGroupJoinRequestModelFactory();
        for (GroupInviteModel groupInviteModel : groupInviteModelFactory.getByGroupApiId(groupModel.getApiGroupId())) {
            incomingGroupJoinRequestModelFactory.deleteAllForGroupInvite(groupInviteModel.getId());
            groupInviteModelFactory.delete(groupInviteModel);
        }

        // Remove all messages
        for (GroupMessageModel messageModel : this.databaseService.getGroupMessageModelFactory().getByGroupIdUnsorted(groupModel.getId())) {
            this.fileService.removeMessageFiles(messageModel, true);
        }
        this.databaseService.getGroupMessageModelFactory().deleteByGroupId(groupModel.getId());

        // Remove avatar
        this.fileService.removeGroupAvatar(groupModel);
        this.avatarCacheService.reset(groupModel);

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
        for (GroupModel g : this.getAll()) {
            this.remove(g);
        }
        //remove last request sync table

        this.databaseService.getOutgoingGroupSyncRequestLogModelFactory().deleteAll();
        this.databaseService.getIncomingGroupSyncRequestLogModelFactory().deleteAll();
    }


    @Override
    @Nullable
    public GroupModel getByGroupMessage(@NonNull final AbstractGroupMessage message) {
        return getByApiGroupIdAndCreator(message.getApiGroupId(), message.getGroupCreator());
    }

    @Override
    public void scheduleSyncRequest(@NonNull String groupCreator, @NonNull GroupId groupId) {
        getTaskManager().schedule(new OutgoingGroupSyncRequestTask(
                groupId, groupCreator, null, serviceManager
            )
        );
    }

    @Override
    @Nullable
    public GroupModel getByApiGroupIdAndCreator(@NonNull GroupId apiGroupId, @NonNull String creatorIdentity) {
        synchronized (this.groupModelCache) {
            @Nullable GroupModel model = Functional.select(
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

    @Nullable
    @Override
    public GroupModel getByGroupIdentity(@NonNull GroupIdentity groupIdentity) {
        return getByApiGroupIdAndCreator(
            new GroupId(groupIdentity.getGroupId()),
            groupIdentity.getCreatorIdentity()
        );
    }

    @NonNull
    @Override
    public Intent getGroupDetailIntent(@NonNull GroupModel groupModel, @NonNull Activity activity) {
        Intent intent = new Intent(activity, GroupDetailActivity.class);
        intent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, groupModel.getId());
        return intent;
    }

    @NonNull
    @Override
    public Intent getGroupDetailIntent(@NonNull ch.threema.data.models.GroupModel groupModel, @NonNull Activity activity) {
        Intent intent = new Intent(activity, GroupDetailActivity.class);
        intent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, groupModel.getDatabaseId());
        return intent;
    }

    @Override
    public @Nullable GroupModel getById(int groupId) {
        synchronized (this.groupModelCache) {
            GroupModel existingGroupModel = groupModelCache.get(groupId);
            if (existingGroupModel != null) {
                return existingGroupModel;
            }
            return this.cache(this.databaseService.getGroupModelFactory().getById(groupId));
        }
    }

    @Override
    public void runRejectedMessagesRefreshSteps(@NonNull ch.threema.data.models.GroupModel groupModel) {
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
            Set<String> members = data.otherMembers;

            List<GroupMessageModel> allRejectedMessages = groupMessageModelFactory.getAllRejectedMessagesInGroup(groupModel);
            for (GroupMessageModel rejectedMessage : allRejectedMessages) {
                // Try to get the message id of the group message
                MessageId rejectedMessageId;
                try {
                    rejectedMessageId = MessageId.fromString(rejectedMessage.getApiMessageId());
                } catch (ThreemaException e) {
                    logger.error("Could not get message id from rejected message");
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
        GroupModel groupModel = getByGroupIdentity(groupIdentity);
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
    public Set<String> getMembersWithoutUser(@NonNull GroupModel groupModel) {
        Set<String> otherMembers = new HashSet<>(Arrays.asList(getGroupMemberIdentities(groupModel)));
        otherMembers.remove(userService.getIdentity());
        return otherMembers;
    }

    @NonNull
    @Override
    public String[] getGroupMemberIdentities(@NonNull GroupModel groupModel) {
        synchronized (this.groupIdentityCache) {
            String[] existingIdentities = this.groupIdentityCache.get(groupModel.getId());
            if (existingIdentities != null) {
                return existingIdentities;
            }

            List<GroupMemberModel> memberModels = this.getGroupMemberModels(groupModel);
            boolean isGroupMember = isGroupMember(groupModel);

            String[] res;
            int arrayIndexOffset;
            if (isGroupMember) {
                res = new String[memberModels.size() + 1];
                arrayIndexOffset = 1;
                // Include the user in the array if it is a member. Note that this is required as the
                // user is never stored as a group member.
                res[0] = userService.getIdentity();
            } else {
                res = new String[memberModels.size()];
                arrayIndexOffset = 0;
            }

            for (int i = 0; i < memberModels.size(); i++) {
                res[i + arrayIndexOffset] = memberModels.get(i).getIdentity();
            }

            this.groupIdentityCache.put(groupModel.getId(), res);
            return res;
        }
    }

    @Override
    public boolean isGroupMember(@NonNull GroupModel groupModel) {
        return groupModel.getUserState() == GroupModel.UserState.MEMBER;
    }

    @Override
    public boolean isGroupMember(@NonNull GroupModel groupModel, @Nullable String identity) {
        if (!TestUtil.isEmptyOrNull(identity)) {
            if (userService.getIdentity().equals(identity)) {
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

    @Override
    public boolean isOrphanedGroup(@NonNull GroupModel groupModel) {
        return !isGroupMember(groupModel, groupModel.getCreatorIdentity()) && !isGroupCreator(groupModel);
    }

    /**
     * Get the group member models of the given group. Note that the user is not part of this list.
     */
    @NonNull
    private List<GroupMemberModel> getGroupMemberModels(@NonNull GroupModel groupModel) {
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
    public Collection<ContactModel> getMembers(@NonNull GroupModel groupModel) {
        return this.contactService.getByIdentities(this.getGroupMemberIdentities(groupModel));
    }

    @Override
    @NonNull
    public Collection<ContactModel> getMembers(@NonNull GroupModelData groupModelData) {
        LinkedList<String> members = new LinkedList<>(groupModelData.otherMembers);
        if (groupModelData.isMember()) {
            members.addFirst(userService.getIdentity());
        }
        return this.contactService.getByIdentities(new ArrayList<>(members));
    }

    @Override
    @NonNull
    public String getMembersString(@Nullable GroupModel groupModel) {
        if (groupModel == null) {
            return "";
        }
        // Add display names or nickname of members
        Collection<ContactModel> contacts = this.getMembers(groupModel);
        List<String> names = new ArrayList<>(contacts.size());
        for (ContactModel c : contacts) {
            names.add(NameUtil.getDisplayNameOrNickname(c, true));
        }
        return TextUtils.join(", ", names);
    }

    @NonNull
    @Override
    public String getMembersString(@Nullable ch.threema.data.models.GroupModel groupModel) {
        if (groupModel == null) {
            return "";
        }

        GroupModelData groupModelData = groupModel.getData().getValue();
        if (groupModelData == null) {
            logger.warn("Cannot get member string: Group model already deleted");
            return "";
        }

        // Add display names or nickname of members
        Collection<ContactModel> contacts = this.getMembers(groupModelData);
        List<String> names = new ArrayList<>(contacts.size());
        for (ContactModel contact : contacts) {
            names.add(NameUtil.getDisplayNameOrNickname(contact, true));
        }
        return TextUtils.join(", ", names);
    }

    @Override
    @NonNull
    public GroupMessageReceiver createReceiver(@NonNull GroupModel groupModel) {
        return new GroupMessageReceiver(
            groupModel,
            this,
            this.databaseService,
            this.contactService,
            this.contactModelRepository,
            this.groupModelRepository,
            this.serviceManager
        );
    }

    @Nullable
    @Override
    public GroupMessageReceiver createReceiver(@NonNull ch.threema.data.models.GroupModel groupModel) {
        GroupIdentity groupIdentity = groupModel.getGroupIdentity();
        GroupModel legacyGroupModel = getByApiGroupIdAndCreator(
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
    public Bitmap getAvatar(@Nullable GroupModel groupModel, @NonNull AvatarOptions options) {
        if (groupModel == null) {
            return null;
        }

        // If the custom avatar is requested without default fallback and there is no avatar for
        // this group, we can return null directly. Important: This is necessary to prevent glide
        // from logging an unnecessary error stack trace.
        if (options.defaultAvatarPolicy == AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR
            && !fileService.hasGroupAvatarFile(groupModel)) {
            return null;
        }

        return avatarCacheService.getGroupAvatar(groupModel, options);
    }

    @Nullable
    @Override
    public Bitmap getAvatar(@Nullable ch.threema.data.models.GroupModel groupModel, @NonNull AvatarOptions options) {
        if (groupModel == null) {
            return null;
        }
        GroupModel oldGroupModel = getByGroupIdentity(groupModel.getGroupIdentity());
        if (oldGroupModel == null) {
            logger.error("Could not get group avatar because the old group model could not be found");
            return null;
        }
        return getAvatar(oldGroupModel, options);
    }

    @Override
    public void loadAvatarIntoImageView(
        @NonNull ch.threema.data.models.GroupModel groupModel,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    ) {
        GroupModel oldGroupModel = getByGroupIdentity(groupModel.getGroupIdentity());
        if (oldGroupModel == null) {
            logger.error("Could load group avatar because the old group model could not be found");
            return;
        }
        loadAvatarIntoImage(oldGroupModel, imageView, options, requestManager);
    }

    @AnyThread
    @Override
    public void loadAvatarIntoImage(
        @NonNull GroupModel groupModel,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    ) {
        avatarCacheService.loadGroupAvatarIntoImage(groupModel, imageView, options, requestManager);
    }

    @Override
    public @ColorInt int getAvatarColor(@Nullable GroupModel group) {
        if (group != null) {
            return group.getThemedColor(context);
        }
        return ColorUtil.getInstance().getCurrentThemeGray(this.context);
    }

    @Override
    public boolean isGroupCreator(GroupModel groupModel) {
        return groupModel != null
            && this.userService.getIdentity() != null
            && this.userService.isMe(groupModel.getCreatorIdentity());
    }

    @Override
    public int countMembers(@NonNull GroupModel groupModel) {
        synchronized (this.groupIdentityCache) {
            String[] existingIdentities = this.groupIdentityCache.get(groupModel.getId());
            if (existingIdentities != null) {
                return existingIdentities.length;
            }
        }
        int userMemberCount = groupModel.getUserState() == GroupModel.UserState.MEMBER ? 1 : 0;
        return (int) this.databaseService.getGroupMemberModelFactory().countMembersWithoutUser(groupModel.getId()) + userMemberCount;
    }

    @Override
    public boolean isNotesGroup(@NonNull GroupModel groupModel) {
        return
            isGroupCreator(groupModel) &&
                countMembers(groupModel) == 1;
    }

    @Override
    public int countMembersWithoutUser(@NonNull GroupModel groupModel) {
        return (int) this.databaseService
            .getGroupMemberModelFactory()
            .countMembersWithoutUser(groupModel.getId());
    }

    @Override
    @NonNull
    public Map<String, Integer> getGroupMemberIDColorIndices(@NonNull ch.threema.data.models.GroupModel model) {
        long groupDatabaseId = model.getDatabaseId();
        Map<String, Integer> colors = this.groupMemberColorCache.get((int) groupDatabaseId);
        if (colors == null || colors.isEmpty()) {
            colors = this.databaseService.getGroupMemberModelFactory().getIDColorIndices(groupDatabaseId);

            this.groupMemberColorCache.put((int) groupDatabaseId, colors);
        }

        return colors;
    }

    @Override
    @NonNull
    public List<GroupModel> getGroupsByIdentity(@Nullable String identity) {
        List<GroupModel> groupModels = new ArrayList<>();
        if (TestUtil.isEmptyOrNull(identity) || !TestUtil.required(this.databaseService, this.groupModelCache)) {
            return groupModels;
        }

        identity = identity.toUpperCase();

        List<Integer> res = this.databaseService.getGroupMemberModelFactory().getGroupIdsByIdentity(
            identity);

        List<Integer> groupIds = new ArrayList<>();
        synchronized (this.groupModelCache) {
            for (int id : res) {
                GroupModel existingGroupModel = this.groupModelCache.get(id);
                if (existingGroupModel == null) {
                    groupIds.add(id);
                } else {
                    groupModels.add(existingGroupModel);
                }
            }
        }

        if (!groupIds.isEmpty()) {
            List<GroupModel> groups = this.databaseService.getGroupModelFactory().getInId(
                groupIds);

            for (GroupModel gm : groups) {
                groupModels.add(this.cache(gm));
            }
        }

        return groupModels;
    }

    @Override
    public GroupAccessModel getAccess(@Nullable GroupModel groupModel, boolean allowEmpty) {
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
        ch.threema.data.models.GroupModel groupModel = groupModelRepository
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
    public void bumpLastUpdate(@NonNull GroupModel groupModel) {
        GroupIdentity groupIdentity = new GroupIdentity(
            groupModel.getCreatorIdentity(),
            groupModel.getApiGroupId().toLong()
        );
        this.databaseService.getGroupModelFactory().setLastUpdate(groupIdentity, new Date());
        ListenerManager.groupListeners.handle(listener -> listener.onUpdate(groupIdentity));
    }

    @Override
    public boolean isFull(final GroupModel groupModel) {
        return this.countMembers(groupModel) >= BuildConfig.MAX_GROUP_SIZE;
    }

    @Override
    public void removeGroupMessageState(@NonNull GroupMessageModel messageModel, @NonNull String identityToRemove) {
        GroupModel groupModel = getById(messageModel.getGroupId());
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

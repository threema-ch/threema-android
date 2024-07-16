/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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
import android.util.SparseArray;
import android.widget.ImageView;

import com.bumptech.glide.RequestManager;

import org.slf4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.GroupDetailActivity;
import ch.threema.app.collections.Functional;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.tasks.OutgoingGroupDeleteProfilePictureTask;
import ch.threema.app.tasks.OutgoingGroupLeaveTask;
import ch.threema.app.tasks.OutgoingGroupNameTask;
import ch.threema.app.tasks.OutgoingGroupProfilePictureTask;
import ch.threema.app.tasks.OutgoingGroupSetupTask;
import ch.threema.app.tasks.OutgoingGroupSyncRequestTask;
import ch.threema.app.tasks.OutgoingGroupSyncTask;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConversationUtil;
import ch.threema.app.utils.GroupFeatureSupport;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.OutgoingCspMessageUtilsKt;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base32;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.factories.GroupMemberModelFactory;
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

public class GroupServiceImpl implements GroupService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupServiceImpl");
	private static final String GROUP_UID_PREFIX = "g-";

	private final Context context;

	// Services
	private final @NonNull ServiceManager serviceManager;
	private final @NonNull UserService userService;
	private final @NonNull ContactService contactService;
	private final @NonNull DatabaseServiceNew databaseServiceNew;
	private final @NonNull AvatarCacheService avatarCacheService;
	private final @NonNull FileService fileService;
	private final @NonNull WallpaperService wallpaperService;
	private final @NonNull DeadlineListService mutedChatsListService, hiddenChatsListService;
	private final @NonNull RingtoneService ringtoneService;
	private final @NonNull ConversationTagService conversationTagService;

	private final SparseArray<Map<String, Integer>> groupMemberColorCache;
	private final SparseArray<GroupModel> groupModelCache;
	private final SparseArray<String[]> groupIdentityCache;
	private @Nullable TaskManager taskManager = null;

	// String-enum used when updating certain group fields, e.g. name or profile picture
	@IntDef({UPDATE_UNCHANGED, UPDATE_SUCCESS, UPDATE_ERROR})
	public @interface UpdateResult {}
	public static final int UPDATE_UNCHANGED = 0;
	public static final int UPDATE_SUCCESS = 1;
	public static final int UPDATE_ERROR = 2;

	public GroupServiceImpl(
		@NonNull Context context,
		@NonNull CacheService cacheService,
		@NonNull UserService userService,
		@NonNull ContactService contactService,
		@NonNull DatabaseServiceNew databaseServiceNew,
		@NonNull AvatarCacheService avatarCacheService,
		@NonNull FileService fileService,
		@NonNull WallpaperService wallpaperService,
		@NonNull DeadlineListService mutedChatsListService,
		@NonNull DeadlineListService hiddenChatsListService,
		@NonNull RingtoneService ringtoneService,
		@NonNull ConversationTagService conversationTagService,
		@NonNull ServiceManager serviceManager
	) {
		this.context = context;

		this.userService = userService;
		this.contactService = contactService;
		this.databaseServiceNew = databaseServiceNew;
		this.avatarCacheService = avatarCacheService;
		this.fileService = fileService;
		this.wallpaperService = wallpaperService;
		this.mutedChatsListService = mutedChatsListService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.ringtoneService = ringtoneService;
		this.conversationTagService = conversationTagService;
		this.serviceManager = serviceManager;

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
		List<GroupModel> res = new ArrayList<>(this.databaseServiceNew.getGroupModelFactory().filter(filter));

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
	public void dissolveGroupFromLocal(@NonNull final GroupModel groupModel) {
		String myIdentity = userService.getIdentity();
		if (!myIdentity.equals(groupModel.getCreatorIdentity())) {
			logger.error("Cannot dissolve a group where the user is not the creator");
			return;
		}

		// Send an empty sync
		String[] identities = getGroupIdentities(groupModel);
		scheduleEmptyGroupSetup(groupModel, Set.of(identities));

		// Remove me from the group members
		removeMemberFromGroup(groupModel, userService.getIdentity());

		// Update the rejected message states
		runRejectedMessagesRefreshSteps(groupModel);

		// Trigger listener
		ListenerManager.groupListeners.handle(listener -> listener.onMemberLeave(groupModel, myIdentity, identities.length));
		ListenerManager.groupListeners.handle(listener -> listener.onLeave(groupModel));
	}

	@Override
	public void leaveGroupFromLocal(@NonNull final GroupModel groupModel) {
		if (isGroupCreator(groupModel)) {
			logger.error("Cannot leave own groups");
			return;
		}

		String[] identities = this.getGroupIdentities(groupModel);

		// Send group leave to all members
		scheduleGroupLeave(groupModel, Set.of(identities));

		// Remove only me from the members
		String myIdentity = userService.getIdentity();
		removeMemberFromGroup(groupModel, myIdentity);

		// Update the rejected message states
		runRejectedMessagesRefreshSteps(groupModel);

		ShortcutUtil.deleteShareTargetShortcut(getUniqueIdString(groupModel));
		ShortcutUtil.deletePinnedShortcut(getUniqueIdString(groupModel));

		//reset cache
		this.resetIdentityCache(groupModel.getId());

		// Fire group left listener
		ListenerManager.groupListeners.handle(listener -> listener.onMemberLeave(groupModel, myIdentity, identities.length));
		ListenerManager.groupListeners.handle(listener -> listener.onLeave(groupModel));
		updateAllowedCallParticipants(groupModel);
	}

	@Override
	public boolean remove(@NonNull GroupModel groupModel) {
		if (isGroupMember(groupModel)) {
			// We do not support removing groups where the user is still a member. The group must be
			// left first.
			logger.error("Group cannot be removed as the user is still a member");
			return false;
		}
		return this.remove(groupModel, false, false);
	}

	@Override
	public void leaveOrDissolveAndRemoveFromLocal(@NonNull GroupModel groupModel) {
		if (this.isGroupMember(groupModel)) {
			if (this.isGroupCreator(groupModel)) {
				this.dissolveGroupFromLocal(groupModel);
			} else {
				this.leaveGroupFromLocal(groupModel);
			}
		}
		this.remove(groupModel);
	}

	private boolean remove(@NonNull final GroupModel groupModel, boolean silent, boolean forceRemove) {
		// Obtain some services through service manager
		//
		// Note: We cannot put these services in the constructor due to circular dependencies.
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			logger.error("Missing serviceManager, cannot remove group");
			return false;
		}
		final ConversationService conversationService;
		final BallotService ballotService;
		try {
			conversationService = serviceManager.getConversationService();
			ballotService = serviceManager.getBallotService();
		} catch (ThreemaException e) {
			logger.error("Could not obtain services when removing group", e);
			return false;
		}

		// Delete polls
		ballotService.remove(createReceiver(groupModel));

		// Remove all group invite links and requests
		final GroupInviteModelFactory groupInviteModelFactory = this.databaseServiceNew.getGroupInviteModelFactory();
		final IncomingGroupJoinRequestModelFactory incomingGroupJoinRequestModelFactory = this.databaseServiceNew.getIncomingGroupJoinRequestModelFactory();
		for (GroupInviteModel groupInviteModel : groupInviteModelFactory.getByGroupApiId(groupModel.getApiGroupId())) {
			incomingGroupJoinRequestModelFactory.deleteAllForGroupInvite(groupInviteModel.getId());
			groupInviteModelFactory.delete(groupInviteModel);
		}

		// Remove all messages
		for (GroupMessageModel messageModel : this.databaseServiceNew.getGroupMessageModelFactory().getByGroupIdUnsorted(groupModel.getId())) {
			this.fileService.removeMessageFiles(messageModel, true);
		}
		this.databaseServiceNew.getGroupMessageModelFactory().deleteByGroupId(groupModel.getId());

		// Remove avatar
		this.fileService.removeGroupAvatar(groupModel);
		this.avatarCacheService.reset(groupModel);

		// Remove chat settings (e.g. wallpaper or custom ringtones)
		String uniqueIdString = getUniqueIdString(groupModel);
		this.wallpaperService.removeWallpaper(uniqueIdString);
		this.ringtoneService.removeCustomRingtone(uniqueIdString);
		this.mutedChatsListService.remove(uniqueIdString);
		this.hiddenChatsListService.remove(uniqueIdString);
		ShortcutUtil.deleteShareTargetShortcut(uniqueIdString);
		ShortcutUtil.deletePinnedShortcut(uniqueIdString);
		this.conversationTagService.removeAll(ConversationUtil.getGroupConversationUid(groupModel.getId()));

		// Update model
		groupModel.setDeleted(true);
		groupModel.setLastUpdate(null);
		save(groupModel);

		// Remove conversation
		conversationService.removeFromCache(groupModel);

		// Delete group members
		this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupId(groupModel.getId());

		// Delete group fully from database if `forceRemove` is set
		if (forceRemove) {
			this.databaseServiceNew.getGroupModelFactory().delete(groupModel);

			synchronized (this.groupModelCache) {
				this.groupModelCache.remove(groupModel.getId());
			}
		}

		this.resetIdentityCache(groupModel.getId());

		if (!silent) {
			ListenerManager.groupListeners.handle(listener -> listener.onRemove(groupModel));
		}

		return true;
	}

	@Override
	public void removeAll() {
		for (GroupModel g : this.getAll()) {
			this.remove(g, true, true);
		}
		//remove last request sync table

		this.databaseServiceNew.getGroupRequestSyncLogModelFactory().deleteAll();
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
			GroupModel model = Functional.select(this.groupModelCache, type -> apiGroupId.toString().equals(type.getApiGroupId().toString()) && creatorIdentity.equals(type.getCreatorIdentity()));

			if (model == null) {
				model = this.databaseServiceNew.getGroupModelFactory().getByApiGroupIdAndCreator(
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

	@Override
	public Intent getGroupDetailIntent(@NonNull GroupModel groupModel, @NonNull Activity activity) {
		Intent intent = new Intent(activity, GroupDetailActivity.class);
		intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupModel.getId());
		return intent;
	}

	@Override
	public @Nullable GroupModel getById(int groupId) {
		synchronized (this.groupModelCache) {
			GroupModel existingGroupModel = groupModelCache.get(groupId);
			if (existingGroupModel != null) {
				return existingGroupModel;
			}
			return this.cache(this.databaseServiceNew.getGroupModelFactory().getById(groupId));
		}
	}

	@NonNull
	@Override
	public GroupModel createGroupFromLocal(
		@NonNull String name,
		@NonNull Set<String> groupMemberIdentities,
		@Nullable Bitmap picture
	) throws Exception {
		if (AppRestrictionUtil.isCreateGroupDisabled(ThreemaApplication.getAppContext())) {
			throw new PolicyViolationException();
		}

		final GroupModel model = this.createGroupFromLocal(name, groupMemberIdentities);

		if (picture != null) {
			// Always send the group picture if there is a group picture
			setGroupPictureFromLocal(model, picture);
		} else {
			// Always send a delete group picture if there is no group picture
			deleteGroupPictureFromLocal(model);
		}

		return model;
	}

	@NonNull
	private GroupModel createGroupFromLocal(
		@NonNull String name,
		@NonNull final Set<String> groupMemberIdentities
	) throws ThreemaException, PolicyViolationException {
		if (AppRestrictionUtil.isCreateGroupDisabled(ThreemaApplication.getAppContext())) {
			throw new PolicyViolationException();
		}

		final String creatorIdentity = userService.getIdentity();

		// Create group model in database and cache
		//
		// Note: Don't yet set the group name in the model here, otherwise no group name message is
		// sent because the group name looks up to date in updateGroupNameFromLocal
		final GroupModel groupModel = new GroupModel();
		final String randomId = UUID.randomUUID().toString();
		final GroupId id = new GroupId(Utils.hexStringToByteArray(randomId.substring(randomId.length() - (ProtocolDefines.GROUP_ID_LEN * 2))));
		final Date now = new Date();
		groupModel
			.setApiGroupId(id)
			.setCreatorIdentity(creatorIdentity)
			.setCreatedAt(now)
			.setLastUpdate(now)
			.setSynchronizedAt(now);
		this.databaseServiceNew.getGroupModelFactory().create(groupModel);
		this.cache(groupModel);

		// Add members to group (including own identity)
		this.addMemberToGroup(groupModel, creatorIdentity);
		for (String identity : groupMemberIdentities) {
			this.addMemberToGroup(groupModel, identity);
		}

		// Notify listeners
		for (String memberIdentity : groupMemberIdentities) {
			ListenerManager.groupListeners.handle(listener ->
				listener.onNewMember(groupModel, memberIdentity, 0)
			);
		}
		ListenerManager.groupListeners.handle(listener -> listener.onCreate(groupModel));
		ListenerManager.groupListeners.handle(listener -> listener.onGroupStateChanged(groupModel, UNDEFINED, getGroupState(groupModel)));

		// Schedule OutgoingGroupSetupTask to send group-setup message to all members
		scheduleGroupSetup(groupModel, groupMemberIdentities, groupMemberIdentities);

		// Update the group name and send group-name message to all members
		updateGroupNameFromLocal(groupModel, name);

		return groupModel;
	}

	@Override
	public boolean addMemberToGroup(@NonNull final GroupModel groupModel, @NonNull final String identity) {
		final GroupMemberModel memberModel = this.getGroupMember(groupModel, identity);

		if (memberModel == null) {
			// Do not add the member to the group if it is already in the group or there is no contact
			// with that ID. Note that the contacts for the valid identities have been created at this
			// point. Therefore only invalid identities are not available as a contact and therefore not
			// added to the group.
			if (contactService.getByIdentity(identity) != null) {
				GroupMemberModel newMemberModel = new GroupMemberModel()
					.setGroupId(groupModel.getId())
					.setIdentity(identity);
				this.databaseServiceNew.getGroupMemberModelFactory().create(newMemberModel);

				this.resetIdentityCache(groupModel.getId());

				return true;
			} else {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean removeMemberFromGroup(@NonNull GroupModel groupModel, @NonNull String identity) {
		GroupMemberModelFactory memberModelFactory = databaseServiceNew.getGroupMemberModelFactory();
		int deletedCount = memberModelFactory.deleteByGroupIdAndIdentity(groupModel.getId(), identity);
		resetIdentityCache(groupModel.getId());
		return deletedCount == 1;
	}

	@Override
	public void runRejectedMessagesRefreshSteps(@NonNull GroupModel groupModel) {
		RejectedGroupMessageFactory rejectedGroupMessageFactory = databaseServiceNew.getRejectedGroupMessageFactory();
		GroupMessageModelFactory groupMessageModelFactory = databaseServiceNew.getGroupMessageModelFactory();
		List<GroupMessageModel> updatedMessages;

		if (!isGroupMember(groupModel)) {
			// If the group is marked as left, remove all reject marks and receivers requiring a re-send
			rejectedGroupMessageFactory.removeAllMessageRejectsInGroup(groupModel);

			updatedMessages = groupMessageModelFactory.getAllRejectedMessagesInGroup(groupModel);
		} else {
			// If the group is not marked left, we remove all ex-members from the reject table that
			// have rejected a message in this group. Message models that do not have any rejecting
			// member anymore after this cleanup will be updated.
			updatedMessages = new ArrayList<>();
			Set<String> members = Set.of(getGroupIdentities(groupModel));

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
	@GroupState
	public int getGroupState(@Nullable GroupModel groupModel) {
		if (groupModel != null) {
			return isNotesGroup(groupModel) ? NOTES : PEOPLE;
		}
		return UNDEFINED;
	}

	@Override
	public @NonNull GroupModel updateGroup(
		final @NonNull GroupModel groupModel,
		@Nullable String name,
		@Nullable String groupDesc,
		final @Nullable String[] groupMemberIdentities,
		@Nullable Bitmap picture,
		boolean removePicture
	) throws Exception {
		String myIdentity = userService.getIdentity();
		if (!groupModel.getCreatorIdentity().equals(myIdentity)) {
			throw new ThreemaException("Cannot update a group where the user is not the creator");
		}

		@GroupState int oldGroupState = getGroupState(groupModel);

		// Existing members
		Set<String> existingMembers = new HashSet<>(Arrays.asList(getGroupIdentities(groupModel)));
		existingMembers.remove(myIdentity);

		// The updated list of the group members (null if there are no changes)
		Set<String> updatedGroupMembers = new HashSet<>();

		// Determine new members and add them to all involved members
		Set<String> newMembers = new HashSet<>();
		if (groupMemberIdentities != null) {
			for (String identity : groupMemberIdentities) {
				updatedGroupMembers.add(identity);
				// Add all non existing members (except the creator)
				if (!existingMembers.contains(identity) && !myIdentity.equals(identity)) {
					newMembers.add(identity);
				}
			}
		} else {
			updatedGroupMembers.addAll(existingMembers);
		}

		// List of all kicked identities
		Set<String> kickedGroupMembers = new HashSet<>(existingMembers);
		kickedGroupMembers.removeAll(updatedGroupMembers);

		int previousMemberCount = countMembers(groupModel);

		// Remove the kicked members from the database
		for (final String kickedIdentity : kickedGroupMembers) {
			logger.debug("Remove member {} from group", kickedIdentity);
			removeMemberFromGroup(groupModel, kickedIdentity);

			ListenerManager.groupListeners.handle(listener ->
				listener.onMemberKicked(groupModel, kickedIdentity, previousMemberCount)
			);
		}

		// Add new members to the database
		for (String newMember : newMembers) {
			logger.debug("Add member {} to group", newMember);
			this.addMemberToGroup(groupModel, newMember);
		}

		boolean hasNewMembers = !newMembers.isEmpty();
		boolean hasKickedMembers = !kickedGroupMembers.isEmpty();
		boolean hasMemberChanges = hasNewMembers || hasKickedMembers;

		if (hasMemberChanges) {
			resetIdentityCache(groupModel.getId());

			runRejectedMessagesRefreshSteps(groupModel);
		}

		// If the user wants to delete the group picture, we send a delete message to the removed
		// members. Note that the delete group picture message is sent to the updated group after
		// the new group setup message.
		if (removePicture && hasKickedMembers) {
			scheduleGroupDeletePhoto(groupModel, kickedGroupMembers);
		}

		// Send a setup message with an empty member list to the removed members
		if (hasKickedMembers) {
			scheduleEmptyGroupSetup(groupModel, kickedGroupMembers);
		}

		// Send a setup message with the updated group member list to all (updated) group members
		if (hasMemberChanges) {
			scheduleGroupSetup(groupModel, updatedGroupMembers, updatedGroupMembers);
		}

		final boolean nameChanged = name != null && groupNameChanged(groupModel, name);
		if (nameChanged) {
			// Rename the group (for all members) if the group name has changed. This includes the
			// new members of the group.
			updateGroupNameFromLocal(groupModel, name);
		} else if (hasNewMembers) {
			// Send rename message to all new group members, so that they receive the group name,
			// even if the group name did not change.
			sendGroupRenameToIdentitiesIfOwner(groupModel, newMembers);
		}

		boolean sendPictureToNewMembers = true;
		if (picture != null || removePicture) {
			// If there is a group picture change (either remove, or a new picture), update it. Note
			// that this includes the new members.
			final @UpdateResult int result = updateGroupPictureFromLocal(groupModel, picture);
			if (result != UPDATE_UNCHANGED) {
				// If there was a change, then the method above already dealt with sending sync
				// messages to all members (including new members).
				sendPictureToNewMembers = false;
			}
		}
		if (sendPictureToNewMembers && hasNewMembers) {
			// If there are new members, we need to send a set profile picture or delete profile
			// picture message - depending on whether there is a group picture set or not. Note that
			// this must be done according to the protocol.
			scheduleGroupPhoto(groupModel, newMembers);
		}

		if (groupDesc != null) {
			this.changeGroupDesc(groupModel, groupDesc);
		}

		// Trigger the listeners for the new members. Note that this sends out open polls to the new
		// members and must therefore be called *after* the group setup has been sent.
		for (String newMember : newMembers) {
			ListenerManager.groupListeners.handle(listener ->
				listener.onNewMember(groupModel, newMember, previousMemberCount)
			);
		}

		scheduleGroupCallStart(groupModel, newMembers);

		// Fire the group state listener if the group state has changed
		@GroupState int newGroupState = getGroupState(groupModel);
		if (oldGroupState != newGroupState) {
			ListenerManager.groupListeners.handle(listener ->
				listener.onGroupStateChanged(groupModel, oldGroupState, newGroupState)
			);
		}

		updateAllowedCallParticipants(groupModel);

		return groupModel;
	}

	/**
	 * Return whether or not the specified {@param newName} is different from the existing group name.
	 */
	private static boolean groupNameChanged(@NonNull GroupModel groupModel, @NonNull String newName) {
		String oldName = groupModel.getName();
		oldName = oldName != null ? oldName : "";
		return !oldName.equals(newName);
	}

	/**
	 * Update the group name. Besides updating the database and group model, this fires the group
	 * listeners and sends the group name message to the members. If the new name is the same as the
	 * old name, nothing is done.
	 *
	 * @param groupModel the group model that is changed
	 * @param newName    the new group name
	 */
	private void updateGroupNameFromLocal(@NonNull GroupModel groupModel, @NonNull String newName) {
		if (!isGroupCreator(groupModel)) {
			logger.error("Cannot rename group where the user is not the creator");
			return;
		}

		if (!groupNameChanged(groupModel, newName)) {
			return;
		}

		// Update and save the group model
		groupModel.setName(newName);
		this.save(groupModel);

		// Fire the group rename listener
		ListenerManager.groupListeners.handle(listener -> listener.onRename(groupModel));

		// delete share target shortcut as name is different
		ShortcutUtil.deleteShareTargetShortcut(getUniqueIdString(groupModel.getId()));

		// Send rename message to group members if group owner
		Set<String> groupIdentities = Set.of(getGroupIdentities(groupModel));
		sendGroupRenameToIdentitiesIfOwner(groupModel, groupIdentities);
	}

	/**
	 * Update the group picture. If the picture is null, then the group picture is deleted. If the
	 * old picture and the new picture is the same, then nothing is done. This method also fires the
	 * the group listeners and sends the message to the group members.
	 *
	 * @param groupModel the group model
	 * @param picture    the new picture
	 * @return an {@link UpdateResult}
	 */
	private @UpdateResult int updateGroupPictureFromLocal(@NonNull GroupModel groupModel, @Nullable Bitmap picture) {
		if (!isGroupCreator(groupModel)) {
			logger.error("Cannot rename group where the user is not the creator");
			return UPDATE_ERROR;
		}

		try {
			if (picture == null && fileService.hasGroupAvatarFile(groupModel)) {
				deleteGroupPictureFromLocal(groupModel);
				return UPDATE_SUCCESS;
			} else if (picture != null) {
				Bitmap existingGroupPicture = fileService.getGroupAvatar(groupModel);
				if (picture.sameAs(existingGroupPicture)) {
					return UPDATE_UNCHANGED;
				} else {
					setGroupPictureFromLocal(groupModel, picture);
					return UPDATE_SUCCESS;
				}
			}
		} catch (Exception e) {
			logger.error("Failed to update group picture", e);
			return UPDATE_ERROR;
		}

		return UPDATE_SUCCESS;
	}

	/**
	 * Update the group picture locally. Upload the group picture and send it to the members. This
	 * also fires the group listeners.
	 *
	 * @param groupModel the group model
	 * @param picture    the group picture
	 * @throws Exception if uploading, sending or saving the group picture failed
	 */
	private void setGroupPictureFromLocal(@NonNull GroupModel groupModel, @NonNull Bitmap picture) throws Exception {
		byte[] groupPicture = BitmapUtil.bitmapToJpegByteArray(picture);

		// Save the image
		boolean success = fileService.writeGroupAvatar(groupModel, groupPicture);

		if (success) {
			// Reset the avatar cache entry
			avatarCacheService.reset(groupModel);

			// Fire listeners
			ListenerManager.groupListeners.handle(listener -> listener.onUpdatePhoto(groupModel));

			// Send to the new blob to the users
			scheduleGroupPhoto(groupModel, Set.of(getGroupIdentities(groupModel)));
		}
	}

	/**
	 * Delete the group picture. This also sends a group delete photo message. Note that this is
	 * also done, if the group currently does not have a group picture. The group listeners are only
	 * fired, if there was a group picture previously.
	 *
	 * @param groupModel the group that is updated
	 */
	private void deleteGroupPictureFromLocal(@NonNull GroupModel groupModel) {
		boolean hadGroupPicture = fileService.hasGroupAvatarFile(groupModel);

		fileService.removeGroupAvatar(groupModel);

		avatarCacheService.reset(groupModel);

		if (hadGroupPicture) {
			ListenerManager.groupListeners.handle(listener -> listener.onUpdatePhoto(groupModel));
		}

		scheduleGroupPhoto(groupModel, Set.of(getGroupIdentities(groupModel)));
	}

	private void scheduleGroupCallStart(@NonNull GroupModel groupModel, @NonNull Set<String> newMemberIdentities) {
		if (newMemberIdentities.isEmpty()) {
			return;
		}
		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager == null) {
				logger.error("Service manager is null. Aborting potential group call start send");
				return;
			}
			GroupCallManager groupCallManager = serviceManager.getGroupCallManager();
			groupCallManager.scheduleGroupCallStartForNewMembers(groupModel, newMemberIdentities);
		} catch (ThreemaException e) {
			logger.error("Could not get group call manager. Aborting potential group call start send", e);
		}
	}

	private void updateAllowedCallParticipants(@NonNull GroupModel groupModel) {
		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager == null) {
				logger.error("Service manager is null. Abort updating allowed call participants");
				return;
			}
			serviceManager.getGroupCallManager().updateAllowedCallParticipants(groupModel);
		} catch (ThreemaException e) {
			logger.error("Could not get group call manager. Abort updating allowed call participants");
		}
	}

	private void sendGroupRenameToIdentitiesIfOwner(@NonNull GroupModel groupModel, @NonNull Set<String> identities) {
		if (!isGroupCreator(groupModel)) {
			// Don't send the group rename if the user is not the owner
			return;
		}

		final String groupName = groupModel.getName() != null ? groupModel.getName() : "";
		scheduleGroupName(groupModel, identities, groupName);
	}

	// on Update new group desc
	private void changeGroupDesc(final GroupModel group, final String newGroupDesc) {
		group.setGroupDesc(newGroupDesc);
		this.save(group);
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
	public Set<String> getOtherMembers(@NonNull GroupModel groupModel) {
		Set<String> otherMembers = new HashSet<>(Arrays.asList(getGroupIdentities(groupModel)));
		otherMembers.remove(userService.getIdentity());
		return otherMembers;
	}

	@NonNull
	@Override
	public String[] getGroupIdentities(@NonNull GroupModel groupModel) {
		synchronized (this.groupIdentityCache) {
			String[] existingIdentities = this.groupIdentityCache.get(groupModel.getId());
			if (existingIdentities != null) {
				return existingIdentities;
			}

			List<GroupMemberModel> memberModels = this.getGroupMemberModels(groupModel);
			String[] res = new String[memberModels.size()];
			for (int i = 0; i < memberModels.size(); i++) {
				res[i] = memberModels.get(i).getIdentity();
			}

			this.groupIdentityCache.put(groupModel.getId(), res);
			return res;
		}
	}

	private boolean isGroupMember(@NonNull GroupModel groupModel, @Nullable String identity) {
		if (!TestUtil.empty(identity)) {
			for (String existingIdentity : this.getGroupIdentities(groupModel)) {
				if (TestUtil.compare(existingIdentity, identity)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean isGroupMember(@NonNull GroupModel groupModel) {
		return isGroupMember(groupModel, userService.getIdentity());
	}

	@Override
	public boolean isOrphanedGroup(@NonNull GroupModel groupModel) {
		return !isGroupMember(groupModel, groupModel.getCreatorIdentity()) && !isGroupCreator(groupModel);
	}

	@Override
	public List<GroupMemberModel> getGroupMemberModels(@NonNull GroupModel groupModel) {
		return this.databaseServiceNew.getGroupMemberModelFactory().getByGroupId(
			groupModel.getId()
		);
	}

	/**
	 * Get the group member model by group id and identity.
	 *
	 * @param groupModel the group model of the member
	 * @param identity   the identity of the member
	 * @return the group member model if it exists, null otherwise
	 */
	@Nullable
	private GroupMemberModel getGroupMember(@NonNull GroupModel groupModel, @NonNull String identity) {
		return this.databaseServiceNew.getGroupMemberModelFactory().getByGroupIdAndIdentity(
			groupModel.getId(),
			identity);
	}

	@Override
	@NonNull
	public Collection<ContactModel> getMembers(@NonNull GroupModel groupModel) {
		return this.contactService.getByIdentities(this.getGroupIdentities(groupModel));
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

	@Override
	@NonNull
	public GroupMessageReceiver createReceiver(@NonNull GroupModel groupModel) {
		return new GroupMessageReceiver(
			groupModel,
			this,
			this.databaseServiceNew,
			this.serviceManager
		);
	}

	@AnyThread
	@Nullable
	@Override
	public Bitmap getAvatar(@Nullable GroupModel groupModel, @NonNull AvatarOptions options) {
		// If the custom avatar is requested without default fallback and there is no avatar for
		// this group, we can return null directly. Important: This is necessary to prevent glide
		// from logging an unnecessary error stack trace.
		if (options.defaultAvatarPolicy == AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR
			&& !fileService.hasGroupAvatarFile(groupModel)) {
			return null;
		}

		return avatarCacheService.getGroupAvatar(groupModel, options);
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
	public void clearAvatarCache(@NonNull GroupModel model) {
		avatarCacheService.reset(model);
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
		return (int) this.databaseServiceNew.getGroupMemberModelFactory().countMembers(groupModel.getId());
	}

	@Override
	public boolean isNotesGroup(@NonNull GroupModel groupModel) {
		return
			isGroupCreator(groupModel) &&
				countMembers(groupModel) == 1;
	}

	@Override
	public int getOtherMemberCount(@NonNull GroupModel groupModel) {
		int count = 0;
		String[] identities = this.getGroupIdentities(groupModel);
		for (String identity : identities) {
			if (!this.userService.isMe(identity)) {
				count++;
			}
		}
		return count;
	}

	@Override
	@NonNull
	public Map<String, Integer> getGroupMemberIDColorIndices(@NonNull GroupModel model) {
		Map<String, Integer> colors = this.groupMemberColorCache.get(model.getId());
		if (colors == null || colors.isEmpty()) {
			colors = this.databaseServiceNew.getGroupMemberModelFactory().getIDColorIndices(
				model.getId()
			);

			this.groupMemberColorCache.put(model.getId(), colors);
		}

		return colors;
	}

	private void scheduleEmptyGroupSetup(
		@NonNull GroupModel groupModel,
		@NonNull Set<String> receiverIdentities
	) {
		scheduleGroupSetup(groupModel, receiverIdentities, Collections.emptySet());
	}

	private void scheduleGroupSetup(
		@NonNull GroupModel groupModel,
		@NonNull Set<String> receiverIdentities,
		@NonNull Set<String> memberIdentities
	) {
		getTaskManager().schedule(new OutgoingGroupSetupTask(
			groupModel.getApiGroupId(),
			groupModel.getCreatorIdentity(),
			memberIdentities,
			receiverIdentities,
			null,
			serviceManager
		));
	}

	private void scheduleGroupName(
		@NonNull GroupModel groupModel,
		@NonNull Set<String> receiverIdentities,
		@NonNull String groupName
	) {
		getTaskManager().schedule(new OutgoingGroupNameTask(
			groupModel.getApiGroupId(),
			groupModel.getCreatorIdentity(),
			groupName,
			receiverIdentities,
			null,
			serviceManager
		));
	}

	/**
	 * Schedule a group photo task. Note that this will send a set-profile-picture message if a
	 * group avatar is set. If there is no group avatar set, a delete-profile-picture message is
	 * sent.
	 *
	 * @param groupModel         the group model
	 * @param receiverIdentities the receiver identities
	 */
	private void scheduleGroupPhoto(
		@NonNull GroupModel groupModel,
		@NonNull Set<String> receiverIdentities
	) {
		getTaskManager().schedule(new OutgoingGroupProfilePictureTask(
			groupModel.getApiGroupId(),
			groupModel.getCreatorIdentity(),
			receiverIdentities,
			null,
			serviceManager
		));
	}

	/**
	 * Schedule a group delete photo task. Note that this will send a delete-profile-picture message
	 * even if a group avatar is set for this group!
	 *
	 * @param groupModel         the group model
	 * @param receiverIdentities the receiver identities
	 */
	private void scheduleGroupDeletePhoto(
		@NonNull GroupModel groupModel,
		@NonNull Set<String> receiverIdentities
	) {
		getTaskManager().schedule(new OutgoingGroupDeleteProfilePictureTask(
			groupModel.getApiGroupId(),
			groupModel.getCreatorIdentity(),
			receiverIdentities,
			null,
			serviceManager
		));
	}

	private void scheduleGroupLeave(
		@NonNull GroupModel groupModel,
		@NonNull Set<String> receiverIdentities
	) {
		getTaskManager().schedule(new OutgoingGroupLeaveTask(
			groupModel.getApiGroupId(),
			groupModel.getCreatorIdentity(),
			receiverIdentities,
			null,
			serviceManager
		));
	}

	@Override
	public boolean scheduleSync(GroupModel groupModel) {
		// Send event to clients
		final String[] groupMemberIdentities = this.getGroupIdentities(groupModel);

		// Send to ALL members!
		return this.scheduleSync(groupModel, groupMemberIdentities);
	}

	@Override
	public boolean scheduleSync(@NonNull final GroupModel groupModel, @NonNull final String[] receiverIdentities) {
		String creatorIdentity = groupModel.getCreatorIdentity();
		Set<String> receivers = new HashSet<>();
		for (String identity : receiverIdentities) {
			if (!creatorIdentity.equals(identity)) {
				receivers.add(identity);
			}
		}

		Set<String> finalReceiverIdentities;
		if (receivers.size() != receiverIdentities.length) {
			finalReceiverIdentities = receivers;
		} else {
			finalReceiverIdentities = Set.of(receiverIdentities);
		}

		getTaskManager().schedule(new OutgoingGroupSyncTask(
			groupModel.getApiGroupId(),
			groupModel.getCreatorIdentity(),
			finalReceiverIdentities,
			serviceManager
		));

		groupModel.setSynchronizedAt(new Date());
		this.save(groupModel);

		return true;
	}

	@Override
	@NonNull
	public List<GroupModel> getGroupsByIdentity(@Nullable String identity) {
		List<GroupModel> groupModels = new ArrayList<>();
		if (TestUtil.empty(identity) || !TestUtil.required(this.databaseServiceNew, this.groupModelCache)) {
			return groupModels;
		}

		identity = identity.toUpperCase();

		List<Integer> res = this.databaseServiceNew.getGroupMemberModelFactory().getGroupIdsByIdentity(
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

		if (groupIds.size() > 0) {
			List<GroupModel> groups = this.databaseServiceNew.getGroupModelFactory().getInId(
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
			} else if (getOtherMemberCount(groupModel) <= 0 && !allowEmpty) {
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
	@Deprecated
	public int getUniqueId(GroupModel groupModel) {
		return (GROUP_UID_PREFIX + groupModel.getId()).hashCode();
	}

	@Override
	public String getUniqueIdString(GroupModel groupModel) {
		if (groupModel != null) {
			return getUniqueIdString(groupModel.getId());
		}
		return "";
	}

	@Override
	public String getUniqueIdString(int groupId) {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			messageDigest.update((GROUP_UID_PREFIX + groupId).getBytes());
			return Base32.encode(messageDigest.digest());
		} catch (NoSuchAlgorithmException e) {
			//
		}
		return "";
	}

	@Override
	public void setIsArchived(GroupModel groupModel, boolean archived) {
		if (groupModel != null && groupModel.isArchived() != archived) {
			groupModel.setArchived(archived);
			save(groupModel);

			synchronized (this.groupModelCache) {
				this.groupModelCache.remove(groupModel.getId());
			}

			ListenerManager.groupListeners.handle(listener -> listener.onUpdate(groupModel));
		}
	}

	@Override
	public void bumpLastUpdate(@NonNull GroupModel groupModel) {
		groupModel.setLastUpdate(new Date());
		save(groupModel);
		ListenerManager.groupListeners.handle(listener -> listener.onUpdate(groupModel));
	}

	@Override
	public boolean isFull(final GroupModel groupModel) {
		return this.countMembers(groupModel) >= BuildConfig.MAX_GROUP_SIZE;
	}

	@Override
	public void save(@NonNull GroupModel model) {
		this.databaseServiceNew.getGroupModelFactory().createOrUpdate(model);
	}

	@Override
	public void addGroupMessageState(@NonNull GroupMessageModel messageModel, @NonNull String identityToAdd, @NonNull MessageState newState) {
		GroupModel groupModel = getById(messageModel.getGroupId());
		if (groupModel != null) {
			if (isGroupMember(groupModel, identityToAdd)) {
				Map<String, Object> groupMessageStates = messageModel.getGroupMessageStates();
				if (groupMessageStates == null) {
					groupMessageStates = new HashMap<>();
				}
				groupMessageStates.put(identityToAdd, newState.toString());
				messageModel.setGroupMessageStates(groupMessageStates);
			} else {
				logger.debug("Received state change for non-member {}", identityToAdd);
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
	public GroupFeatureSupport getFeatureSupport(@NonNull GroupModel groupModel, @ThreemaFeature.Feature long feature) {
		return new GroupFeatureSupport(
			feature,
			new ArrayList<>(OutgoingCspMessageUtilsKt.filterBroadcastIdentity(getMembers(groupModel), groupModel))
		);
	}
}

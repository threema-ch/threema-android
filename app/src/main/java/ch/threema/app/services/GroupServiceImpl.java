/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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
import android.text.format.DateUtils;
import android.util.SparseArray;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestManager;
import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.GroupDetailActivity;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.exceptions.NoIdentityException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base32;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.blob.BlobUploader;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.messages.GroupCreateMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeletePhotoMessage;
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage;
import ch.threema.domain.protocol.csp.messages.GroupRenameMessage;
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage;
import ch.threema.domain.protocol.csp.messages.GroupSetPhotoMessage;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.factories.GroupMemberModelFactory;
import ch.threema.storage.factories.GroupMessagePendingMessageIdModelFactory;
import ch.threema.storage.factories.GroupRequestSyncLogModelFactory;
import ch.threema.storage.factories.IncomingGroupJoinRequestModelFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.GroupRequestSyncLogModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.access.Access;
import ch.threema.storage.models.access.GroupAccessModel;
import ch.threema.storage.models.group.GroupInviteModel;

public class GroupServiceImpl implements GroupService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupServiceImpl");
	private static final String GROUP_UID_PREFIX = "g-";

	private final Context context;
	private final ApiService apiService;
	private final GroupMessagingService groupMessagingService;
	private final UserService userService;
	private final ContactService contactService;
	private final DatabaseServiceNew databaseServiceNew;
	private final AvatarCacheService avatarCacheService;
	private final FileService fileService;
	private final PreferenceService preferenceService;
	private final WallpaperService wallpaperService;
	private final DeadlineListService mutedChatsListService, hiddenChatsListService;
	private final RingtoneService ringtoneService;
	private final SparseArray<Map<String, Integer>> groupMemberColorCache;
	private final SparseArray<GroupModel> groupModelCache;
	private final SparseArray<String[]> groupIdentityCache;
	private final List<AbstractGroupMessage> pendingGroupMessages = new ArrayList<>();

	static class GroupPhotoUploadResult {
		public byte[] bitmapArray;
		public byte[] blobId;
		public byte[] encryptionKey;
		public int size;
	}

	public GroupServiceImpl(
			Context context,
			CacheService cacheService,
			ApiService apiService,
			GroupMessagingService groupMessagingService,
			UserService userService,
			ContactService contactService,
			DatabaseServiceNew databaseServiceNew,
			AvatarCacheService avatarCacheService,
			FileService fileService,
			PreferenceService preferenceService,
			WallpaperService wallpaperService,
			DeadlineListService mutedChatsListService,
			DeadlineListService hiddenChatsListService,
			RingtoneService ringtoneService) {
		this.context = context;
		this.apiService = apiService;
		this.groupMessagingService = groupMessagingService;

		this.userService = userService;
		this.contactService = contactService;
		this.databaseServiceNew = databaseServiceNew;
		this.avatarCacheService = avatarCacheService;
		this.fileService = fileService;
		this.preferenceService = preferenceService;
		this.wallpaperService = wallpaperService;
		this.mutedChatsListService = mutedChatsListService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.ringtoneService = ringtoneService;

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

		for(GroupModel m: res) {
			this.cache(m);
		}

		return res;
	}

	private GroupModel cache(GroupModel groupModel) {
		if(groupModel == null) {
			return null;
		}

		synchronized (this.groupModelCache) {
			GroupModel existingGroupModel = groupModelCache.get(groupModel.getId());
			if(existingGroupModel != null) {
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
		sendEmptyGroupSetup(groupModel, identities);

		// Remove me from the group members
		removeMemberFromGroup(groupModel, userService.getIdentity());

		// Trigger listener
		ListenerManager.groupListeners.handle(listener -> listener.onMemberLeave(groupModel, myIdentity, identities.length));
		ListenerManager.groupListeners.handle(listener -> listener.onLeave(groupModel));
	}

	@Override
	public boolean leaveGroupFromLocal(@Nullable final GroupModel groupModel) {
		if (groupModel == null || isGroupCreator(groupModel)) {
			return false;
		}

		String[] identities = this.getGroupIdentities(groupModel);

		try {
			this.groupMessagingService.sendMessage(groupModel, identities, messageId -> {
				final GroupLeaveMessage groupLeaveMessage = new GroupLeaveMessage();
				groupLeaveMessage.setMessageId(messageId);
				return groupLeaveMessage;
			});
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			return false;
		}

		// Remove only me from the members
		String myIdentity = userService.getIdentity();
		removeMemberFromGroup(groupModel, myIdentity);

		ShortcutUtil.deleteShareTargetShortcut(getUniqueIdString(groupModel));
		ShortcutUtil.deletePinnedShortcut(getUniqueIdString(groupModel));

		//reset cache
		this.resetIdentityCache(groupModel.getId());

		// Fire group left listener
		ListenerManager.groupListeners.handle(listener -> listener.onMemberLeave(groupModel, myIdentity, identities.length));
		ListenerManager.groupListeners.handle(listener -> listener.onLeave(groupModel));
		updateAllowedCallParticipants(groupModel);

		return true;
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

	private boolean remove(@NonNull final GroupModel groupModel, boolean silent, boolean forceRemove) {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				// cannot assign ballot service fixed in the constructor because of circular dependency
				ThreemaApplication.getServiceManager().getBallotService().remove(createReceiver(groupModel));
			} catch (MasterKeyLockedException | FileSystemNotPresentException | NoIdentityException e) {
				logger.error("Exception removing ballot models", e);
				return false;
			}
		}
		else {
			logger.error("Missing serviceManager, cannot delete ballot models for group");
			return false;
		}

		final GroupMessagePendingMessageIdModelFactory groupPendingMessageIdModelFactory = this.databaseServiceNew.getGroupMessagePendingMessageIdModelFactory();
		for(GroupMessageModel messageModel: this.databaseServiceNew.getGroupMessageModelFactory().getByGroupIdUnsorted(groupModel.getId())) {
			//remove all message identity models
			groupPendingMessageIdModelFactory.delete(messageModel.getId());

			//remove all files
			this.fileService.removeMessageFiles(messageModel, true);
		}

		// remove all group invite links and requests
		final GroupInviteModelFactory groupInviteModelFactory = this.databaseServiceNew.getGroupInviteModelFactory();
		final IncomingGroupJoinRequestModelFactory incomingGroupJoinRequestModelFactory = this.databaseServiceNew.getIncomingGroupJoinRequestModelFactory();
		for (GroupInviteModel groupInviteModel : groupInviteModelFactory.getByGroupApiId(groupModel.getApiGroupId())) {
			incomingGroupJoinRequestModelFactory.deleteAllForGroupInvite(groupInviteModel.getId());
			groupInviteModelFactory.delete(groupInviteModel);
		}

		//now remove all message models!
		this.databaseServiceNew.getGroupMessageModelFactory().deleteByGroupId(groupModel.getId());

		//remove avatar
		this.fileService.removeGroupAvatar(groupModel);
		this.avatarCacheService.reset(groupModel);

		// remove wallpaper and stuff
		String uniqueIdString = getUniqueIdString(groupModel);
		this.wallpaperService.removeWallpaper(uniqueIdString);
		this.ringtoneService.removeCustomRingtone(uniqueIdString);
		this.mutedChatsListService.remove(uniqueIdString);
		this.hiddenChatsListService.remove(uniqueIdString);
		ShortcutUtil.deleteShareTargetShortcut(uniqueIdString);
		ShortcutUtil.deletePinnedShortcut(uniqueIdString);

		groupModel.setDeleted(true);

		save(groupModel);

		this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupId(groupModel.getId());

		if (forceRemove) {
			this.databaseServiceNew.getGroupModelFactory().delete(groupModel);

			synchronized (this.groupModelCache) {
				this.groupModelCache.remove(groupModel.getId());
			}
		}

		this.resetIdentityCache(groupModel.getId());

		if(!silent) {
			ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
				@Override
				public void handle(GroupListener listener) {
					listener.onRemove(groupModel);
				}
			});
		}

		return true;
	}

	@Override
	public void removeAll() {
		for(GroupModel g: this.getAll()) {
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
	public CommonGroupReceiveStepsResult runCommonGroupReceiveSteps(@NonNull AbstractGroupMessage message) {
		// 1. Look up the group
		final GroupModel groupModel = getByGroupMessage(message);

		// 2. Check if the group could be found
		if (groupModel == null) {
			if (TestUtil.compare(message.getGroupCreator(), userService.getIdentity())) {
				// 2.1 If the user is the creator of the group (as alleged by received message),
				// discard the received message and abort these steps
				logger.info("Could not find group with me as creator");
				return CommonGroupReceiveStepsResult.DISCARD_MESSAGE;
			}
			// 2.2 Send a group-sync-request to the group creator and cache the received message for
			// processing after receiving the group-sync.
			boolean syncSent = requestSync(message);
			if (!syncSent) {
				logger.warn("Got message for an unknown group and sync could not be sent");
				return CommonGroupReceiveStepsResult.DISCARD_MESSAGE;
			}
			return CommonGroupReceiveStepsResult.SYNC_REQUEST_SENT;
		}

		// 3. Check if the group is left
		if (!isGroupMember(groupModel)) {
			if (isGroupCreator(groupModel)) {
				// 3.1 If the user is the creator, send a group-setup with an empty
				// members list back to the sender and discard the received message.
				logger.info("Got a message in a left group where I am the creator");
				sendEmptyGroupSetup(groupModel, message.getFromIdentity());
			} else {
				// 3.2 Send a group leave to the sender and discard the received message
				logger.info("Got a message in a left group");
				sendLeaveToSender(message);
			}
			return CommonGroupReceiveStepsResult.DISCARD_MESSAGE;
		}

		// 4. If the sender is not a member of the group and the user is the creator of the group,
		// send a group-setup with an empty members list back to the sender and discard the received
		// message.
		if (!isGroupMember(groupModel, message.getFromIdentity())) {
			logger.info("Got a message in a group from a sender that is not a member");
			if (isGroupCreator(groupModel)) {
				sendEmptyGroupSetup(groupModel, message.getFromIdentity());
			}
			return CommonGroupReceiveStepsResult.DISCARD_MESSAGE;
		}

		return CommonGroupReceiveStepsResult.SUCCESS;
	}

	/**
	 * Send a group sync request for the group of the given message. The sync is only sent, if there
	 * was no sync request sent in the last week with this method to this group. Sync requests that
	 * are sent by using {@link #requestSync(String, GroupId)} are not considered. If the sync
	 * request should be sent in any case, then use {@link #requestSync(String, GroupId)}.
	 *
	 * @param msg the message that identifies the group
	 * @return true if the request has been sent, false otherwise
	 */
	private boolean requestSync(AbstractGroupMessage msg) {
		if (msg != null) {

			// Do not send a request to myself
			if (TestUtil.compare(msg.getGroupCreator(), this.userService.getIdentity())) {
				return false;
			}

			try {
				GroupRequestSyncLogModelFactory groupRequestSyncLogModelFactory = this.databaseServiceNew.getGroupRequestSyncLogModelFactory();
				GroupRequestSyncLogModel model = groupRequestSyncLogModelFactory.get(msg.getApiGroupId().toString(),
						msg.getGroupCreator());

				// Send a request sync if the old request sync older than one week or null
				if (model == null
						|| model.getLastRequest() == null
						|| model.getLastRequest().getTime() < (System.currentTimeMillis() - DateUtils.WEEK_IN_MILLIS)) {
					// Send a request sync to the creator
					boolean requestSent = requestSync(msg.getGroupCreator(), msg.getApiGroupId());

					if (requestSent) {
						if (model == null) {
							GroupRequestSyncLogModel newModel = new GroupRequestSyncLogModel();
							newModel.setAPIGroupId(msg.getApiGroupId().toString(), msg.getGroupCreator());
							newModel.setCount(1);
							newModel.setLastRequest(new Date());

							groupRequestSyncLogModelFactory.create(newModel);
						} else {
							model.setLastRequest(new Date());
							model.setCount(model.getCount() + 1);

							groupRequestSyncLogModelFactory.update(model);
						}
					}
				} else {
					logger.info("Do not send request sync to group creator {}: last sync request was at {}", msg.getGroupCreator(), model.getLastRequest());
				}

				synchronized (this.pendingGroupMessages) {
					if(Functional.select(this.pendingGroupMessages, new IPredicateNonNull<AbstractGroupMessage>() {
						@Override
						public boolean apply(@NonNull AbstractGroupMessage m) {
							return m.getMessageId().toString().equals(m.getMessageId().toString());
						}
					}) == null) {
						this.pendingGroupMessages.add(msg);
					}
				}

				return true;
			} catch (ThreemaException e) {
				logger.error("Exception", e);
			}

		}
		return false;
	}

	@Override
	public boolean requestSync(@NonNull String groupCreator, @NonNull GroupId groupId) throws ThreemaException {
		return this.groupMessagingService.sendMessage(
			groupId,
			groupCreator,
			new String[] { groupCreator },
			messageId -> {
				GroupRequestSyncMessage groupRequestSyncMessage = new GroupRequestSyncMessage();
				groupRequestSyncMessage.setMessageId(messageId);
				return groupRequestSyncMessage;
			},
			null
		) == 1;
	}

	private void sendLeaveToSender(AbstractGroupMessage msg) {
		if (msg != null) {
			try {
				this.groupMessagingService.sendMessage(
					msg.getApiGroupId(),
					msg.getGroupCreator(),
					new String[]{msg.getFromIdentity()},
					messageId -> {
						final GroupLeaveMessage groupLeaveMessage = new GroupLeaveMessage();
						groupLeaveMessage.setMessageId(messageId);
						return groupLeaveMessage;
					},
					null
				);
			} catch (ThreemaException e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	@Nullable
	public GroupModel getByApiGroupIdAndCreator(@NonNull GroupId apiGroupId, @NonNull String creatorIdentity) {
		synchronized (this.groupModelCache) {
			GroupModel model = Functional.select(this.groupModelCache, type -> apiGroupId.toString().equals(type.getApiGroupId().toString()) && creatorIdentity.equals(type.getCreatorIdentity()));

			if(model == null) {
				model = this.databaseServiceNew.getGroupModelFactory().getByApiGroupIdAndCreator(
					apiGroupId.toString(),
					creatorIdentity
				);

				if (model != null) {
					return this.cache(model);
				}
			}
			else {
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
			if(existingGroupModel != null) {
				return existingGroupModel;
			}
			return this.cache(this.databaseServiceNew.getGroupModelFactory().getById(groupId));
		}
	}

	@NonNull
	@Override
	public GroupModel createGroupFromLocal(
		@NonNull String name,
		@NonNull String[] groupMemberIdentities,
		@Nullable Bitmap picture
	) throws Exception {
		if (AppRestrictionUtil.isCreateGroupDisabled(ThreemaApplication.getAppContext())) {
			throw new PolicyViolationException();
		}

		GroupModel model = this.createGroupFromLocal(name, groupMemberIdentities);

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
		@NonNull final String[] groupMemberIdentities
	) throws ThreemaException, PolicyViolationException {
		if (AppRestrictionUtil.isCreateGroupDisabled(ThreemaApplication.getAppContext())) {
			throw new PolicyViolationException();
		}

		final String creatorIdentity = userService.getIdentity();

		// Note: don't set the group name in the model here, otherwise no group name message is sent
		// because the group name looks up to date in updateGroupNameFromLocal
		final GroupModel groupModel = new GroupModel();
		String randomId = UUID.randomUUID().toString();
		GroupId id = new GroupId(Utils.hexStringToByteArray(randomId.substring(randomId.length() - (ProtocolDefines.GROUP_ID_LEN * 2))));
		groupModel
				.setApiGroupId(id)
				.setCreatorIdentity(creatorIdentity)
				.setCreatedAt(new Date())
				.setSynchronizedAt(new Date());

		this.databaseServiceNew.getGroupModelFactory().create(groupModel);
		this.cache(groupModel);

		this.addMemberToGroup(groupModel, creatorIdentity);

		for (String identity : groupMemberIdentities) {
			this.addMemberToGroup(groupModel, identity);
		}

		for (String memberIdentity : groupMemberIdentities) {
			ListenerManager.groupListeners.handle(listener ->
				listener.onNewMember(groupModel, memberIdentity, 0)
			);
		}

		ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
			@Override
			public void handle(GroupListener listener) {
				listener.onCreate(groupModel);
			}
		});

		ListenerManager.groupListeners.handle(listener -> listener.onGroupStateChanged(groupModel, UNDEFINED, getGroupState(groupModel)));

		//send event to server
		sendGroupSetup(groupModel, groupMemberIdentities, groupMemberIdentities);

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
					.setActive(true)
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
		List<String> newMembers = new ArrayList<>();
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
		String[] newMemberIdentities = newMembers.toArray(new String[0]);
		String[] updatedGroupMemberIdentities = updatedGroupMembers.toArray(new String[0]);

		// List of all kicked identities
		List<String> kickedGroupMembers = new ArrayList<>(existingMembers);
		kickedGroupMembers.removeAll(updatedGroupMembers);
		String[] kickedGroupMemberIdentities = kickedGroupMembers.toArray(new String[0]);

		int previousMemberCount = countMembers(groupModel);

		// Remove the kicked members from the database
		for (final String kickedIdentity: kickedGroupMembers) {
			logger.debug("Remove member {} from group", kickedIdentity);
			removeMemberFromGroup(groupModel, kickedIdentity);

			ListenerManager.groupListeners.handle(listener ->
				listener.onMemberKicked(groupModel, kickedIdentity, previousMemberCount)
			);
		}

		// Add new members to the database
		for (String newMember: newMembers) {
			logger.debug("Add member {} to group", newMember);
			this.addMemberToGroup(groupModel, newMember);
			ListenerManager.groupListeners.handle(listener ->
				listener.onNewMember(groupModel, newMember, previousMemberCount)
			);
		}

		boolean hasNewMembers = !newMembers.isEmpty();
		boolean hasKickedMembers = !kickedGroupMembers.isEmpty();
		boolean hasMemberChanges = hasNewMembers || hasKickedMembers;

		if (hasMemberChanges) {
			resetIdentityCache(groupModel.getId());
		}

		// If the user wants to delete the group picture, we send a delete message to the removed
		// members. Note that the delete group picture message is sent to the updated group after
		// the new group setup message.
		if (removePicture && hasKickedMembers) {
			sendGroupDeletePhotoToMembers(groupModel, kickedGroupMemberIdentities);
		}

		// Send a setup message with an empty member list to the removed members
		if (hasKickedMembers) {
			sendEmptyGroupSetup(groupModel, kickedGroupMemberIdentities);
		}

		// Send a setup message with the updated group member list to all (updated) group members
		if (hasMemberChanges) {
			sendGroupSetup(groupModel, updatedGroupMemberIdentities, updatedGroupMemberIdentities);
		}

		if (name != null) {
			// Rename the group (for all members) if the group name has changed. This includes the
			// new members of the group.
			updateGroupNameFromLocal(groupModel, name);
		} else if (hasNewMembers) {
			// Send rename message to all new group members, so that they receive the group name,
			// even if the group name did not change.
			sendGroupRenameToIdentitiesIfOwner(groupModel, newMemberIdentities);
		}

		if (removePicture || picture != null) {
			// If there is a group picture change (either remove, or a new picture), update it. Note
			// that this includes the new members.
			updateGroupPictureFromLocal(groupModel, picture);
		} else if (hasNewMembers) {
			// If there are new members, we need to send a set profile picture or delete profile
			// picture message - depending on whether there is a group picture set or not. Note that
			// this must be done according to the protocol.
			if (fileService.hasGroupAvatarFile(groupModel)) {
				GroupPhotoUploadResult result = uploadGroupPhoto(fileService.getGroupAvatar(groupModel));
				sendGroupPhotoToMembers(groupModel, newMemberIdentities, result);
			} else {
				sendGroupDeletePhotoToMembers(groupModel, newMemberIdentities);
			}
		}

		if (groupDesc != null) {
			this.changeGroupDesc(groupModel, groupDesc);
		}

		sendGroupCallStart(groupModel, newMembers);

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
	 * Update the group name. Besides updating the database and group model, this fires the group
	 * listeners and sends the group name message to the members. If the new name is the same as the
	 * old name, nothing is done.
	 *
	 * @param groupModel the group model that is changed
	 * @param newName    the new group name
	 * @return true if the group name has changed or if it is the same as the old name, false if an
	 * error occurred
	 */
	private boolean updateGroupNameFromLocal(@NonNull GroupModel groupModel, @NonNull String newName) {
		if (!isGroupCreator(groupModel)) {
			logger.error("Cannot rename group where the user is not the creator");
			return false;
		}

		String oldName = groupModel.getName();
		oldName = oldName != null ? oldName : "";

		if (oldName.equals(newName)) {
			return true;
		}

		// Update and save the group model
		groupModel.setName(newName);
		this.save(groupModel);

		// Fire the group rename listener
		ListenerManager.groupListeners.handle(listener -> listener.onRename(groupModel));

		// delete share target shortcut as name is different
		ShortcutUtil.deleteShareTargetShortcut(getUniqueIdString(groupModel.getId()));

		// Send rename message to group members if group owner
		try {
			String[] groupIdentities = getGroupIdentities(groupModel);
			return sendGroupRenameToIdentitiesIfOwner(groupModel, groupIdentities)
				== groupIdentities.length - 1; // The creator should not receive the message
		} catch (ThreemaException e) {
			logger.error("Could not send group name message", e);
			return false;
		}
	}

	/**
	 * Update the group picture. If the picture is null, then the group picture is deleted. If the
	 * old picture and the new picture is the same, then nothing is done. This method also fires the
	 * the group listeners and sends the message to the group members.
	 *
	 * @param groupModel the group model
	 * @param picture    the new picture
	 * @return true if the picture has changed successfully or it is the same as the old picture,
	 * false if an error occurred
	 */
	private boolean updateGroupPictureFromLocal(@NonNull GroupModel groupModel, @Nullable Bitmap picture) {
		if (!isGroupCreator(groupModel)) {
			logger.error("Cannot rename group where the user is not the creator");
			return false;
		}

		try {
			if (picture == null && fileService.hasGroupAvatarFile(groupModel)) {
				return deleteGroupPictureFromLocal(groupModel);
			} else if (picture != null) {
				Bitmap existingGroupPicture = fileService.getGroupAvatar(groupModel);
				if (!picture.sameAs(existingGroupPicture)) {
					return setGroupPictureFromLocal(groupModel, picture);
				}
			}
		} catch (Exception e) {
			logger.error("Failed to update group picture", e);
		}

		return true;
	}

	/**
	 * Update the group picture locally. Upload the group picture and send it to the members. This
	 * also fires the group listeners.
	 *
	 * @param groupModel the group model
	 * @param picture    the group picture
	 * @return true if the group picture has been set successfully, false otherwise
	 * @throws Exception if uploading, sending or saving the group picture failed
	 */
	private boolean setGroupPictureFromLocal(@NonNull GroupModel groupModel, @NonNull Bitmap picture) throws Exception {
		GroupPhotoUploadResult result = uploadGroupPhoto(picture);

		// Save the image
		boolean success = fileService.writeGroupAvatar(groupModel, result.bitmapArray);

		if (success) {
			// Reset the avatar cache entry
			avatarCacheService.reset(groupModel);

			// Fire listeners
			ListenerManager.groupListeners.handle(listener -> listener.onUpdatePhoto(groupModel));

			// Send to the new blob to the users
			return sendGroupPhotoToMembers(groupModel, getGroupIdentities(groupModel), result);
		}
		return false;
	}

	/**
	 * Delete the group picture. This also sends a group delete photo message. Note that this is
	 * also done, if the group currently does not have a group picture. The group listeners are only
	 * fired, if there was a group picture previously.
	 *
	 * @param groupModel the group that is updated
	 * @return true if the profile picture was successfully deleted, false otherwise
	 */
	private boolean deleteGroupPictureFromLocal(@NonNull GroupModel groupModel) {
		boolean hadGroupPicture = fileService.hasGroupAvatarFile(groupModel);

		fileService.removeGroupAvatar(groupModel);

		avatarCacheService.reset(groupModel);

		if (hadGroupPicture) {
			ListenerManager.groupListeners.handle(listener -> listener.onUpdatePhoto(groupModel));
		}

		return sendGroupDeletePhotoToMembers(groupModel, getGroupIdentities(groupModel));
	}

	private void sendGroupCallStart(@NonNull GroupModel groupModel, @NonNull List<String> newMemberIdentities) {
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
			groupCallManager.sendGroupCallStartToNewMembers(groupModel, newMemberIdentities);
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

	private int sendGroupRenameToIdentitiesIfOwner(@NonNull GroupModel groupModel, @NonNull String[] identities) throws ThreemaException {
		if (!isGroupCreator(groupModel)) {
			// Don't send the group rename if the user is not the owner
			return -1;
		}

		final String groupName = groupModel.getName() != null ? groupModel.getName() : "";

		return groupMessagingService.sendMessage(groupModel, identities, messageId -> {
			final GroupRenameMessage rename = new GroupRenameMessage();
			rename.setMessageId(messageId);
			rename.setGroupName(groupName);
			return rename;
		});
	}

	// on Update new group desc
	private void changeGroupDesc(final GroupModel group, final String newGroupDesc) {
		group.setGroupDesc(newGroupDesc);
		this.save(group);
	}

	private GroupPhotoUploadResult uploadGroupPhoto(Bitmap picture) throws IOException, ThreemaException {
		GroupPhotoUploadResult result = new GroupPhotoUploadResult();
		SecureRandom rnd = new SecureRandom();
		result.encryptionKey = new byte[NaCl.SYMMKEYBYTES];
		rnd.nextBytes(result.encryptionKey);

		result.bitmapArray = BitmapUtil.bitmapToJpegByteArray(picture);
		byte[] thumbnailBoxed = NaCl.symmetricEncryptData(result.bitmapArray, result.encryptionKey, ProtocolDefines.GROUP_PHOTO_NONCE);
		BlobUploader blobUploaderThumbnail = this.apiService.createUploader(thumbnailBoxed);
		result.blobId = blobUploaderThumbnail.upload();
		result.size = thumbnailBoxed.length;

		return result;
	}

	private boolean sendGroupPhotoToMembers(
		@NonNull GroupModel groupModel,
		@NonNull String[] identities,
		@NonNull final GroupPhotoUploadResult uploadResult
	) throws ThreemaException {
		return this.groupMessagingService.sendMessage(groupModel, identities, messageId -> {
			final GroupSetPhotoMessage msg = new GroupSetPhotoMessage();
			msg.setMessageId(messageId);
			msg.setBlobId(uploadResult.blobId);
			msg.setEncryptionKey(uploadResult.encryptionKey);
			msg.setSize(uploadResult.size);
			return msg;
		}) == identities.length;
	}

	private boolean sendGroupDeletePhotoToMembers(@NonNull GroupModel groupModel, @NonNull String[] identities) {
		try {
			this.groupMessagingService.sendMessage(
				groupModel,
				identities,
				messageId -> {
					GroupDeletePhotoMessage groupDeletePhotoMessage = new GroupDeletePhotoMessage();
					groupDeletePhotoMessage.setMessageId(messageId);
					return groupDeletePhotoMessage;
				}
			);
		} catch (ThreemaException e) {
			logger.error("Could not send group delete photo message", e);
			return false;
		}
		return true;
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
	 * @param identity the identity of the member
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
			this.groupMessagingService,
			this.contactService
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

	@Override
	public boolean sendEmptyGroupSetup(
		@NonNull GroupModel groupModel,
		@NonNull String receiverIdentity
	) {
		return sendEmptyGroupSetup(groupModel, new String[]{receiverIdentity});
	}

	private boolean sendEmptyGroupSetup(
		@NonNull GroupModel groupModel,
		@NonNull String[] receiverIdentities
	) {
		return sendGroupSetup(groupModel, receiverIdentities, new String[0]);
	}

	private boolean sendGroupSetup(
		@NonNull GroupModel groupModel,
		@NonNull String[] receiverIdentities,
		@NonNull String[] memberIdentities
	) {
		try {
			this.groupMessagingService.sendMessage(
				groupModel,
				receiverIdentities,
				messageId -> {
					GroupCreateMessage groupCreateMessage = new GroupCreateMessage();
					groupCreateMessage.setMessageId(messageId);
					groupCreateMessage.setMembers(memberIdentities);
					return groupCreateMessage;
				}
			);
			return true;
		} catch (ThreemaException e) {
			logger.error("Exception", e);
		}
		return false;
	}

	@Override
	public boolean sendSync(GroupModel groupModel) {
		// Send event to clients
		final String[] groupMemberIdentities =  this.getGroupIdentities(groupModel);

		// Send to ALL members!
		return this.sendSync(groupModel, groupMemberIdentities);
	}

	@Override
	public boolean sendSync(@NonNull final GroupModel groupModel, @NonNull final String[] receiverIdentities) {
		String creatorIdentity = groupModel.getCreatorIdentity();
		List<String> receivers = new LinkedList<>();
		for (String identity : receiverIdentities) {
			if (!creatorIdentity.equals(identity)) {
				receivers.add(identity);
			}
		}

		String[] finalReceiverIdentities;
		if (receivers.size() != receiverIdentities.length) {
			finalReceiverIdentities = new String[receivers.size()];
			receivers.toArray(finalReceiverIdentities);
		} else {
			finalReceiverIdentities = receiverIdentities;
		}

		boolean success = false;

		try {
			success = sendGroupSetup(groupModel, finalReceiverIdentities, getGroupIdentities(groupModel));

			success &= sendGroupRenameToIdentitiesIfOwner(groupModel, finalReceiverIdentities)
				== finalReceiverIdentities.length;

			if (fileService.hasGroupAvatarFile(groupModel)) {
				GroupPhotoUploadResult result = uploadGroupPhoto(fileService.getGroupAvatar(groupModel));
				success &= sendGroupPhotoToMembers(groupModel, finalReceiverIdentities, result);
			} else {
				success &= sendGroupDeletePhotoToMembers(groupModel, finalReceiverIdentities);
			}

			// Update sync date if sync was successful
			if (success) {
				groupModel.setSynchronizedAt(new Date());
				this.save(groupModel);
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return success;
	}

	@Nullable
	private byte[] getGroupAvatarBytes(@Nullable GroupModel groupModel) {
		if (groupModel == null) {
			return null;
		}
		try (InputStream groupAvatarInputStream = fileService.getGroupAvatarStream(groupModel)) {
			return IOUtils.toByteArray(groupAvatarInputStream);
		} catch (Exception exception) {
			logger.error("Could not get group avatar", exception);
		}
		return null;
	}

	@Override
	@NonNull
	public List<GroupModel> getGroupsByIdentity(@Nullable String identity) {
		List<GroupModel> groupModels = new ArrayList<>();
		if(TestUtil.empty(identity) || !TestUtil.required(this.databaseServiceNew, this.groupModelCache)) {
			return groupModels;
		}

		identity = identity.toUpperCase();

		List<Integer> res = this.databaseServiceNew.getGroupMemberModelFactory().getGroupIdsByIdentity(
				identity);

		List<Integer> groupIds = new ArrayList<>();
		synchronized (this.groupModelCache) {
			for (int id : res) {
				GroupModel existingGroupModel = this.groupModelCache.get(id);
				if(existingGroupModel == null) {
					groupIds.add(id);
				}
				else {
					groupModels.add(existingGroupModel);
				}
			}
		}

		if (groupIds.size() > 0) {
			List<GroupModel> groups = this.databaseServiceNew.getGroupModelFactory().getInId(
					groupIds);

			for(GroupModel gm: groups) {
				groupModels.add(this.cache(gm));
			}
		}

		return groupModels;
	}

	@Override
	public GroupAccessModel getAccess(@Nullable GroupModel groupModel, boolean allowEmpty) {
		GroupAccessModel groupAccessModel = new GroupAccessModel();
		if(groupModel != null) {
			// Don't allow to send and receive messages in left groups
			if(!isGroupMember(groupModel)) {
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

			ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
				@Override
				public void handle(GroupListener listener) {
					listener.onUpdate(groupModel);
				}
			});
		}
	}

	@Override
	public boolean isFull(final GroupModel groupModel) {
		return this.countMembers(groupModel) >= BuildConfig.MAX_GROUP_SIZE;
	}

	private void save(GroupModel model) {
		this.databaseServiceNew.getGroupModelFactory().createOrUpdate(
				model
		);
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
}

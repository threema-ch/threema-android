/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.GroupDetailActivity;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.exceptions.InvalidEntryException;
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
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.blob.BlobLoader;
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
	private final IdListService blackListService;
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
			RingtoneService ringtoneService,
			IdListService blackListService) {
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
		this.blackListService = blackListService;

		this.groupModelCache = cacheService.getGroupModelCache();
		this.groupIdentityCache = cacheService.getGroupIdentityCache();
		this.groupMemberColorCache = cacheService.getGroupMemberColorCache();
	}

	@Override
	public List<GroupModel> getAll() {
		return this.getAll(null);
	}

	@Override
	public List<GroupModel> getAll(GroupFilter filter) {
		List<GroupModel> res = new ArrayList<>(this.databaseServiceNew.getGroupModelFactory().filter(filter));

		if (filter != null && !filter.withDeserted()) {
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
	public boolean removeAllMembersAndLeave(final GroupModel groupModel) {
		String[] identities = new String[]{groupModel.getCreatorIdentity()};

		try {
			updateGroup(groupModel, null, null, identities, null,false);
			if (leaveGroup(groupModel)) {
				ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
					@Override
					public void handle(GroupListener listener) {
						listener.onLeave(groupModel);
					}
				});
				return true;
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return false;
	}

	@Override
	public boolean leaveGroup(final GroupModel groupModel) {
		if(groupModel == null) {
			return false;
		}

		// Get current display name (#ANDR-744)
		String displayName = createReceiver(groupModel).getDisplayName();

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

		this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupId(groupModel.getId());

		ShortcutUtil.deleteShareTargetShortcut(getUniqueIdString(groupModel));
		ShortcutUtil.deletePinnedShortcut(getUniqueIdString(groupModel));

		// save with "old" name
		groupModel.setName(displayName);
		this.save(groupModel);

		//reset cache
		this.resetIdentityCache(groupModel.getId());

		//fire kicked
		ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
			@Override
			public void handle(GroupListener listener) {
				listener.onMemberKicked(groupModel, userService.getIdentity(), identities.length);
			}
		});

		return true;
	}

	@Override
	public boolean remove(GroupModel groupModel) {
		return this.remove(groupModel, false);
	}

	@Override
	public boolean remove(final GroupModel groupModel, boolean silent) {
		ServiceManager serviceManager= ThreemaApplication.getServiceManager();
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

		this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupId(groupModel.getId());

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
		this.databaseServiceNew.getGroupModelFactory().delete(
				groupModel
		);

		synchronized (this.groupModelCache) {
			this.groupModelCache.remove(groupModel.getId());
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
			this.remove(g, true);
		}
		//remove last request sync table

		this.databaseServiceNew.getGroupRequestSyncLogModelFactory().deleteAll();
	}


	@Override
	public GroupModel getGroup(final AbstractGroupMessage message) {
		GroupModel model;

		try {
			model = this.getByAbstractGroupMessage(message);
		} catch (SQLException e) {
			logger.error("Exception", e);
			model = null;
		}

		return model;
	}


	@Override
	public boolean requestSync(AbstractGroupMessage msg, boolean leaveIfMine) {
		if(msg != null) {

			//do not send a request to myself
			if(TestUtil.compare(msg.getGroupCreator(), this.userService.getIdentity())) {
				if(leaveIfMine) {
					//auto leave
					this.sendLeave(msg);
				}
				else {
					return false;
				}
			}

			try {
				GroupRequestSyncLogModelFactory groupRequestSyncLogModelFactory = this.databaseServiceNew.getGroupRequestSyncLogModelFactory();
				GroupRequestSyncLogModel model = groupRequestSyncLogModelFactory.get(msg.getApiGroupId().toString(),
						msg.getGroupCreator());

				//send a request sync if the old request sync older than one week or NULL
				if(model == null
						|| model.getLastRequest() == null
						|| model.getLastRequest().getTime() < (System.currentTimeMillis() - DateUtils.WEEK_IN_MILLIS)) {
					logger.debug("send request sync to group creator [" + msg.getGroupCreator() + "]");
					//send a request sync to the creator!!
					int messageCount = requestSync(msg.getGroupCreator(), msg.getApiGroupId());

					if(messageCount == 1) {
						if(model == null) {
							model = new GroupRequestSyncLogModel();
							model.setAPIGroupId(msg.getApiGroupId().toString(), msg.getGroupCreator());
							model.setCount(1);
							model.setLastRequest(new Date());

							groupRequestSyncLogModelFactory.create(
									model
							);
						}
						else {
							model.setLastRequest(new Date());
							model.setCount(model.getCount() + 1);

							groupRequestSyncLogModelFactory.update(
									model
							);
						}
					}
				}
				else {
					logger.debug("do not send request sync to group creator [" + msg.getGroupCreator() + "]");
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
	public int requestSync(String groupCreator, GroupId groupId) throws ThreemaException {
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
		);
	}

	@Override
	public boolean sendLeave(AbstractGroupMessage msg) {
		if(msg != null) {
			try {
				//send a leave to the creator!!
				this.groupMessagingService.sendMessage(
					msg.getApiGroupId(),
					msg.getGroupCreator(),
					new String[]{ msg.getFromIdentity(), msg.getGroupCreator() },
					messageId -> {
						final GroupLeaveMessage groupLeaveMessage = new GroupLeaveMessage();
						groupLeaveMessage.setMessageId(messageId);
						return groupLeaveMessage;
					},
					null
				);

				return true;
			} catch (ThreemaException e) {
				logger.error("Exception", e);
			}

		}
		return false;
	}

	@Override
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

	private GroupModel getByAbstractGroupMessage(final AbstractGroupMessage message) throws SQLException {
		return getByApiGroupIdAndCreator(message.getApiGroupId(), message.getGroupCreator());
	}

	@Override
	public boolean removeMemberFromGroup(GroupLeaveMessage msg) {
		try {
			GroupModel model = this.getByAbstractGroupMessage(msg);

			if(model != null) {
				@GroupState int groupState = getGroupState(model);

				if (this.removeMemberFromGroup(model, msg.getFromIdentity())) {
					ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
						@Override
						public void handle(GroupListener listener) {
							listener.onGroupStateChanged(model, groupState, getGroupState(model));
						}
					});
				}

				return true;
			}
			else {
				//return true to "kill" message from server!
				return true;
			}
		} catch (SQLException e) {
			logger.error("Exception", e);
		}

		return false;
	}

	private boolean removeMemberFromGroup(final GroupModel group, final String identity) {
		final int previousMemberCount = countMembers(group);

		if(this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupIdAndIdentity(
				group.getId(),
				identity
		)> 0) {
			this.resetIdentityCache(group.getId());

			ListenerManager.groupListeners.handle(listener -> listener.onMemberLeave(group, identity, previousMemberCount));
			return true;
		}

		return false;
	}

	@Override
	public Intent getGroupEditIntent(@NonNull GroupModel groupModel, @NonNull Activity activity) {
		return new Intent(activity, GroupDetailActivity.class);
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


	@Override
	public GroupCreateMessageResult processGroupCreateMessage(final GroupCreateMessage groupCreateMessage) {
		final GroupCreateMessageResult result = new GroupCreateMessageResult();
		result.success = false;

		boolean isNewGroup;
		final int previousMemberCount;

		//check if i am in group
		boolean iAmAGroupMember = Functional.select(groupCreateMessage.getMembers(), new IPredicateNonNull<String>() {
			@Override
			public boolean apply(@NonNull String identity) {
				return TestUtil.compare(identity, userService.getIdentity());
			}
		}, null) != null;

		try {
			result.groupModel = this.getByAbstractGroupMessage(groupCreateMessage);
			previousMemberCount = result.groupModel != null ? countMembers(result.groupModel) : 0;
			isNewGroup = result.groupModel == null;

		} catch (SQLException e) {
			logger.error("Exception", e);
			return null;
		}

		@GroupState int groupState = getGroupState(result.groupModel);

		if (isNewGroup && this.blackListService != null && this.blackListService.has(groupCreateMessage.getFromIdentity())) {
			logger.info("GroupCreateMessage {}: Received group create from blocked ID. Sending leave.", groupCreateMessage.getMessageId());

			sendLeave(groupCreateMessage);

			result.success = true;
			return result;
		}

		if (!iAmAGroupMember) {
			if (isNewGroup) {
				// i'm not a member of this new group
				// ignore this groupCreate message
				result.success = true;
				result.groupModel = null;
			}
			else {
				// i was kicked out of group
				// remove all members
				this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupId(
						result.groupModel.getId());

				final GroupModel groupModel = result.groupModel;

				//reset result
				result.success = true;
				result.groupModel = null;

				//reset cache
				this.resetIdentityCache(groupModel.getId());

				//fire kicked
				ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
					@Override
					public void handle(GroupListener listener) {
						listener.onMemberKicked(groupModel, userService.getIdentity(), previousMemberCount);
					}
				});
			}

			ListenerManager.groupListeners.handle(listener -> listener.onGroupStateChanged(result.groupModel, groupState, getGroupState(result.groupModel)));

			return result;
		}

		if (result.groupModel == null) {
			result.groupModel = new GroupModel();
			result.groupModel
					.setApiGroupId(groupCreateMessage.getApiGroupId())
					.setCreatorIdentity(groupCreateMessage.getGroupCreator())
					.setCreatedAt(new Date());

			this.databaseServiceNew.getGroupModelFactory().create(result.groupModel);
			this.cache(result.groupModel);

		}
		else if (result.groupModel.isDeleted()) {
			result.groupModel.setDeleted(false);
			this.databaseServiceNew.getGroupModelFactory().update(result.groupModel);
			isNewGroup = true;
		}

		List<GroupMemberModel> localSavedGroupMembers = null;

		if(!isNewGroup) {
			//all saved members on database, excluded the group creator
			localSavedGroupMembers = Functional.filter(this.getGroupMembers(result.groupModel), new IPredicateNonNull<GroupMemberModel>() {
				@Override
				public boolean apply(@NonNull GroupMemberModel type) {
					return type != null
							&& !TestUtil.compare(type.getIdentity(), groupCreateMessage.getGroupCreator());
				}}
			);

			if (localSavedGroupMembers != null) {
				for (String identity : groupCreateMessage.getMembers()) {
					GroupMemberModel localSavedGroupMember = Functional.select(localSavedGroupMembers, new IPredicateNonNull<GroupMemberModel>() {
						@Override
						public boolean apply(@NonNull GroupMemberModel gm) {
							return gm != null && TestUtil.compare(gm.getIdentity(), identity);
						}
					});

					if (localSavedGroupMember != null) {
						//remove from list
						localSavedGroupMembers.remove(localSavedGroupMember);
					}
				}
			}
		}

		//add creator as member
		this.addMemberToGroup(result.groupModel, groupCreateMessage.getGroupCreator());
		this.addMembersToGroup(result.groupModel, groupCreateMessage.getMembers());

		//now remove all local saved members that not in the create message
		if(localSavedGroupMembers != null
				&& localSavedGroupMembers.size() > 0) {
			//remove ALL from database
			this.databaseServiceNew.getGroupMemberModelFactory().delete(
					localSavedGroupMembers);

			for(final GroupMemberModel groupMemberModel: localSavedGroupMembers) {
				ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
					@Override
					public void handle(GroupListener listener) {
						listener.onMemberKicked(result.groupModel, groupMemberModel.getIdentity(), previousMemberCount);
					}
				});
			}
		}

		//success!
		result.success = true;

		if(isNewGroup) {
			//only fire on new group event
			final GroupModel gm = result.groupModel;
			ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
				@Override
				public void handle(GroupListener listener) {
					listener.onCreate(gm);
				}
			});
		}

		ListenerManager.groupListeners.handle(listener -> listener.onGroupStateChanged(result.groupModel, groupState, getGroupState(result.groupModel)));

		return result;
	}

	@Override
	public GroupModel createGroup(String name, String[] groupMemberIdentities, Bitmap picture) throws Exception {
		if (AppRestrictionUtil.isCreateGroupDisabled(ThreemaApplication.getAppContext())) {
			throw new PolicyViolationException();
		}

		GroupPhotoUploadResult uploadPhotoResult = null;
		if(picture != null) {
			uploadPhotoResult = this.uploadGroupPhoto(picture);
		}

		GroupModel model = this.createGroup(name, groupMemberIdentities);

		if (uploadPhotoResult != null) {
			this.updateGroupPhoto(model, uploadPhotoResult);
		}
		return model;
	}

	private GroupModel createGroup(String name, final String[] groupMemberIdentities) throws ThreemaException, PolicyViolationException {
		if (AppRestrictionUtil.isCreateGroupDisabled(ThreemaApplication.getAppContext())) {
			throw new PolicyViolationException();
		}

		final GroupModel groupModel = new GroupModel();
		String randomId = UUID.randomUUID().toString();
		GroupId id = new GroupId(Utils.hexStringToByteArray(randomId.substring(randomId.length() - (ProtocolDefines.GROUP_ID_LEN * 2))));
		groupModel
				.setApiGroupId(id)
				.setCreatorIdentity(this.userService.getIdentity())
				.setName(name)
				.setCreatedAt(new Date())
				.setSynchronizedAt(new Date());

		this.databaseServiceNew.getGroupModelFactory().create(groupModel);
		this.cache(groupModel);

		for (String identity : groupMemberIdentities) {
			this.addMemberToGroup(groupModel, identity);
		}

		//add creator to group
		this.addMemberToGroup(groupModel, groupModel.getCreatorIdentity());

		ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
			@Override
			public void handle(GroupListener listener) {
				listener.onCreate(groupModel);
			}
		});

		ListenerManager.groupListeners.handle(listener -> listener.onGroupStateChanged(groupModel, UNDEFINED, getGroupState(groupModel)));

		//send event to server
		this.groupMessagingService.sendMessage(groupModel, this.getGroupIdentities(groupModel), messageId -> {
			GroupCreateMessage groupCreateMessage = new GroupCreateMessage();
			groupCreateMessage.setMessageId(messageId);
			groupCreateMessage.setMembers(groupMemberIdentities);
			return groupCreateMessage;
		});

		if(groupModel.getName() != null && groupModel.getName().length() > 0) {
			this.renameGroup(groupModel, groupModel.getName());
		}

		return groupModel;
	}

	@Override
	public @Nullable Boolean addMemberToGroup(final GroupModel groupModel, final String identity) {
		GroupMemberModel m = this.getGroupMember(groupModel, identity);
		boolean isNewMember = m == null;
		final int previousMemberCount = countMembers(groupModel);

		//check if member already in group

		if(m == null) {
			//create a identity contact if not exist
			if (!this.userService.getIdentity().equals(identity) && this.contactService.getByIdentity(identity) == null) {
				if (!this.preferenceService.isBlockUnknown()) {
					try {
						this.contactService.createContactByIdentity(identity, true, true);
					} catch (InvalidEntryException | PolicyViolationException e) {
						return null;
					} catch (EntryAlreadyExistsException e) {
						// ignore
					}
				} else {
					return false;
				}
			}


			m = new GroupMemberModel();
		}

		m
				.setActive(true)
				.setGroupId(groupModel.getId())
				.setIdentity(identity);

		if(isNewMember) {
			this.databaseServiceNew.getGroupMemberModelFactory().create(m);
		}
		else {
			this.databaseServiceNew.getGroupMemberModelFactory().update(m);
		}

		this.resetIdentityCache(groupModel.getId());

		//fire new member event after the data are saved
		if(isNewMember) {
			ListenerManager.groupListeners.handle(
				listener -> listener.onNewMember(groupModel, identity, previousMemberCount)
			);
		}

		return isNewMember;
	}

	@Override
	public Boolean addMembersToGroup(@NonNull final GroupModel groupModel, @Nullable final String[] identities) {
		if (identities != null && identities.length > 0) {
			@GroupState int groupState = getGroupState(groupModel);

			ArrayList<String> newContacts = new ArrayList<>();
			ArrayList<String> newMembers = new ArrayList<>();

			int previousMemberCount = countMembers(groupModel);

			// check for new contacts, if necessary, create them
			if (!this.preferenceService.isBlockUnknown()) {
				for (String identity : identities) {
					if (!this.userService.getIdentity().equals(identity) && this.contactService.getByIdentity(identity) == null) {
						newContacts.add(identity);
					}
				}

				if (newContacts.size() > 0) {
					APIConnector apiConnector = ThreemaApplication.getServiceManager().getAPIConnector();

					ArrayList<APIConnector.FetchIdentityResult> results;
					try {
						results = apiConnector.fetchIdentities(newContacts);

						for (String identity: newContacts) {
							APIConnector.FetchIdentityResult result = apiConnector.getFetchResultByIdentity(results, identity);

							ContactModel contactModel;

							if (result != null) {
								contactModel = new ContactModel(result.identity, result.publicKey);
								contactModel.setVerificationLevel(contactService.getInitialVerificationLevel(contactModel));
								contactModel.setFeatureMask(result.featureMask);
								contactModel.setIdentityType(result.type);
								switch (result.state) {
									case IdentityState.ACTIVE:
										contactModel.setState(ContactModel.State.ACTIVE);
										break;
									case IdentityState.INACTIVE:
										contactModel.setState(ContactModel.State.INACTIVE);
										break;
									case IdentityState.INVALID:
										contactModel.setState(ContactModel.State.INVALID);
										break;
								}
								contactModel.setDateCreated(new Date());
								contactModel.setIsHidden(true);
							} else {
								// this is an invalid contact, as it was not returned by the call to fetchIdentities(newContacts) - fix it
								contactModel = this.contactService.getByIdentity(identity);
								if (contactModel != null) {
									contactModel.setState(ContactModel.State.INVALID);
								} else {
									continue;
								}
							}
							contactService.save(contactModel);
						}
					} catch (Exception e) {
						// no connection
						logger.error("Exception", e);
						return null;
					}
				}
			}

			// check for new members
			for (String identity: identities) {
				GroupMemberModel m = this.getGroupMember(groupModel, identity);
				if (m == null) {
					// this is a new member
					m = new GroupMemberModel();
					m.setActive(true)
						.setGroupId(groupModel.getId())
						.setIdentity(identity);
					this.databaseServiceNew.getGroupMemberModelFactory().create(m);

					newMembers.add(identity);
				} else {
					// this is an existing member
					m.setActive(true)
						.setGroupId(groupModel.getId())
						.setIdentity(identity);
					this.databaseServiceNew.getGroupMemberModelFactory().update(m);

				}
			}

			this.resetIdentityCache(groupModel.getId());

			//fire new member event after the data are saved
			ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
				@Override
				public void handle(GroupListener listener) {
					for (String identity: newMembers) {
						listener.onNewMember(groupModel, identity, previousMemberCount);
					}
				}
			});

			ListenerManager.groupListeners.handle(listener -> listener.onGroupStateChanged(groupModel, groupState, getGroupState(groupModel)));

			return true;
		}
		return false;
	}

	private int getGroupState(@Nullable GroupModel groupModel) {
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
		@Nullable Bitmap photo,
		boolean removePhoto
	) throws Exception {

		@GroupState int groupState = getGroupState(groupModel);

		//existing members
		logger.debug("Group Join Request: updateGroup");
		String[] existingMembers = this.getGroupIdentities(groupModel);

		//list with all (also kicked and added) members
		List<String> allInvolvedMembers = new LinkedList<>(Arrays.asList(existingMembers));

		//list of all kicked identities
		List<String> kickedGroupMemberIdentities = new ArrayList<>();

		//check new members
		List<String> newMembers = new ArrayList<>();
		if (groupMemberIdentities != null) {
			for (String identity : groupMemberIdentities) {
				if(this.getGroupMember(groupModel, identity) == null) {
					newMembers.add(identity);
					allInvolvedMembers.add(identity);
				}
			}
		}

		GroupPhotoUploadResult photoUploadResult = null;
		boolean isANewGroupPhoto = photo != null;

		if (removePhoto) {
			this.fileService.removeGroupAvatar(groupModel);
		} else {
			if (photo == null && !newMembers.isEmpty()) {
				//load existing picture
				photo = this.fileService.getGroupAvatar(groupModel);
			}

			if (photo != null) {
				//upload the picture if possible
				photoUploadResult = this.uploadGroupPhoto(photo);
			}
		}

		// add new members to group
		for (String newMember: newMembers) {
			logger.debug("add member {} to group", newMember);
			this.addMemberToGroup(groupModel, newMember);
		}

		//add creator to group
		this.addMemberToGroup(groupModel, groupModel.getCreatorIdentity());

		//now kick the members
		for(final String savedIdentity: existingMembers) {
			//if the identity NOT in the new groupMemberIdentities, kick the member
			if(null == Functional.select(
				groupMemberIdentities,
				identity -> TestUtil.compare(identity, savedIdentity), null
			))
			{
				logger.debug("remove member {} from group", savedIdentity);
				//remove from database

				//get model
				if(this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupIdAndIdentity(
					groupModel.getId(),
					savedIdentity ) > 0) {
					kickedGroupMemberIdentities.add(savedIdentity);
				}
			}
		}

		//send event to ALL members (including kicked and added) of group
		this.groupMessagingService.sendMessage(
			groupModel,
			allInvolvedMembers.toArray(new String[allInvolvedMembers.size()]),
			messageId -> {
				GroupCreateMessage groupCreateMessage = new GroupCreateMessage();
				groupCreateMessage.setMessageId(messageId);
				groupCreateMessage.setMembers(groupMemberIdentities);
				return groupCreateMessage;
			}
		);


		if (removePhoto) {
			//send event to ALL members (including kicked and added) of group
			this.groupMessagingService.sendMessage(
				groupModel,
				allInvolvedMembers.toArray(new String[allInvolvedMembers.size()]),
				messageId -> {
					GroupDeletePhotoMessage groupDeletePhotoMessage = new GroupDeletePhotoMessage();
					groupDeletePhotoMessage.setMessageId(messageId);
					return groupDeletePhotoMessage;
				}
			);
			this.avatarCacheService.reset(groupModel);
			ListenerManager.groupListeners.handle(listener -> listener.onUpdatePhoto(groupModel));
		} else {
			if (photoUploadResult != null) {
				if (isANewGroupPhoto) {
					//its a new picture, save it and send it to every member
					this.updateGroupPhoto(groupModel, photoUploadResult);
				} else {
					//only send the picture to the new members
					this.sendGroupPhotoToMembers(groupModel, newMembers.toArray(new String[newMembers.size()]), photoUploadResult);
				}
			}
		}

		if (name != null) {
			this.renameGroup(groupModel, name);
		}

		if (groupDesc != null) {
			this.changeGroupDesc(groupModel, groupDesc);
		}

		sendGroupCallStart(groupModel, newMembers);

		if(!kickedGroupMemberIdentities.isEmpty()) {
			//remove from cache!
			this.resetIdentityCache(groupModel.getId());

			for(final String kickedGroupMemberIdentity: kickedGroupMemberIdentities) {
				ListenerManager.groupListeners.handle(listener -> listener.onMemberKicked(groupModel, kickedGroupMemberIdentity, existingMembers.length));
			}
		}

		ListenerManager.groupListeners.handle(listener -> listener.onGroupStateChanged(groupModel, groupState, getGroupState(groupModel)));

		return groupModel;
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

	@Override
	public boolean renameGroup(GroupRenameMessage renameMessage) throws ThreemaException {
		final GroupModel groupModel = this.getGroup(renameMessage);

		if(groupModel != null) {
			//only rename, if the name is different
			if(!TestUtil.compare(groupModel.getName(), renameMessage.getGroupName())) {
				this.renameGroup(groupModel, renameMessage.getGroupName());
				ListenerManager.groupListeners.handle(listener -> listener.onRename(groupModel));
			}
			return true;
		}

		return false;
	}

	@Override
	public boolean renameGroup(final GroupModel group, final String newName) throws ThreemaException {
		boolean localeRenamed = !TestUtil.compare(group.getName(), newName);
		group.setName(newName);
		this.save(group);

		if(this.isGroupOwner(group)) {
			//send rename event!
			this.groupMessagingService.sendMessage(group, this.getGroupIdentities(group), messageId -> {
				final GroupRenameMessage rename = new GroupRenameMessage();
				rename.setMessageId(messageId);
				rename.setGroupName(newName);
				return rename;
			});

			if(localeRenamed) {
				ListenerManager.groupListeners.handle(listener -> listener.onRename(group));
			}
		}

		return false;
	}

	// on Update new group desc
	private void changeGroupDesc(final GroupModel group, final String newGroupDesc) {
		group.setGroupDesc(newGroupDesc);
		this.save(group);
	}

	/**
	 * Do not make the upload
	 */
	private void updateGroupPhoto(final GroupModel groupModel, GroupPhotoUploadResult result) throws Exception {

		//send to the new blob to the users
		this.sendGroupPhotoToMembers(groupModel, this.getGroupIdentities(groupModel), result);

		//save the image
		this.fileService.writeGroupAvatar(groupModel, result.bitmapArray);

		//reset the avatar cache entry
		this.avatarCacheService.reset(groupModel);

		ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
			@Override
			public void handle(GroupListener listener) {
				listener.onUpdatePhoto(groupModel);
			}
		});
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

	private void sendGroupPhotoToMembers(GroupModel groupModel, String[] identities, final GroupPhotoUploadResult uploadResult) throws ThreemaException, IOException {
		this.groupMessagingService.sendMessage(groupModel, identities, messageId -> {
			final GroupSetPhotoMessage msg = new GroupSetPhotoMessage();
			msg.setMessageId(messageId);
			msg.setBlobId(uploadResult.blobId);
			msg.setEncryptionKey(uploadResult.encryptionKey);
			msg.setSize(uploadResult.size);
			return msg;
		});
	}

	@Override
	public boolean updateGroupPhoto(GroupSetPhotoMessage msg) throws Exception {
		final GroupModel groupModel = this.getGroup(msg);

		if(groupModel != null) {
			BlobLoader blobLoader = this.apiService.createLoader(msg.getBlobId());
			byte[] blob = blobLoader.load(false);
			NaCl.symmetricDecryptDataInplace(blob, msg.getEncryptionKey(), ProtocolDefines.GROUP_PHOTO_NONCE);

			boolean differentGroupPhoto = true;

			try (InputStream existingAvatar = this.fileService.getGroupAvatarStream(groupModel)) {
				if (blob != null && existingAvatar != null) {
					int index = 0;
					int next;
					while ((next = existingAvatar.read()) != -1) {
						if ((byte) next != blob[index]) {
							break;
						}
						index++;
					}
					differentGroupPhoto = index != blob.length;
				}
			}

			if (differentGroupPhoto) {
				this.fileService.writeGroupAvatar(groupModel, blob);

				//reset the avatar cache entry
				this.avatarCacheService.reset(groupModel);

				ListenerManager.groupListeners.handle(listener -> listener.onUpdatePhoto(groupModel));
			}

			return true;
		}

		return false;
	}

	@Override
	public boolean deleteGroupPhoto(GroupDeletePhotoMessage msg) {
		final GroupModel groupModel = this.getGroup(msg);

		if (groupModel != null) {
			this.fileService.removeGroupAvatar(groupModel);

			//reset the avatar cache entry
			this.avatarCacheService.reset(groupModel);

			ListenerManager.groupListeners.handle(listener -> listener.onUpdatePhoto(groupModel));

			return true;
		}
		return false;
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
	public String[] getGroupIdentities(GroupModel groupModel) {
		synchronized (this.groupIdentityCache) {
			String[] existingIdentities = this.groupIdentityCache.get(groupModel.getId());
			if(existingIdentities != null) {
				return existingIdentities;
			}

			List<GroupMemberModel> result = this.getGroupMembers(groupModel);
			String[] res = new String[result.size()];
			int pos = 0;
			for (GroupMemberModel m : result) {
				res[pos++] = m.getIdentity();
			}

			this.groupIdentityCache.put(groupModel.getId(), res);
			return res;
		}
	}

	private boolean isGroupMember(GroupModel groupModel, String identity) {
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
	public boolean isGroupMember(GroupModel groupModel) {
		return isGroupMember(groupModel, userService.getIdentity());
	}

	@Override
	public List<GroupMemberModel> getGroupMembers(GroupModel groupModel) {
		return this.databaseServiceNew.getGroupMemberModelFactory().getByGroupId(
				groupModel.getId()
		);
	}

	@Override
	public GroupMemberModel getGroupMember(GroupModel groupModel, String identity) {
		return this.databaseServiceNew.getGroupMemberModelFactory().getByGroupIdAndIdentity(
				groupModel.getId(),
				identity);
	}

	@Override
	public Collection<ContactModel> getMembers(GroupModel groupModel) {
		return this.contactService.getByIdentities(this.getGroupIdentities(groupModel));
	}


	@Override
	public String getMembersString(GroupModel groupModel) {
		// should probably rather return a list of ContactModels, or maybe ThreemaIds :-)
		Collection<ContactModel> contacts = this.getMembers(groupModel);
		String[] names = new String[contacts.size()];
		int pos = 0;
		for (ContactModel c : contacts) {
			names[pos++] = NameUtil.getDisplayNameOrNickname(c, true);
		}
		return TextUtils.join(", ", names);
	}

	@Override
	public GroupMessageReceiver createReceiver(GroupModel groupModel) {
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
		return avatarCacheService.getGroupAvatar(groupModel, options);
	}

	@AnyThread
	@Override
	public void loadAvatarIntoImage(@NonNull GroupModel groupModel, @NonNull ImageView imageView, @NonNull AvatarOptions options) {
		avatarCacheService.loadGroupAvatarIntoImage(groupModel, imageView, options);
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
	public boolean isGroupOwner(GroupModel groupModel) {
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
			isGroupOwner(groupModel) &&
			countMembers(groupModel) == 1;
	}

	@Override
	public int getOtherMemberCount(GroupModel groupModel) {
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
	public Map<String, Integer> getGroupMemberIDColorIndices(GroupModel model) {
		Map<String, Integer> colors = this.groupMemberColorCache.get(model.getId());
		if(colors == null || colors.size() == 0) {
			colors = this.databaseServiceNew.getGroupMemberModelFactory().getIDColorIndices(
					model.getId()
			);

			this.groupMemberColorCache.put(model.getId(), colors);
		}

		return colors;
	}

	@Override
	public boolean sendEmptySync(GroupModel groupModel, String receiverIdentity) {
		try {
			this.groupMessagingService.sendMessage(
				groupModel,
				new String[]{receiverIdentity},
				messageId -> {
					GroupCreateMessage groupCreateMessage = new GroupCreateMessage();
					groupCreateMessage.setMessageId(messageId);
					groupCreateMessage.setMembers(new String[]{userService.getIdentity()});
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
		//send event to clients
		final String[] groupMemberIdentities =  this.getGroupIdentities(groupModel);

		//send to ALL members!
		return this.sendSync(groupModel, groupMemberIdentities);
	}

	@Override
	public boolean sendSync(final GroupModel groupModel, final String[] memberIdentities) {
		boolean success = false;

		this.createReceiver(groupModel);

		try {
			this.groupMessagingService.sendMessage(groupModel, memberIdentities, messageId -> {
				final GroupCreateMessage groupCreateMessage = new GroupCreateMessage();
				groupCreateMessage.setMessageId(messageId);
				groupCreateMessage.setMembers(getGroupIdentities(groupModel));
				return groupCreateMessage;
			});

			this.groupMessagingService.sendMessage(groupModel, memberIdentities, messageId -> {
				final GroupRenameMessage groupRenameMessage = new GroupRenameMessage();
				groupRenameMessage.setMessageId(messageId);
				groupRenameMessage.setGroupName(groupModel.getName());
				return groupRenameMessage;
			});

			byte[] groupPhoto = null;
			/* do not send a group picture if none has been set */
			if (fileService.hasGroupAvatarFile(groupModel)) {
				// Read bytes directly from the file to ensure that no jpeg/bitmap encoding/decoding
				// differences occur. Otherwise the receivers may think it is a new profile picture.
				groupPhoto = getGroupAvatarBytes(groupModel);
			}

			if (groupPhoto != null) {
				SecureRandom rnd = new SecureRandom();
				final byte[] encryptionKey = new byte[NaCl.SYMMKEYBYTES];
				rnd.nextBytes(encryptionKey);

				byte[] thumbnailBoxed = NaCl.symmetricEncryptData(groupPhoto, encryptionKey, ProtocolDefines.GROUP_PHOTO_NONCE);
				BlobUploader blobUploaderThumbnail = this.apiService.createUploader(thumbnailBoxed);
				final byte[] blobId;
				try {
					blobId = blobUploaderThumbnail.upload();

					final int size = thumbnailBoxed.length;

					this.groupMessagingService.sendMessage(groupModel, memberIdentities, messageId -> {
						final GroupSetPhotoMessage msg = new GroupSetPhotoMessage();
						msg.setMessageId(messageId);
						msg.setBlobId(blobId);
						msg.setEncryptionKey(encryptionKey);
						msg.setSize(size);
						return msg;
					});
				} catch (IOException e) {
					logger.error("Exception", e);
					return false;
				}
			}


			//update sync
			groupModel.setSynchronizedAt(new Date());
			this.save(groupModel);

			success = true;
		} catch (ThreemaException e) {
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
	public boolean processRequestSync(GroupRequestSyncMessage msg) {
		GroupModel groupModel = this.getGroup(msg);
		if(this.isGroupOwner(groupModel)) {
			//only handle, if i am the owner!
			if (this.isGroupMember(groupModel, msg.getFromIdentity())) {
				return this.sendSync(groupModel, new String[]{msg.getFromIdentity()});
			}
		}

		//mark as "handled"
		return true;
	}

	@Override
	public List<GroupModel> getGroupsByIdentity(String identity) {
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
	public GroupAccessModel getAccess(GroupModel groupModel, boolean allowEmpty) {
		GroupAccessModel groupAccessModel = new GroupAccessModel();
		if(groupModel != null) {
			final String myIdentity = this.userService.getIdentity();

			boolean iAmGroupMember = this.getGroupMember(groupModel, myIdentity)
					!= null;

			if(!iAmGroupMember && !allowEmpty) {
				// check if i am the administrator - even if i'm no longer a group member
				iAmGroupMember = TestUtil.compare(myIdentity, groupModel.getCreatorIdentity());
			}

			if(!iAmGroupMember) {
				groupAccessModel.setCanReceiveMessageAccess(new Access(
						false,
						R.string.you_are_not_a_member_of_this_group
				));

				groupAccessModel.setCanSendMessageAccess(new Access(
						false,
						R.string.you_are_not_a_member_of_this_group
				));
			}
			else if (!allowEmpty) {
				//check if the group is empty
				if(this.getOtherMemberCount(groupModel) <= 0) {
					//a empty group
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

	@Override
	public void save(GroupModel model) {
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

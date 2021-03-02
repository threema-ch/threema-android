/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.SparseArray;

import com.neilalexander.jnacl.NaCl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.APIConnector;
import ch.threema.client.AbstractGroupMessage;
import ch.threema.client.Base32;
import ch.threema.client.BlobLoader;
import ch.threema.client.BlobUploader;
import ch.threema.client.GroupCreateMessage;
import ch.threema.client.GroupDeletePhotoMessage;
import ch.threema.client.GroupId;
import ch.threema.client.GroupLeaveMessage;
import ch.threema.client.GroupRenameMessage;
import ch.threema.client.GroupRequestSyncMessage;
import ch.threema.client.GroupSetPhotoMessage;
import ch.threema.client.IdentityState;
import ch.threema.client.MessageId;
import ch.threema.client.ProtocolDefines;
import ch.threema.client.Utils;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.GroupRequestSyncLogModelFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.GroupRequestSyncLogModel;
import ch.threema.storage.models.access.Access;
import ch.threema.storage.models.access.GroupAccessModel;

public class GroupServiceImpl implements GroupService {
	private static final Logger logger = LoggerFactory.getLogger(GroupService.class);

	private final ApiService apiService;
	private final GroupApiService groupApiService;
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

	class GroupPhotoUploadResult {
		public byte[] bitmapArray;
		public byte[] blobId;
		public byte[] encryptionKey;
		public int size;
	}

	public GroupServiceImpl(
			CacheService cacheService,
			ApiService apiService,
			GroupApiService groupApiService,
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
		this.apiService = apiService;
		this.groupApiService = groupApiService;

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
	public List<GroupModel> getAll() {
		return this.getAll(null);
	}

	@Override
	public List<GroupModel> getAll(GroupFilter filter) {
		List<GroupModel> res = new ArrayList<>();

		res.addAll(this.databaseServiceNew.getGroupModelFactory().filter(filter));

		if (filter != null && !filter.withDeserted()) {
			Iterator iterator = res.iterator();
			while (iterator.hasNext()) {
				GroupModel groupModel = (GroupModel) iterator.next();
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
			updateGroup(groupModel, null, identities, null, false);
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
			this.groupApiService.sendMessage(groupModel, identities, new GroupApiService.CreateApiMessage() {
				@Override
				public AbstractGroupMessage create(MessageId messageId) {
					GroupLeaveMessage groupLeaveMessage = new GroupLeaveMessage();
					groupLeaveMessage.setMessageId(messageId);
					return groupLeaveMessage;
				}
			});
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			return false;
		}

		this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupId(groupModel.getId());

		// save with "old" name
		groupModel.setName(displayName);
		this.save(groupModel);

		//reset cache
		this.resetIdentityCache(groupModel.getId());

		//fire kicked
		ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
			@Override
			public void handle(GroupListener listener) {
				listener.onMemberKicked(groupModel, userService.getIdentity());
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
		this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupId(groupModel.getId());
		for(GroupMessageModel messageModel: this.databaseServiceNew.getGroupMessageModelFactory().getByGroupIdUnsorted(groupModel.getId())) {
			//remove all message identity models
			this.databaseServiceNew.getGroupMessagePendingMessageIdModelFactory().delete(messageModel.getId());

			//remove all files
			this.fileService.removeMessageFiles(messageModel, true);
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
				GroupRequestSyncLogModel model = groupRequestSyncLogModelFactory.get(msg.getGroupId().toString(),
						msg.getGroupCreator());

				//send a request sync if the old request sync older than one week or NULL
				if(model == null
						|| model.getLastRequest() == null
						|| model.getLastRequest().getTime() < (System.currentTimeMillis() - DateUtils.WEEK_IN_MILLIS)) {
					logger.debug("send request sync to group creator [" + msg.getGroupCreator() + "]");
					//send a request sync to the creator!!
					int messageCount = requestSync(msg.getGroupCreator(), msg.getGroupId());

					if(messageCount == 1) {
						if(model == null) {
							model = new GroupRequestSyncLogModel();
							model.setAPIGroupId(msg.getGroupId().toString(), msg.getGroupCreator());
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
		return this.groupApiService.sendMessage(groupId,
				groupCreator,
				new String[]{groupCreator},
				new GroupApiService.CreateApiMessage() {
					@Override
					public AbstractGroupMessage create(MessageId messageId) {
						GroupRequestSyncMessage groupRequestSyncMessage = new GroupRequestSyncMessage();
						groupRequestSyncMessage.setMessageId(messageId);
						return groupRequestSyncMessage;
					}
				}
		);
	}

	public boolean sendLeave(AbstractGroupMessage msg) {
		if(msg != null) {
			try {
				//send a request sync to the creator!!
				this.groupApiService.sendMessage(msg.getGroupId(),
						msg.getGroupCreator(),
						new String[]{msg.getFromIdentity(), msg.getGroupCreator()},
						new GroupApiService.CreateApiMessage() {
							@Override
							public AbstractGroupMessage create(MessageId messageId) {
								GroupLeaveMessage groupLeaveMessage = new GroupLeaveMessage();
								groupLeaveMessage.setMessageId(messageId);
								return groupLeaveMessage;
							}
						});

				return true;
			} catch (ThreemaException e) {
				logger.error("Exception", e);
			}

		}
		return false;
	}

	private GroupModel getByAbstractGroupMessage(final AbstractGroupMessage message) throws SQLException {
		synchronized (this.groupModelCache) {
			GroupModel model = Functional.select(this.groupModelCache, new IPredicateNonNull<GroupModel>() {
				@Override
				public boolean apply(@NonNull GroupModel type) {
					return message.getGroupId().toString().equals(type.getApiGroupId()) && message.getGroupCreator().equals(type.getCreatorIdentity());
				}
			});

			if(model == null) {
				model = this.databaseServiceNew.getGroupModelFactory().getByApiGroupIdAndCreator(
						message.getGroupId().toString(),
						message.getGroupCreator()
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
	public boolean removeMemberFromGroup(GroupLeaveMessage msg) {
		try {
			GroupModel model = this.getByAbstractGroupMessage(msg);

			if(model != null) {
				this.removeMemberFromGroup(model, msg.getFromIdentity());
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

	@Override
	public boolean removeMemberFromGroup(final GroupModel group, final String identity) {
		if(this.databaseServiceNew.getGroupMemberModelFactory().deleteByGroupIdAndIdentity(
				group.getId(),
				identity
		)> 0) {
			this.resetIdentityCache(group.getId());

			ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
				@Override
				public void handle(GroupListener listener) {
					listener.onMemberLeave(group, identity);
				}
			});
			return true;
		}

		return false;
	}

	@Override
	public GroupModel getById(int groupId) {
		synchronized (this.groupModelCache) {
			GroupModel existingGroupModel = groupModelCache.get(groupId);
			if(existingGroupModel != null) {
				return existingGroupModel;
			}
			return this.cache(this.databaseServiceNew.getGroupModelFactory().getById(groupId));
		}
	}


	@Override
	public GroupCreateMessageResult processGroupCreateMessage(final GroupCreateMessage groupCreateMessage) throws ThreemaException, InvalidEntryException {
		final GroupCreateMessageResult result = new GroupCreateMessageResult();
		result.success = false;

		boolean isNewGroup;

		//check if i am in group
		boolean iAmAGroupMember = Functional.select(groupCreateMessage.getMembers(), new IPredicateNonNull<String>() {
			@Override
			public boolean apply(@NonNull String identity) {
				return TestUtil.compare(identity, userService.getIdentity());
			}
		}, null) != null;

		try {
			result.groupModel = this.getByAbstractGroupMessage(groupCreateMessage);
			isNewGroup = result.groupModel == null;

		} catch (SQLException e) {
			logger.error("Exception", e);
			return null;
		}

		if(!iAmAGroupMember) {

			if(isNewGroup) {
				//do nothing
				//group not saved and i am not a member
				result.success = true;
				result.groupModel = null;

			}
			else {
				//user is kicked out of group
				//remove all members
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
						listener.onMemberKicked(groupModel, userService.getIdentity());
					}
				});
			}

			return result;
		}


		if(result.groupModel == null) {
			result.groupModel = new GroupModel();
			result.groupModel
					.setApiGroupId(groupCreateMessage.getGroupId().toString())
					.setCreatorIdentity(groupCreateMessage.getGroupCreator())
					.setCreatedAt(new Date());

			this.databaseServiceNew.getGroupModelFactory().create(result.groupModel);
			this.cache(result.groupModel);

		}
		else if(result.groupModel.isDeleted()) {
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
				//fire event

				ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
					@Override
					public void handle(GroupListener listener) {
						listener.onMemberKicked(result.groupModel, groupMemberModel.getIdentity());
					}
				});
			}
		}

		//success!
		result.success = true;

		this.rebuildColors(result.groupModel);

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
				.setApiGroupId(Utils.byteArrayToHexString(id.getGroupId()))
				.setCreatorIdentity(this.userService.getIdentity())
				.setName(name)
				.setCreatedAt(new Date())
				.setSynchronizedAt(new Date());

		this.databaseServiceNew.getGroupModelFactory().create(groupModel);
		this.cache(groupModel);

		//if a name is set
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

		//send event to server
		this.groupApiService.sendMessage(groupModel, this.getGroupIdentities(groupModel), new GroupApiService.CreateApiMessage() {
			@Override
			public AbstractGroupMessage create(MessageId messageId) {
				GroupCreateMessage groupCreateMessage = new GroupCreateMessage();
				groupCreateMessage.setMessageId(messageId);
				groupCreateMessage.setMembers(groupMemberIdentities);
				return groupCreateMessage;
			}
		});

		if(groupModel.getName() != null && groupModel.getName().length() > 0) {
			this.renameGroup(groupModel, groupModel.getName());
		}

		this.rebuildColors(groupModel);

		return groupModel;
	}

	@Override
	public Boolean addMemberToGroup(final GroupModel groupModel, final String identity) {
		GroupMemberModel m = this.getGroupMember(groupModel, identity);
		boolean isNewMember = m == null;

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
			ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
				@Override
				public void handle(GroupListener listener) {
					listener.onNewMember(groupModel, identity);
				}
			});
		}

		return isNewMember;
	}

	/**
	 * Add one or more members to a group. Will fetch identities from server if not known
	 * If "block unknown" is enabled, new contacts will not be created for group members not already in contacts
	 * @param groupModel Group model to add members to
	 * @param identities Array of identities to add
	 * @return true if members have been added, false if no members have been specified, null if new identities could not be fetched
	 */
	@Override
	public Boolean addMembersToGroup(final GroupModel groupModel, @Nullable final String[] identities) {
		if (identities != null && identities.length > 0) {
			ArrayList<String> newContacts = new ArrayList<>();
			ArrayList<String> newMembers = new ArrayList<>();

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
								contactModel.setType(result.type);
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
						listener.onNewMember(groupModel, identity);
					}
				}
			});

			return true;
		}
		return false;
	}

	@Override
	public GroupModel updateGroup(final GroupModel groupModel, String name, final String[] groupMemberIdentities, Bitmap photo, boolean removePhoto) throws Exception {
		//existing members
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
			if (photo == null && newMembers.size() > 0) {
				//load existing picture
				photo = this.fileService.getGroupAvatar(groupModel);
			}

			if (photo != null) {
				//upload the picture if possible
				photoUploadResult = this.uploadGroupPhoto(photo);
			}
		}

		// add new members to group
		if (newMembers.size() > 0) {
			for (String newMember: newMembers) {
				logger.debug("add member " + newMember + " to group");
				this.addMemberToGroup(groupModel, newMember);
			}
		}

		//add creator to group
		this.addMemberToGroup(groupModel, groupModel.getCreatorIdentity());

		//now kick the members
		for(final String savedIdentity: existingMembers) {
			//if the identity NOT in the new groupMemberIdentities, kick the member
			if(null == Functional.select(groupMemberIdentities, new IPredicateNonNull<String>() {
				@Override
				public boolean apply(@NonNull String identity) {
					return TestUtil.compare(identity, savedIdentity);
				}
			}, null))
			{
				logger.debug("remove member " + savedIdentity + " from group");
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
		this.groupApiService.sendMessage(groupModel, allInvolvedMembers.toArray(new String[allInvolvedMembers.size()]), new GroupApiService.CreateApiMessage() {
			@Override
			public AbstractGroupMessage create(MessageId messageId) {
				GroupCreateMessage groupCreateMessage = new GroupCreateMessage();
				groupCreateMessage.setMessageId(messageId);
				groupCreateMessage.setMembers(groupMemberIdentities);
				return groupCreateMessage;
			}
		});


		if (removePhoto) {
			//send event to ALL members (including kicked and added) of group
			this.groupApiService.sendMessage(groupModel, allInvolvedMembers.toArray(new String[allInvolvedMembers.size()]), new GroupApiService.CreateApiMessage() {
				@Override
				public AbstractGroupMessage create(MessageId messageId) {
					GroupDeletePhotoMessage groupDeletePhotoMessage = new GroupDeletePhotoMessage();
					groupDeletePhotoMessage.setMessageId(messageId);
					return groupDeletePhotoMessage;
				}
			});
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

		if(kickedGroupMemberIdentities.size() > 0) {
			//remove from cache!
			this.resetIdentityCache(groupModel.getId());

			for(final String kickedGroupMemberIdentity: kickedGroupMemberIdentities) {
				ListenerManager.groupListeners.handle(listener -> listener.onMemberKicked(groupModel, kickedGroupMemberIdentity));
			}
		}
		return groupModel;
	}

	@Override
	public boolean renameGroup(GroupRenameMessage renameMessage) throws ThreemaException {
		final GroupModel groupModel = this.getGroup(renameMessage);

		if(groupModel != null) {
			//only rename, if the name is different
			if(!TestUtil.compare(groupModel.getName(), renameMessage.getGroupName())) {
				this.renameGroup(groupModel, renameMessage.getGroupName());
				ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
					@Override
					public void handle(GroupListener listener) {
						listener.onRename(groupModel);
					}
				});
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
			this.groupApiService.sendMessage(group, this.getGroupIdentities(group), new GroupApiService.CreateApiMessage() {
				@Override
				public AbstractGroupMessage create(MessageId messageId) {
					GroupRenameMessage rename = new GroupRenameMessage();
					rename.setMessageId(messageId);
					rename.setGroupName(newName);
					return rename;
				}
			});

			if(localeRenamed) {
				ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
					@Override
					public void handle(GroupListener listener) {
						listener.onRename(group);
					}
				});
			}
		}

		return false;
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
		this.groupApiService.sendMessage(groupModel, identities, new GroupApiService.CreateApiMessage() {
			@Override
			public AbstractGroupMessage create(MessageId messageId) {
				GroupSetPhotoMessage msg = new GroupSetPhotoMessage();
				msg.setMessageId(messageId);
				msg.setBlobId(uploadResult.blobId);
				msg.setEncryptionKey(uploadResult.encryptionKey);
				msg.setSize(uploadResult.size);
				return msg;
			}
		});
	}

	@Override
	public boolean updateGroupPhoto(GroupSetPhotoMessage msg) throws Exception {
		final GroupModel groupModel = this.getGroup(msg);

		if(groupModel != null) {
			BlobLoader blobLoader = this.apiService.createLoader(msg.getBlobId());
			byte[] blob = blobLoader.load(false);
			NaCl.symmetricDecryptDataInplace(blob, msg.getEncryptionKey(), ProtocolDefines.GROUP_PHOTO_NONCE);

			this.fileService.writeGroupAvatar(groupModel, blob);

			//reset the avatar cache entry
			this.avatarCacheService.reset(groupModel);

			ListenerManager.groupListeners.handle(listener -> listener.onUpdatePhoto(groupModel));

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
//		logger.debug("MessageReceiver", "create group receiver");
		return new GroupMessageReceiver(groupModel,
				this,
				this.databaseServiceNew,
				this.groupApiService,
				this.contactService);
	}

	@Override
	@Nullable
	public Bitmap getCachedAvatar(GroupModel groupModel) {
		if(groupModel == null) {
			return null;
		}
		return this.avatarCacheService.getGroupAvatarLowFromCache(groupModel);
	}

	@Override
	public Bitmap getAvatar(GroupModel groupModel, boolean highResolution) {
		return getAvatar(groupModel, highResolution, false);
	}

	@Override
	public Bitmap getDefaultAvatar(GroupModel groupModel, boolean highResolution) {
		return getAvatar(groupModel, highResolution, true);
	}

	@Override
	public Bitmap getNeutralAvatar(boolean highResolution) {
		return avatarCacheService.getGroupAvatarNeutral(highResolution);
	}

	private @Nullable Bitmap getAvatar(GroupModel groupModel, boolean highResolution, boolean defaultOnly) {
		if(groupModel == null) {
			return null;
		}

		Map<String, Integer> colorMap = this.getGroupMemberColors(groupModel);
		Collection<Integer> colors = null;
		if(colorMap != null) {
			colors = colorMap.values();
		}
		if(highResolution) {
			return this.avatarCacheService.getGroupAvatarHigh(groupModel, colors, defaultOnly);
		}
		else {
			return this.avatarCacheService.getGroupAvatarLow(groupModel, colors, defaultOnly);
		}
	}

	public boolean isGroupOwner(GroupModel groupModel) {
		return groupModel != null
				&& this.userService.getIdentity() != null
				&& this.userService.isMe(groupModel.getCreatorIdentity());
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
	public int getPrimaryColor(GroupModel groupModel) {
		if(groupModel != null) {
			//get members
			Map<String, Integer> colors = this.getGroupMemberColors(groupModel);
			if(colors != null && colors.size() > 0) {
				Collection<Integer> v = colors.values();

				if(v.size() > 0) {
					return v.iterator().next();
				}
			}
		}
		return 0;
	}

	@Override
	public boolean rebuildColors(GroupModel model) {
//		List<GroupMemberModel> members = this.getGroupMembers(model);
//		RuntimeExceptionDao<GroupMemberModel, Integer> groupMemberDao = this.databaseService.getGroupMemberDao();
//		if(TestUtil.required(members, groupMemberDao)) {
//			int colors[] = ColorUtil.generateColorPalette(members.size());
//			for(int n = 0; n < members.size(); n++) {
//				GroupMemberModel member = members.get(n);
//				if(member != null) {
//					members.get(n).setColor(colors[n]);
//					groupMemberDao.update(members.get(n));
//				}
//			}
//		}
		return false;
	}

	@Override
	public Map<String, Integer> getGroupMemberColors(GroupModel model) {
		Map<String, Integer> colors = this.groupMemberColorCache.get(model.getId());
		if(colors == null || colors.size() == 0) {
			colors = this.databaseServiceNew.getGroupMemberModelFactory().getColors(
					model.getId()
			);

			this.groupMemberColorCache.put(model.getId(), colors);
		}

		return colors;
	}

	@Override
	public boolean sendEmptySync(GroupModel groupModel, String receiverIdentity) {
		try {
			this.groupApiService.sendMessage(groupModel, new String[]{receiverIdentity}, new GroupApiService.CreateApiMessage() {
				@Override
				public AbstractGroupMessage create(MessageId messageId) {
					GroupCreateMessage groupCreateMessage = new GroupCreateMessage();
					groupCreateMessage.setMessageId(messageId);
					groupCreateMessage.setMembers(new String[]{userService.getIdentity()});
					return groupCreateMessage;
				}
			});
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
			this.groupApiService.sendMessage(groupModel, memberIdentities, new GroupApiService.CreateApiMessage() {
				@Override
				public AbstractGroupMessage create(MessageId messageId) {
					GroupCreateMessage groupCreateMessage = new GroupCreateMessage();
					groupCreateMessage.setMessageId(messageId);
					groupCreateMessage.setMembers(getGroupIdentities(groupModel));
					return groupCreateMessage;
				}
			});

			this.groupApiService.sendMessage(groupModel, memberIdentities, new GroupApiService.CreateApiMessage() {
				@Override
				public AbstractGroupMessage create(MessageId messageId) {
					GroupRenameMessage groupRenameMessage = new GroupRenameMessage();
					groupRenameMessage.setMessageId(messageId);
					groupRenameMessage.setGroupName(groupModel.getName());
					return groupRenameMessage;
				}
			});

			Bitmap picture = null;
			/* do not send a group picture if none has been set */
			if (fileService.hasGroupAvatarFile(groupModel)) {
				picture = this.getAvatar(groupModel, true);
			}

			if (picture != null) {
				SecureRandom rnd = new SecureRandom();
				final byte[] encryptionKey = new byte[NaCl.SYMMKEYBYTES];
				rnd.nextBytes(encryptionKey);

				byte[] bitmapArray = BitmapUtil.bitmapToJpegByteArray(picture);
				byte[] thumbnailBoxed = NaCl.symmetricEncryptData(bitmapArray, encryptionKey, ProtocolDefines.GROUP_PHOTO_NONCE);
				BlobUploader blobUploaderThumbnail = this.apiService.createUploader(thumbnailBoxed);
				final byte[] blobId;
				try {
					blobId = blobUploaderThumbnail.upload();

					final int size = thumbnailBoxed.length;

					this.groupApiService.sendMessage(groupModel, memberIdentities, new GroupApiService.CreateApiMessage() {
						@Override
						public AbstractGroupMessage create(MessageId messageId) {
							GroupSetPhotoMessage msg = new GroupSetPhotoMessage();
							msg.setMessageId(messageId);
							msg.setBlobId(blobId);
							msg.setEncryptionKey(encryptionKey);
							msg.setSize(size);
							return msg;
						}
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

	/**
	 *
	 * @param groupModel
	 * @param allowEmpty - allow access even if there are no other members in this group
	 * @return GroupAccessModel
	 */
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
		return ("g-" + String.valueOf(groupModel.getId())).hashCode();
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
			messageDigest.update(("g-" + String.valueOf(groupId)).getBytes());
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

	private void save(GroupModel model) {
		this.databaseServiceNew.getGroupModelFactory().createOrUpdate(
				model
		);
	}
}

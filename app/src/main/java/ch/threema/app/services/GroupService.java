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
import android.content.Intent;
import android.graphics.Bitmap;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.messages.GroupCreateMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeletePhotoMessage;
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage;
import ch.threema.domain.protocol.csp.messages.GroupRenameMessage;
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage;
import ch.threema.domain.protocol.csp.messages.GroupSetPhotoMessage;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.access.GroupAccessModel;

public interface GroupService extends AvatarService<GroupModel> {

	/**
	 * Group state note yet determined
	 */
	int UNDEFINED = 0;
	/**
	 * A local notes "group"
	 */
	int NOTES = 1;
	/**
	 * A group with other people in it
	 */
	int PEOPLE = 2;

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({UNDEFINED, NOTES, PEOPLE})
	@interface GroupState {}

	interface GroupFilter {
		boolean sortingByDate();
		boolean sortingAscending();
		boolean sortingByName();
		boolean withDeleted();
		boolean withDeserted(); // include groups that the user is no longer a member of
	}

	class GroupCreateMessageResult {
		protected GroupModel groupModel;
		protected boolean success;

		public GroupModel getGroupModel() {
			return this.groupModel;
		}

		public boolean success() {
			return this.success;
		}
	}

	@Nullable GroupModel getById(int intExtra);

	GroupModel getGroup(AbstractGroupMessage message) throws SQLException;

	boolean requestSync(AbstractGroupMessage msg, boolean leaveIfMine);

	int requestSync(String groupCreator, GroupId groupId) throws ThreemaException;

	boolean sendLeave(AbstractGroupMessage msg);

	GroupCreateMessageResult processGroupCreateMessage(GroupCreateMessage groupCreateMessage);

	GroupModel createGroup(String name, String[] groupMemberIdentities, Bitmap picture) throws Exception;

	/**
	 * Update group properties and members.
	 * This method triggers protocol messages to all group members that are affected by the change.
	 *
	 * @param groupModel            Group that should be modified
	 * @param name                  New name of group, {@code null} if unchanged.
	 * @param groupDesc             New group description for the group, {@code null} if unchanged.
	 * @param groupMemberIdentities Identities of all group members.
	 * @param photo                 New group photo, {@code null} if unchanged.
	 * @param removePhoto           Whether to remove the group photo.
	 * @return Updated groupModel
	 */
	@NonNull
	GroupModel updateGroup(
		@NonNull GroupModel groupModel,
		@Nullable String name,
		@Nullable String groupDesc,
		@Nullable String[] groupMemberIdentities,
		@Nullable Bitmap photo,
		boolean removePhoto
	) throws Exception;

	boolean renameGroup(GroupRenameMessage renameMessage) throws ThreemaException;

	/**
	 * Rename the group. This renames the group regardless whether the new name is the same or not.
	 * If the user is the owner of the group, a rename message is sent to every member of the group.
	 * Note that this method does not trigger the group listeners.
	 *
	 * @param group   the group model that may be updated
	 * @param newName the new name of the group
	 * @throws ThreemaException if sending the group rename message fails
	 */
	void renameGroup(GroupModel group, String newName) throws ThreemaException;

	/**
	 * @return return true if a new member added, false a existing group member updated or null if a error occurred
	 * @throws InvalidEntryException
	 */
	@Nullable Boolean addMemberToGroup(GroupModel groupModel, String identity);

	/**
	 * Add one or more members to a group. Will fetch identities from server if not known
	 * If "block unknown" is enabled, new contacts will not be created for group members not already in contacts
	 * @param groupModel Group model to add members to
	 * @param identities Array of identities to add
	 * @return true if members have been added, false if no members have been specified, null if new identities could not be fetched
	 */
	Boolean addMembersToGroup(final GroupModel groupModel, final String[] identities);

	boolean remove(GroupModel groupModel);
	boolean remove(GroupModel groupModel, boolean silent);
	boolean removeAllMembersAndLeave(GroupModel groupModel);
	boolean leaveGroup(GroupModel groupModel);

	void removeAll();
	boolean updateGroupPhoto(GroupSetPhotoMessage msg) throws Exception;
	boolean deleteGroupPhoto(GroupDeletePhotoMessage msg);

	/**
	 * Return the identities of all members of this group including the creator and including the current user
	 * @param groupModel Group model of the group
	 * @return String array of identities (i.e. Threema IDs)
	 */
	@NonNull String[] getGroupIdentities(GroupModel groupModel);
	GroupMemberModel getGroupMember(GroupModel groupModel, String identity);
	List<GroupMemberModel> getGroupMembers(GroupModel groupModel);

	List<GroupModel> getAll();
	List<GroupModel> getAll(GroupFilter filter);

	Collection<ContactModel> getMembers(GroupModel groupModel);

	String getMembersString(GroupModel groupModel);

	GroupMessageReceiver createReceiver(GroupModel groupModel);

	boolean isGroupOwner(GroupModel groupModel);
	boolean isGroupMember(GroupModel groupModel);

	GroupModel getByApiGroupIdAndCreator(@NonNull GroupId apiGroupId, @NonNull String creatorIdentity);

	boolean removeMemberFromGroup(GroupLeaveMessage msg);

	/**
	 * Count members in a group
	 * @param groupModel
	 * @return Number of members in this group including group creator and the current user
	 */
	int countMembers(@NonNull GroupModel groupModel);

	/**
	 * Whether the provided group is an implicit note group (i.e. data is kept local)
	 * @param groupModel of the group
	 * @return true if the group is a note group, false otherwise
	 */
	boolean isNotesGroup(@NonNull GroupModel groupModel);

	int getOtherMemberCount(GroupModel model);

	/**
	 * Get a map from the group member identity to its id color index.
	 *
	 * @param model the group model
	 * @return a map with the ID color indices of the members
	 */
	Map<String, Integer> getGroupMemberIDColorIndices(GroupModel model);

	boolean sendEmptySync(GroupModel groupModel, String receiverIdentity);

	boolean sendSync(GroupModel groupModel);
	boolean sendSync(GroupModel groupModel, String[] memberIdentities);
	boolean processRequestSync(GroupRequestSyncMessage msg);

	List<GroupModel> getGroupsByIdentity(String identity);

	/**
	 * Get group status info for the provided group
	 * @param groupModel Group
	 * @param allowEmpty Whether to allow access even if there are no other members in this group
	 * @return GroupAccessModel
	 */
	GroupAccessModel getAccess(GroupModel groupModel, boolean allowEmpty);

	@Deprecated
	int getUniqueId(GroupModel groupModel);
	String getUniqueIdString(GroupModel groupModel);

	String getUniqueIdString(int groupId);

	void setIsArchived(GroupModel groupModel, boolean archived);

	boolean isFull(GroupModel groupModel);

	Intent getGroupEditIntent(@NonNull GroupModel groupModel, @NonNull Activity activity);
	void save(GroupModel model);

	/**
	 * Add or update an identity-specific state to the specified group message
	 * Don't forget to call save() to update the model
	 * @param messageModel group message to update
	 * @param identityToAdd identity of initiator of state change
	 * @param newState state for the specified identity
	 */
	void addGroupMessageState(@NonNull GroupMessageModel messageModel, @NonNull String identityToAdd, @NonNull MessageState newState);
}

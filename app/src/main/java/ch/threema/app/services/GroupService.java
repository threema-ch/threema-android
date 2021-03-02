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

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.base.ThreemaException;
import ch.threema.client.AbstractGroupMessage;
import ch.threema.client.GroupCreateMessage;
import ch.threema.client.GroupDeletePhotoMessage;
import ch.threema.client.GroupId;
import ch.threema.client.GroupLeaveMessage;
import ch.threema.client.GroupRenameMessage;
import ch.threema.client.GroupRequestSyncMessage;
import ch.threema.client.GroupSetPhotoMessage;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.access.GroupAccessModel;

public interface GroupService extends AvatarService<GroupModel> {
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

	GroupModel getById(int intExtra);
	GroupModel getGroup(AbstractGroupMessage message) throws SQLException;

	boolean requestSync(AbstractGroupMessage msg, boolean leaveIfMine);

	int requestSync(String groupCreator, GroupId groupId) throws ThreemaException;

	boolean sendLeave(AbstractGroupMessage msg);

	GroupCreateMessageResult processGroupCreateMessage(GroupCreateMessage groupCreateMessage) throws ThreemaException, InvalidEntryException;

	GroupModel createGroup(String name, String[] groupMemberIdentities, Bitmap picture) throws Exception;
	GroupModel updateGroup(GroupModel groupModel, String name, String[] groupMemberIdentities, Bitmap photo, boolean removePhoto) throws Exception;
	boolean renameGroup(GroupRenameMessage renameMessage) throws ThreemaException;

	boolean renameGroup(GroupModel group, String newName) throws ThreemaException;

	/**
	 * @return return true if a new member added, false a existing group member updated or null if a error occurred
	 * @throws InvalidEntryException
	 */
	Boolean addMemberToGroup(GroupModel groupModel, String identity) throws InvalidEntryException;
	Boolean addMembersToGroup(final GroupModel groupModel, final String[] identities);

	boolean remove(GroupModel groupModel);
	boolean remove(GroupModel groupModel, boolean silent);
	boolean removeAllMembersAndLeave(GroupModel groupModel);
	boolean leaveGroup(GroupModel groupModel);

	void removeAll();
	boolean updateGroupPhoto(GroupSetPhotoMessage msg) throws Exception;
	boolean deleteGroupPhoto(GroupDeletePhotoMessage msg);

	@NonNull String[] getGroupIdentities(GroupModel groupModel);
	GroupMemberModel getGroupMember(GroupModel groupModel, String identity);
	List<GroupMemberModel> getGroupMembers(GroupModel groupModel);

	List<GroupModel> getAll();
	List<GroupModel> getAll(GroupFilter filter);

	Collection<ContactModel> getMembers(GroupModel groupModel);

	String getMembersString(GroupModel groupModel);

	GroupMessageReceiver createReceiver(GroupModel groupModel);

	Bitmap getDefaultAvatar(GroupModel groupModel, boolean highResolution);

	Bitmap getNeutralAvatar(boolean highResolution);

	boolean isGroupOwner(GroupModel groupModel);

	int countMembers(@NonNull GroupModel groupModel);

	boolean isGroupMember(GroupModel groupModel);

	boolean removeMemberFromGroup(GroupLeaveMessage msg);
	boolean removeMemberFromGroup(GroupModel group, String identity);

	int getOtherMemberCount(GroupModel model);

	int getPrimaryColor(GroupModel groupModel);
	boolean rebuildColors(GroupModel model);
	Map<String, Integer> getGroupMemberColors(GroupModel model);

	boolean sendEmptySync(GroupModel groupModel, String receiverIdentity);

	boolean sendSync(GroupModel groupModel);
	boolean sendSync(GroupModel groupModel, String[] memberIdentities);
	boolean processRequestSync(GroupRequestSyncMessage msg);

	List<GroupModel> getGroupsByIdentity(String identity);
	GroupAccessModel getAccess(GroupModel groupModel, boolean allowEmpty);

	@Deprecated
	int getUniqueId(GroupModel groupModel);
	String getUniqueIdString(GroupModel groupModel);

	String getUniqueIdString(int groupId);

	void setIsArchived(GroupModel groupModel, boolean archived);
}

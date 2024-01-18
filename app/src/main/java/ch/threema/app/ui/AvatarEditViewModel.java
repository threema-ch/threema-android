/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

package ch.threema.app.ui;

import org.msgpack.core.annotations.Nullable;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

public class AvatarEditViewModel extends ViewModel {

	private static final String KEY_CAMERA_FILE = "cam";
	private static final String KEY_CROPPED_FILE = "crop";
	private static final String KEY_GROUP_ID = "group";
	private static final String KEY_CONTACT_IDENTITY = "contact";

	private SavedStateHandle savedState;
	private ContactService contactService;
	private GroupService groupService;

	public AvatarEditViewModel(SavedStateHandle savedStateHandle) {
		this.savedState = savedStateHandle;

		try {
			this.contactService = ThreemaApplication.getServiceManager().getContactService();
			this.groupService = ThreemaApplication.getServiceManager().getGroupService();
		} catch (Exception e) {
			//
		}
	}

	public File getCameraFile() {
		return this.savedState.get(KEY_CAMERA_FILE);
	}

	public void setCameraFile(File cameraFile) {
		this.savedState.set(KEY_CAMERA_FILE, cameraFile);
	}

	public File getCroppedFile() {
		return this.savedState.get(KEY_CROPPED_FILE);
	}

	public void setCroppedFile(File croppedFile) {
		this.savedState.set(KEY_CROPPED_FILE, croppedFile);
	}

	public @Nullable GroupModel getGroupModel() {
		Integer groupId = this.savedState.get(KEY_GROUP_ID);
		if (groupService != null && groupId != null) {
			return groupService.getById(groupId);
		}
		return null;
	}

	public void setGroupModel(@NonNull GroupModel groupModel) {
		this.savedState.set(KEY_GROUP_ID, groupModel.getId());
	}

	public @Nullable ContactModel getContactModel() {
		String identity = this.savedState.get(KEY_CONTACT_IDENTITY);
		if (contactService != null && identity != null) {
			return contactService.getByIdentity(identity);
		}
		return null;
	}

	public void setContactModel(@NonNull ContactModel contactModel) {
		this.savedState.set(KEY_CONTACT_IDENTITY, contactModel.getIdentity());
	}
}

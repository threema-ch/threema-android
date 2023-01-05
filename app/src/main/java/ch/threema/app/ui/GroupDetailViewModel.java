/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.GroupDetailAdapter;
import ch.threema.app.services.ContactService;
import ch.threema.storage.models.ContactModel;

/**
 * The ViewModel's role is to provide data to the UI and survive configuration changes.
 * A ViewModel acts as a communication center between the Repository and the UI.
 *
 * Never pass context into ViewModel instances. Do not store Activity, Fragment, or View instances or
 * their Context in the ViewModel.
 */

public class GroupDetailViewModel extends ViewModel {
	private static final String KEY_AVATAR_FILE = "avatar";
	private static final String KEY_GROUP_NAME = "name";
	private static final String KEY_GROUP_IDENTITIES = "contacts";
	private static final String KEY_AVATAR_REMOVED = "isRemoved";
	private static final String KEY_GROUP_DESC = "description";
	private static final String KEY_GROUP_DESC_TIMESTAMP = "descTimestamp";
	private static final String KEY_GROUP_DESC_STATE = "descState";


	private SavedStateHandle savedState;
	private ContactService contactService;
	private MutableLiveData<List<ContactModel>> groupMembers;

	public GroupDetailViewModel(SavedStateHandle savedStateHandle) {
		savedState = savedStateHandle;

		try {
			this.contactService = ThreemaApplication.getServiceManager().getContactService();
		} catch (Exception e) {
			//
		}

		this.groupMembers = new MutableLiveData<List<ContactModel>>() {
			@Nullable
			@Override
			public List<ContactModel> getValue() {
				return getGroupContacts();
			}
		};
	}

	public File getAvatarFile() {
		return this.savedState.get(KEY_AVATAR_FILE);
	}

	public void setAvatarFile(File avatarFile) {
		this.savedState.set(KEY_AVATAR_FILE, avatarFile);
	}

	public boolean getIsAvatarRemoved() {
		Boolean isRemoved = this.savedState.get(KEY_AVATAR_REMOVED);
		if (isRemoved != null) {
			return isRemoved;
		}
		return false;
	}

	public void setIsAvatarRemoved(boolean isRemoved) {
		this.savedState.set(KEY_AVATAR_REMOVED, isRemoved);
	}

	public String getGroupName() {
		return this.savedState.get(KEY_GROUP_NAME);
	}


	public void setGroupName(String groupName) {
		this.savedState.set(KEY_GROUP_NAME, groupName);
	}

	public List<ContactModel> getGroupContacts() {
		return addGroupMembersToList(new ArrayList<>(), getGroupIdentities());
	}

	public String[] getGroupIdentities() {
		return this.savedState.get(KEY_GROUP_IDENTITIES);
	}

	public void setGroupContacts(List<ContactModel> groupContacts) {
		setGroupIdentities(getIdentitiesFromContactModels(groupContacts));
	}

	public void removeGroupContact(ContactModel contactModel) {
		List<ContactModel> contactModels = getGroupContacts();
		contactModels.remove(contactModel);
		setGroupContacts(contactModels);
	}

	public void addGroupContacts(@Nullable String[] contactIdentities) {
		if (contactIdentities != null && contactIdentities.length > 0) {
			setGroupContacts(addGroupMembersToList(getGroupContacts(), contactIdentities));
		}
	}

	public void setGroupIdentities(String[] groupIdentities) {
		this.savedState.set(KEY_GROUP_IDENTITIES, groupIdentities);
		onDataChanged();
	}

	private String[] getIdentitiesFromContactModels(@NonNull List<ContactModel> groupContacts) {
		final ArrayList<String> identities = new ArrayList<>();

		for (ContactModel groupContact : groupContacts) {
			identities.add(groupContact.getIdentity());
		}
		return identities.toArray(new String[identities.size()]);
	}

	private List<ContactModel> addGroupMembersToList(@NonNull List<ContactModel> contacts, @Nullable String[] contactIds) {
		if (contactIds != null && contactIds.length > 0) {
			for (String contactId : contactIds) {
				if (!containsModel(contacts, contactId)) {
					contacts.add(contactService.getByIdentity(contactId));
				}
			}
		}
		return contacts;
	}

	public boolean containsModel(List<ContactModel> contacts, String contactId) {
		// prevent duplicates - we can't compare models
		if (contacts != null && contacts.size() > 0) {
			for (ContactModel contact : contacts) {
				if (contact.getIdentity().equals(contactId)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean containsModel(@NonNull String contactId) {
		String[] identities = getGroupIdentities();

		if (identities != null) {
			return Arrays.asList(getGroupIdentities()).contains(contactId);
		}
		return false;
	}

	public LiveData<List<ContactModel>> getGroupMembers() {
		return this.groupMembers;
	}

	@SuppressLint("StaticFieldLeak")
	public void onDataChanged() {
		new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... strings) {
				groupMembers.postValue(getGroupContacts());
				return null;
			}
		}.execute();
	}


	public GroupDetailViewModel setGroupDesc(String groupDesc) {
		this.savedState.set(KEY_GROUP_DESC, groupDesc);
		return this;
	}


	public GroupDetailViewModel setGroupDescTimestamp(Date groupDescDate) {
		this.savedState.set(KEY_GROUP_DESC_TIMESTAMP, groupDescDate);
		return this;
	}


	public String getGroupDesc() {
		return this.savedState.get(KEY_GROUP_DESC);
	}


	public Date getGroupDescTimestamp() {
		return this.savedState.get(KEY_GROUP_DESC_TIMESTAMP);
	}


	public GroupDetailViewModel setGroupDescState(GroupDetailAdapter.GroupDescState groupDescState) {
		this.savedState.set(KEY_GROUP_DESC_STATE, groupDescState);
		return this;
	}


	public GroupDetailAdapter.GroupDescState getGroupDescState() {
		return savedState.get(KEY_GROUP_DESC_STATE);
	}
}

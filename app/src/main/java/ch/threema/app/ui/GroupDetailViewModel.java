/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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
import ch.threema.app.adapters.GroupDetailAdapter;
import ch.threema.app.services.ContactService;
import ch.threema.data.models.GroupModel;
import ch.threema.data.models.GroupModelData;
import ch.threema.storage.models.ContactModel;

public class GroupDetailViewModel extends ViewModel {
    private static final String KEY_AVATAR_FILE = "avatar";
    private static final String KEY_GROUP_NAME = "name";
    private static final String KEY_GROUP_IDENTITIES = "contacts";
    private static final String KEY_AVATAR_REMOVED = "isRemoved";
    private static final String KEY_GROUP_DESC = "description";
    private static final String KEY_GROUP_DESC_TIMESTAMP = "descTimestamp";
    private static final String KEY_GROUP_DESC_STATE = "descState";

    private final SavedStateHandle savedState;
    @NonNull
    private final ContactService contactService;
    private final MutableLiveData<List<ContactModel>> groupMembers;

    private boolean hasMemberChanges = false;
    private boolean hasAvatarChanges = false;

    public GroupDetailViewModel(
        SavedStateHandle savedStateHandle,
        @NonNull ContactService contactService
    ) {
        savedState = savedStateHandle;
        this.contactService = contactService;
        this.groupMembers = new MutableLiveData<>() {
            @Nullable
            @Override
            public List<ContactModel> getValue() {
                return getGroupContacts();
            }
        };
    }

    @Nullable
    public LiveData<GroupModelData> group;

    public void setGroup(@NonNull GroupModel group) {
        this.group = group.liveData();
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
        hasAvatarChanges = true;
    }

    /**
     * Get the group name. If there is no group name available, an empty string is returned.
     */
    @NonNull
    public String getGroupName() {
        String savedGroupName = this.savedState.get(KEY_GROUP_NAME);
        if (savedGroupName == null) {
            return "";
        } else {
            return savedGroupName.trim();
        }
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
        hasMemberChanges = true;
    }

    public void addGroupContacts(@Nullable String[] contactIdentities) {
        if (contactIdentities != null && contactIdentities.length > 0) {
            setGroupContacts(addGroupMembersToList(getGroupContacts(), contactIdentities));
            hasMemberChanges = true;
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
        if (contacts != null && !contacts.isEmpty()) {
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
            return Arrays.asList(identities).contains(contactId);
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

    public void setGroupDesc(String groupDesc) {
        this.savedState.set(KEY_GROUP_DESC, groupDesc);
    }

    public void setGroupDescTimestamp(Date groupDescDate) {
        this.savedState.set(KEY_GROUP_DESC_TIMESTAMP, groupDescDate);
    }

    public String getGroupDesc() {
        return this.savedState.get(KEY_GROUP_DESC);
    }

    public Date getGroupDescTimestamp() {
        return this.savedState.get(KEY_GROUP_DESC_TIMESTAMP);
    }

    public void setGroupDescState(GroupDetailAdapter.GroupDescState groupDescState) {
        this.savedState.set(KEY_GROUP_DESC_STATE, groupDescState);
    }

    public GroupDetailAdapter.GroupDescState getGroupDescState() {
        return savedState.get(KEY_GROUP_DESC_STATE);
    }

    public boolean hasAvatarChanges() {
        return hasAvatarChanges;
    }

    public boolean hasMemberChanges() {
        return hasMemberChanges;
    }
}

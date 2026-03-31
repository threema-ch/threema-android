package ch.threema.app.ui;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import ch.threema.app.adapters.GroupDetailAdapter;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.DisplayableContactOrUser;
import ch.threema.app.utils.DisplayableGroupParticipant;
import ch.threema.data.models.GroupModel;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.repositories.ContactModelRepository;

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
    private final ContactModelRepository contactModelRepository;
    @NonNull
    private final PreferenceService preferenceService;
    @NonNull
    private final UserService userService;
    private final MutableLiveData<List<DisplayableGroupParticipant>> groupMembers;

    private boolean hasMemberChanges = false;
    private boolean hasAvatarChanges = false;

    public GroupDetailViewModel(
        SavedStateHandle savedStateHandle,
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull PreferenceService preferenceService,
        @NonNull UserService userService
    ) {
        savedState = savedStateHandle;
        this.contactModelRepository = contactModelRepository;
        this.preferenceService = preferenceService;
        this.userService = userService;
        this.groupMembers = new MutableLiveData<>() {
            @Nullable
            @Override
            public List<DisplayableGroupParticipant> getValue() {
                return getDisplayableGroupParticipants();
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

    public List<DisplayableGroupParticipant> getDisplayableGroupParticipants() {
        return addGroupMembersToList(new ArrayList<>(), getGroupIdentities());
    }

    public String[] getGroupIdentities() {
        return this.savedState.get(KEY_GROUP_IDENTITIES);
    }

    public void setGroupMembers(List<DisplayableGroupParticipant> groupContacts) {
        setGroupIdentities(getIdentitiesFromDisplayableGroupParticipants(groupContacts));
    }

    public void removeGroupContact(@Nullable String identity) {
        List<DisplayableGroupParticipant> groupParticipants = getDisplayableGroupParticipants();
        groupParticipants.removeIf(displayableGroupMember -> displayableGroupMember.getDisplayableContactOrUser().getIdentity().equals(identity));
        setGroupMembers(groupParticipants);
        hasMemberChanges = true;
    }

    public void addGroupContacts(@Nullable String[] contactIdentities) {
        if (contactIdentities != null && contactIdentities.length > 0) {
            setGroupMembers(addGroupMembersToList(getDisplayableGroupParticipants(), contactIdentities));
            hasMemberChanges = true;
        }
    }

    public void setGroupIdentities(String[] groupIdentities) {
        this.savedState.set(KEY_GROUP_IDENTITIES, groupIdentities);
        onDataChanged();
    }

    private String[] getIdentitiesFromDisplayableGroupParticipants(@NonNull List<DisplayableGroupParticipant> displayableGroupParticipants) {
        final ArrayList<String> identities = new ArrayList<>();

        for (DisplayableGroupParticipant displayableGroupParticipant : displayableGroupParticipants) {
            identities.add(displayableGroupParticipant.getDisplayableContactOrUser().getIdentity());
        }
        return identities.toArray(new String[0]);
    }

    private List<DisplayableGroupParticipant> addGroupMembersToList(@NonNull List<DisplayableGroupParticipant> groupMembers, @Nullable String[] contactIdentities) {
        if (contactIdentities != null) {
            for (String identity : contactIdentities) {
                if (!containsModel(groupMembers, identity)) {
                    DisplayableContactOrUser displayableContactOrUser;
                    if (identity.equals(userService.getIdentity())) {
                        displayableContactOrUser = DisplayableContactOrUser.User.createByIdentity(userService);
                    } else {
                        displayableContactOrUser = DisplayableContactOrUser.Contact.createByIdentity(
                            identity,
                            contactModelRepository,
                            preferenceService
                        );
                    }

                    DisplayableGroupParticipant displayableGroupParticipant;
                    GroupModelData groupModelData = group != null ? group.getValue() : null;
                    if (groupModelData == null) {
                        return Collections.emptyList();
                    }
                    if (groupModelData.groupIdentity.getCreatorIdentity().equals(identity)) {
                        displayableGroupParticipant = new DisplayableGroupParticipant.Creator(
                            displayableContactOrUser
                        );
                    } else {
                        displayableGroupParticipant = new DisplayableGroupParticipant.Member(
                            displayableContactOrUser
                        );
                    }

                    groupMembers.add(displayableGroupParticipant);
                }
            }
        }
        return groupMembers;
    }

    public boolean containsModel(List<DisplayableGroupParticipant> groupMembers, String identity) {
        // prevent duplicates - we can't compare models
        if (groupMembers != null && !groupMembers.isEmpty()) {
            for (DisplayableGroupParticipant groupMember : groupMembers) {
                if (groupMember.getDisplayableContactOrUser().getIdentity().equals(identity)) {
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

    public LiveData<List<DisplayableGroupParticipant>> getGroupMembers() {
        return this.groupMembers;
    }

    @SuppressLint("StaticFieldLeak")
    public void onDataChanged() {
        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... strings) {
                groupMembers.postValue(getDisplayableGroupParticipants());
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

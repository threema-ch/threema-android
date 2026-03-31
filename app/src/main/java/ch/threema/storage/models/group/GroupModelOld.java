package ch.threema.storage.models.group;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import java.util.Date;
import java.util.Objects;

import ch.threema.data.datatypes.IdColor;
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride;
import ch.threema.base.utils.Utils;
import ch.threema.data.models.GroupIdentity;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.GroupReceiverIdentifier;
import ch.threema.domain.models.UserState;
import ch.threema.storage.models.ReceiverModel;

@Deprecated
public class GroupModelOld implements ReceiverModel {

    public static final int GROUP_NAME_MAX_LENGTH_BYTES = 256;

    public static final String TABLE = "m_group";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_API_GROUP_ID = "apiGroupId";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_CREATOR_IDENTITY = "creatorIdentity";
    public static final String COLUMN_CREATED_AT = "createdAt";
    public static final String COLUMN_SYNCHRONIZED_AT = "synchronizedAt";
    public static final String COLUMN_LAST_UPDATE = "lastUpdateAt"; /* date when the conversation was last updated */
    public static final String COLUMN_IS_ARCHIVED = "isArchived"; /* whether this group has been archived by user */
    public static final String COLUMN_GROUP_DESC = "groupDesc";
    public static final String COLUMN_GROUP_DESC_CHANGED_TIMESTAMP = "changedGroupDescTimestamp";
    public static final String COLUMN_COLOR_INDEX = "colorIndex";
    public static final String COLUMN_USER_STATE = "userState";
    public static final String COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE = "notificationTriggerPolicyOverride";

    private String groupDesc;
    private Date changedGroupDescTimestamp;

    private int id;
    private GroupId apiGroupId;
    private String name;
    private String creatorIdentity;
    private Date createdAt;
    private Date synchronizedAt;
    private @Nullable Date lastUpdate;
    private boolean isArchived;
    private @NonNull IdColor idColor = IdColor.invalid();
    private @Nullable UserState userState;
    private @Nullable Long notificationTriggerPolicyOverride;

    @Nullable
    public String getName() {
        return this.name;
    }

    public GroupModelOld setName(@Nullable String name) {
        this.name = Utils.truncateUTF8String(name, GROUP_NAME_MAX_LENGTH_BYTES);
        return this;
    }

    public int getId() {
        return this.id;
    }

    public GroupModelOld setId(int id) {
        this.id = id;
        return this;
    }

    public GroupModelOld setApiGroupId(GroupId apiGroupId) {
        this.apiGroupId = apiGroupId;
        return this;
    }

    public @NonNull GroupId getApiGroupId() {
        return this.apiGroupId;
    }

    public String getCreatorIdentity() {
        return this.creatorIdentity;
    }

    public GroupModelOld setCreatorIdentity(String creatorIdentity) {
        this.creatorIdentity = creatorIdentity;
        return this;
    }

    public Date getCreatedAt() {
        return this.createdAt;
    }

    public GroupModelOld setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    @Override
    public GroupModelOld setLastUpdate(@Nullable Date lastUpdate) {
        this.lastUpdate = lastUpdate;
        return this;
    }

    @Override
    public @Nullable Date getLastUpdate() {
        // Note: Never return null for groups, they should always be visible
        return this.lastUpdate == null ? new Date(0) : this.lastUpdate;
    }

    public Date getSynchronizedAt() {
        return this.synchronizedAt;
    }

    public GroupModelOld setSynchronizedAt(Date synchronizedAt) {
        this.synchronizedAt = synchronizedAt;
        return this;
    }

    @Override
    public boolean isArchived() {
        return isArchived;
    }

    public GroupModelOld setArchived(boolean archived) {
        isArchived = archived;
        return this;
    }

    @Override
    public boolean isHidden() {
        // Groups can't currently be hidden from the conversation list
        return false;
    }

    @NonNull
    @Override
    public GroupReceiverIdentifier getIdentifier() {
        return new GroupReceiverIdentifier(
            id,
            creatorIdentity,
            apiGroupId.toLong()
        );
    }

    public IdColor getIdColor() {
        if (!idColor.isValid()) {
            idColor = IdColor.ofGroup(getGroupIdentity());
        }
        return idColor;
    }

    public GroupModelOld setColorIndex(int colorIndex) {
        this.idColor = new IdColor(colorIndex);
        return this;
    }

    public GroupModelOld setGroupDesc(String description) {
        groupDesc = description;
        return this;
    }

    public GroupModelOld setGroupDescTimestamp(Date groupDescDate) {
        changedGroupDescTimestamp = groupDescDate;
        return this;
    }

    public String getGroupDesc() {
        return this.groupDesc;
    }

    public Date getGroupDescTimestamp() {
        return this.changedGroupDescTimestamp;
    }

    @NonNull
    public GroupModelOld setUserState(@Nullable UserState userState) {
        this.userState = userState;
        return this;
    }

    @Nullable
    public UserState getUserState() {
        return userState;
    }

    @NonNull
    public GroupModelOld setNotificationTriggerPolicyOverride(@Nullable final Long notificationTriggerPolicyOverride) {
        this.notificationTriggerPolicyOverride = notificationTriggerPolicyOverride;
        return this;
    }

    @Nullable
    public Long getNotificationTriggerPolicyOverride() {
        return notificationTriggerPolicyOverride;
    }

    @NonNull
    public NotificationTriggerPolicyOverride currentNotificationTriggerPolicyOverride() {
        return NotificationTriggerPolicyOverride.fromDbValueGroup(notificationTriggerPolicyOverride);
    }

    public GroupIdentity getGroupIdentity() {
        return new GroupIdentity(
            creatorIdentity,
            apiGroupId.toLong()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupModelOld)) return false;
        GroupModelOld that = (GroupModelOld) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

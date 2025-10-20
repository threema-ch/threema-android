/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.storage.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import java.util.Date;
import java.util.Objects;

import ch.threema.data.datatypes.IdColor;
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride;
import ch.threema.base.utils.Utils;
import ch.threema.data.models.GroupIdentity;
import ch.threema.domain.models.GroupId;

public class GroupModel implements ReceiverModel {

    public static final int GROUP_NAME_MAX_LENGTH_BYTES = 256;

    public static final String TABLE = "m_group";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_API_GROUP_ID = "apiGroupId";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_CREATOR_IDENTITY = "creatorIdentity";
    public static final String COLUMN_CREATED_AT = "createdAt";
    public static final String COLUMN_SYNCHRONIZED_AT = "synchronizedAt";
    public static final String COLUMN_LAST_UPDATE = "lastUpdate"; /* date when the conversation was last updated */
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

    /**
     * The user's state within the group.
     */
    public enum UserState {

        MEMBER(0),

        KICKED(1),

        LEFT(2);

        public final int value;

        UserState(int value) {
            this.value = value;
        }

        @Nullable
        public static UserState valueOf(int value) {
            for (UserState userState : values()) {
                if (userState.value == value) {
                    return userState;
                }
            }

            return null;
        }

    }

    // dummy class
    @Nullable
    public String getName() {
        return this.name;
    }

    public GroupModel setName(@Nullable String name) {
        this.name = Utils.truncateUTF8String(name, GROUP_NAME_MAX_LENGTH_BYTES);
        return this;
    }

    public int getId() {
        return this.id;
    }

    public GroupModel setId(int id) {
        this.id = id;
        return this;
    }

    public GroupModel setApiGroupId(GroupId apiGroupId) {
        this.apiGroupId = apiGroupId;
        return this;
    }

    public @NonNull GroupId getApiGroupId() {
        return this.apiGroupId;
    }

    public String getCreatorIdentity() {
        return this.creatorIdentity;
    }

    public GroupModel setCreatorIdentity(String creatorIdentity) {
        this.creatorIdentity = creatorIdentity;
        return this;
    }

    public Date getCreatedAt() {
        return this.createdAt;
    }

    public GroupModel setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    @Override
    public GroupModel setLastUpdate(@Nullable Date lastUpdate) {
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

    public GroupModel setSynchronizedAt(Date synchronizedAt) {
        this.synchronizedAt = synchronizedAt;
        return this;
    }

    @Override
    public boolean isArchived() {
        return isArchived;
    }

    public GroupModel setArchived(boolean archived) {
        isArchived = archived;
        return this;
    }

    @Override
    public boolean isHidden() {
        // Groups can't currently be hidden from the conversation list
        return false;
    }

    public IdColor getIdColor() {
        if (!idColor.isValid()) {
            idColor = IdColor.ofGroup(getGroupIdentity());
        }
        return idColor;
    }

    public GroupModel setColorIndex(int colorIndex) {
        this.idColor = new IdColor(colorIndex);
        return this;
    }

    public GroupModel setGroupDesc(String description) {
        groupDesc = description;
        return this;
    }

    public GroupModel setGroupDescTimestamp(Date groupDescDate) {
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
    public GroupModel setUserState(@Nullable UserState userState) {
        this.userState = userState;
        return this;
    }

    @Nullable
    public UserState getUserState() {
        return userState;
    }

    @NonNull
    public GroupModel setNotificationTriggerPolicyOverride(@Nullable final Long notificationTriggerPolicyOverride) {
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
        if (!(o instanceof GroupModel)) return false;
        GroupModel that = (GroupModel) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}


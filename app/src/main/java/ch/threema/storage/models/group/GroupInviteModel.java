/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.storage.models.group;

import java.util.Date;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.models.GroupId;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;

public class GroupInviteModel {
    public static final String
        TABLE = "group_invite_model",
        COLUMN_ID = "group_invite_index_id",
        COLUMN_GROUP_ID = "group_id",
        COLUMN_DEFAULT_FLAG = "default_flag",
        COLUMN_TOKEN = "token",
        COLUMN_ORIGINAL_GROUP_NAME = "original_group_name",
        COLUMN_INVITE_NAME = "invite_name",
        COLUMN_MANUAL_CONFIRMATION = "manual_confirmation",
        COLUMN_EXPIRATION_DATE = "expiration_date",
        COLUMN_IS_INVALIDATED = "is_invalidated"; //flag to mark as deleted in UI

    private int id;
    private final GroupId groupApiId;
    private final @NonNull GroupInviteToken token;
    private final @NonNull String originalGroupName;
    private final @NonNull String inviteName;
    private final @Nullable Date expirationDate;
    private final boolean isInvalidated;
    private final boolean manualConfirmation;
    private final boolean isDefault;

    private GroupInviteModel(Builder builder) {
        this.id = builder.id;
        this.groupApiId = builder.groupApiId;
        this.token = builder.token;
        this.inviteName = builder.inviteName;
        this.originalGroupName = builder.originalGroupName;
        this.manualConfirmation = builder.manualConfirmation;
        this.expirationDate = builder.expirationDate;
        this.isInvalidated = builder.isInvalidated;
        this.isDefault = builder.isDefault;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public GroupId getGroupApiId() {
        return this.groupApiId;
    }

    public @NonNull GroupInviteToken getToken() {
        return this.token;
    }

    public @NonNull String getOriginalGroupName() {
        return this.originalGroupName;
    }

    public boolean getManualConfirmation() {
        return this.manualConfirmation;
    }

    public @Nullable Date getExpirationDate() {
        return this.expirationDate;
    }

    public @NonNull String getInviteName() {
        return inviteName;
    }

    public boolean isInvalidated() {
        return isInvalidated;
    }

    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        final GroupInviteModel that = (GroupInviteModel) o;
        return this.id == that.id &&
            this.groupApiId == that.groupApiId &&
            this.manualConfirmation == that.manualConfirmation &&
            this.token.equals(that.token) &&
            this.originalGroupName.equals(that.originalGroupName) &&
            Objects.equals(this.expirationDate, that.expirationDate) &&
            this.isInvalidated == that.isInvalidated &&
            this.isDefault == that.isDefault;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.groupApiId, this.token, this.originalGroupName,
            this.manualConfirmation, this.expirationDate, this.isInvalidated);
    }

    public static class Builder {
        private int id;
        private GroupId groupApiId;
        private boolean isDefault;
        private GroupInviteToken token;
        private String originalGroupName;
        private String inviteName;
        private boolean manualConfirmation;
        private Date expirationDate;
        private boolean isInvalidated;

        public Builder() {
        }

        public Builder(GroupInviteModel model) {
            this.id = model.id;
            this.groupApiId = model.groupApiId;
            this.isDefault = model.isDefault;
            this.token = model.token;
            this.originalGroupName = model.originalGroupName;
            this.inviteName = model.inviteName;
            this.manualConfirmation = model.manualConfirmation;
            this.expirationDate = model.expirationDate;
            this.isInvalidated = model.isInvalidated;
        }

        public Builder withId(int id) {
            this.id = id;
            return this;
        }

        public Builder withGroupApiId(GroupId groupId) {
            this.groupApiId = groupId;
            return this;
        }

        public Builder withToken(GroupInviteToken token) {
            this.token = token;
            return this;
        }

        public Builder withGroupName(String groupName) {
            this.originalGroupName = groupName;
            return this;
        }

        public Builder withInviteName(String inviteName) {
            this.inviteName = inviteName;
            return this;
        }

        public Builder withManualConfirmation(boolean manualConfirmation) {
            this.manualConfirmation = manualConfirmation;
            return this;
        }

        public Builder withExpirationDate(Date expirationDate) {
            this.expirationDate = expirationDate;
            return this;
        }

        public Builder setIsInvalidated(boolean isInvalidated) {
            this.isInvalidated = isInvalidated;
            return this;
        }

        public Builder setIsDefault(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        public GroupInviteModel build() throws MissingRequiredArgumentsException {
            if (!TestUtil.requireAll(new Object[]{this.token, this.originalGroupName, this.inviteName})) {
                throw new MissingRequiredArgumentsException("Not all required params set. Requires token, group name and invite name");
            }
            return new GroupInviteModel(this);
        }
    }

    public static class MissingRequiredArgumentsException extends ThreemaException {
        public MissingRequiredArgumentsException(final String msg) {
            super(msg);
        }
    }
}

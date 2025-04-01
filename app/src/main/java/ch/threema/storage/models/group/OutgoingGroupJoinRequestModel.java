/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.models.GroupId;

public class OutgoingGroupJoinRequestModel {
    public static final String
        TABLE = "group_join_request",
        COLUMN_ID = "outgoing_request_index_id",
        COLUMN_INVITE_TOKEN = "token",
        COLUMN_GROUP_NAME = "group_name",
        COLUMN_MESSAGE = "message",
        COLUMN_INVITE_ADMIN_IDENTITY = "admin_identity",
        COLUMN_REQUEST_TIME = "request_time",
        COLUMN_STATUS = "status",
        COLUMN_GROUP_API_ID = "group_api_id";

    private int id;

    private final @NonNull String inviteToken;
    private final @NonNull String groupName;
    private final @NonNull String message;
    private final @NonNull String adminIdentity;
    private final @NonNull Date requestTime;
    private final @NonNull Status status;
    private final @Nullable GroupId groupApiId;

    public enum Status {
        UNKNOWN,
        ACCEPTED,
        REJECTED,
        GROUP_FULL,
        EXPIRED;

        private static OutgoingGroupJoinRequestModel.Status[] allValues = values();

        public static OutgoingGroupJoinRequestModel.Status fromOrdinal(int n) {
            return allValues[n];
        }

        public static OutgoingGroupJoinRequestModel.Status fromString(String status) {
            switch (status) {
                case "UNKNOWN":
                    return UNKNOWN;
                case "ACCEPTED":
                    return ACCEPTED;
                case "REJECTED":
                    return REJECTED;
                case "GROUP_FULL":
                    return GROUP_FULL;
                case "EXPIRED":
                    return EXPIRED;
                default:
                    return UNKNOWN;
            }
        }
    }

    public OutgoingGroupJoinRequestModel(
        int id,
        @NonNull String inviteToken,
        @NonNull String groupName,
        @NonNull String message,
        @NonNull String adminIdentity,
        @NonNull Date requestTime,
        @NonNull Status status,
        @Nullable GroupId groupApiId
    ) {
        this.id = id;
        this.inviteToken = inviteToken;
        this.groupName = groupName;
        this.message = message;
        this.adminIdentity = adminIdentity;
        this.requestTime = requestTime;
        this.status = status;
        this.groupApiId = groupApiId;
    }

    public OutgoingGroupJoinRequestModel(Builder builder) {
        this.id = builder.id;
        this.inviteToken = builder.inviteToken;
        this.groupName = builder.groupName;
        this.message = builder.message;
        this.adminIdentity = builder.adminIdentity;
        this.requestTime = builder.requestTime;
        this.status = builder.status;
        this.groupApiId = builder.groupApiId;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public @NonNull String getInviteToken() {
        return this.inviteToken;
    }

    public @NonNull String getGroupName() {
        return this.groupName;
    }

    public @NonNull String getMessage() {
        return this.message;
    }

    public @NonNull String getAdminIdentity() {
        return this.adminIdentity;
    }

    public @NonNull Date getRequestTime() {
        return this.requestTime;
    }

    public @NonNull Status getStatus() {
        return this.status;
    }

    public @Nullable GroupId getGroupApiId() {
        return this.groupApiId;
    }

    public static class Builder {
        private int id;
        private String inviteToken;
        private String groupName;
        private String message;
        private String adminIdentity;
        private Date requestTime;
        private Status status;
        private GroupId groupApiId;

        public Builder() {
        }

        public Builder(OutgoingGroupJoinRequestModel model) {
            this.id = model.id;
            this.inviteToken = model.inviteToken;
            this.groupName = model.groupName;
            this.message = model.message;
            this.adminIdentity = model.adminIdentity;
            this.requestTime = model.requestTime;
            this.status = model.status;
            this.groupApiId = model.groupApiId;
        }

        public OutgoingGroupJoinRequestModel.Builder withId(int id) {
            this.id = id;
            return this;
        }

        public OutgoingGroupJoinRequestModel.Builder withInviteToken(String inviteToken) {
            this.inviteToken = inviteToken;
            return this;
        }

        public OutgoingGroupJoinRequestModel.Builder withGroupName(String groupName) {
            this.groupName = groupName;
            return this;
        }

        public OutgoingGroupJoinRequestModel.Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public OutgoingGroupJoinRequestModel.Builder withAdminIdentity(String adminIdentity) {
            this.adminIdentity = adminIdentity;
            return this;
        }

        public OutgoingGroupJoinRequestModel.Builder withRequestTime(Date requestTime) {
            this.requestTime = requestTime;
            return this;
        }

        public OutgoingGroupJoinRequestModel.Builder withResponseStatus(OutgoingGroupJoinRequestModel.Status status) {
            this.status = status;
            return this;
        }

        public OutgoingGroupJoinRequestModel.Builder withGroupApiId(GroupId groupApiId) {
            this.groupApiId = groupApiId;
            return this;
        }

        public OutgoingGroupJoinRequestModel build() {
            return new OutgoingGroupJoinRequestModel(this);
        }
    }
}

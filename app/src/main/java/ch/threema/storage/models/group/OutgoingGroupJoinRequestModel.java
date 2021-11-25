/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021 Threema GmbH
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
		TABLE ="group_join_request",
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

	public enum Status {
		UNKNOWN,
		ACCEPTED,
		REJECTED,
		GROUP_FULL,
		EXPIRED
	}

}

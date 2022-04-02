/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

import java.io.Serializable;
import java.util.Date;

import androidx.annotation.NonNull;

public class IncomingGroupJoinRequestModel implements Serializable {
	public static final String
		TABLE ="incoming_group_join_request",
		COLUMN_ID = "incoming_request_index_id",
		COLUMN_GROUP_INVITE = "group_invite",
		COLUMN_MESSAGE = "message",
		COLUMN_REQUESTING_IDENTITY = "requesting_identity",
		COLUMN_REQUEST_TIME = "request_time",
	    COLUMN_RESPONSE_STATUS = "response_status";

	private int id;
	private final int groupInviteId;
	private final @NonNull String message;
	private final @NonNull String requestingIdentity;
	private final @NonNull Date requestTime;
	private final @NonNull ResponseStatus responseStatus;

	public IncomingGroupJoinRequestModel(
		int id,
		int groupInviteId,
		@NonNull String message,
		@NonNull String requestingIdentity,
		@NonNull Date requestTime,
		@NonNull ResponseStatus responseStatus
	) {
		this.id = id;
		this.groupInviteId = groupInviteId;
		this.message = message;
		this.requestingIdentity = requestingIdentity;
		this.requestTime = requestTime;
		this.responseStatus = responseStatus;
	}

	private IncomingGroupJoinRequestModel(
		Builder builder
	) {
		this.id = builder.id;
		this.groupInviteId = builder.groupInviteId;
		this.message = builder.message;
		this.requestingIdentity = builder.requestingIdentity;
		this.requestTime = builder.requestTime;
		this.responseStatus = builder.responseStatus;
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getGroupInviteId() {
		return this.groupInviteId;
	}

	public @NonNull String getMessage() {
		return this.message;
	}

	public @NonNull String getRequestingIdentity() {
		return this.requestingIdentity;
	}

	public @NonNull Date getRequestTime() {
		return this.requestTime;
	}

	public @NonNull	ResponseStatus getResponseStatus() {
		return this.responseStatus;
	}

	public enum ResponseStatus {
		OPEN,
		ACCEPTED,
		REJECTED,
		GROUP_FULL,
		EXPIRED;

		private static ResponseStatus[] allValues = values();
		public static ResponseStatus fromOrdinal(int n) {return allValues[n];}
		public static IncomingGroupJoinRequestModel.ResponseStatus fromString(String status) {
			switch (status){
				case "OPEN":
					return OPEN;
				case "ACCEPTED":
					return ACCEPTED;
				case "REJECTED":
					return REJECTED;
				case "GROUP_FULL":
					return GROUP_FULL;
				case "EXPIRED":
					return EXPIRED;
				default:
					return EXPIRED;
			}
		}
	}

	public static class Builder {
		private int id;
		private int groupInviteId;
		private String message;
		private String requestingIdentity;
		private Date requestTime;
		private ResponseStatus responseStatus;

		public Builder() { }

		public Builder(IncomingGroupJoinRequestModel model) {
			this.id = model.id;
			this.groupInviteId = model.groupInviteId;
			this.message = model.message;
			this.requestingIdentity = model.requestingIdentity;
			this.requestTime = model.requestTime;
			this.responseStatus = model.responseStatus;

		}

		public IncomingGroupJoinRequestModel.Builder withId(int id) {
			this.id = id;
			return this;
		}

		public IncomingGroupJoinRequestModel.Builder withGroupInviteId(int groupInviteId) {
			this.groupInviteId = groupInviteId;
			return this;
		}

		public IncomingGroupJoinRequestModel.Builder withMessage(String message) {
			this.message = message;
			return this;
		}

		public IncomingGroupJoinRequestModel.Builder withRequestingIdentity(String requestingIdentity) {
			this.requestingIdentity = requestingIdentity;
			return this;
		}

		public IncomingGroupJoinRequestModel.Builder withRequestTime(Date requestTime) {
			this.requestTime = requestTime;
			return this;
		}

		public IncomingGroupJoinRequestModel.Builder withResponseStatus(ResponseStatus responseStatus) {
			this.responseStatus = responseStatus;
			return this;
		}

		public IncomingGroupJoinRequestModel build() {
			return new IncomingGroupJoinRequestModel(this);
		}
	}

}

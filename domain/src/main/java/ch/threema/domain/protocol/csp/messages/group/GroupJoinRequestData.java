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

package ch.threema.domain.protocol.csp.messages.group;

import androidx.annotation.NonNull;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface;
import ch.threema.protobuf.csp.e2e.GroupJoinRequest;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;

public class GroupJoinRequestData implements ProtobufDataInterface<GroupJoinRequest> {

	private final @NonNull GroupInviteToken token;
	private final @NonNull String groupName;
	private final @NonNull String message;

	/**
	 *
	 * @param token Join Invite Token
	 * @param groupName Name of the joining group
	 * @param message Join request message, provided by user
	 * @throws BadMessageException if invalid data was provided
	 */
	public GroupJoinRequestData(@NonNull GroupInviteToken token, @NonNull  String groupName, @NonNull String message) {
		this.token = token;
		this.groupName = groupName;
		this.message = message;
	}

	@NonNull
	public GroupInviteToken getToken() {
		return this.token;
	}

	@NonNull
	public String getGroupName() {
		return this.groupName;
	}

	@NonNull
	public String getMessage() {
		return this.message;
	}

	//region Serialization

	public static GroupJoinRequestData fromProtobuf(@NonNull byte[] rawProtobufMessage) throws BadMessageException {
		try {
			GroupJoinRequest protobufMessage = GroupJoinRequest.parseFrom(rawProtobufMessage);
			return new GroupJoinRequestData(
				new GroupInviteToken(protobufMessage.getToken().toByteArray()),
				protobufMessage.getGroupName(),
				protobufMessage.getMessage()
			);
		} catch (InvalidProtocolBufferException e) {
			throw new BadMessageException("Invalid Group Join Request Protobuf Data", true);
		} catch (GroupInviteToken.InvalidGroupInviteTokenException e) {
			throw new BadMessageException("Invalid Group Invite Token Length", true);
		}
	}

	@Override
	public @NonNull GroupJoinRequest toProtobufMessage() {
		return GroupJoinRequest.newBuilder()
			.setToken(ByteString.copyFrom(this.token.get()))
			.setGroupName(this.groupName)
			.setMessage(this.message).build();
	}

	//endregion

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || this.getClass() != o.getClass()) return false;
		GroupJoinRequestData that = (GroupJoinRequestData) o;
		return this.token.equals(that.token) &&
			this.groupName.equals(that.groupName) &&
			this.message.equals(that.message);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.token, this.groupName, this.message);
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;

import androidx.annotation.NonNull;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface;
import ch.threema.protobuf.url_payloads.GroupInvite;

public class GroupInviteData implements ProtobufDataInterface<GroupInvite> {

	private final @NonNull String adminIdentity;
	private final @NonNull GroupInviteToken token;
	private final @NonNull String groupName;
	private final @NonNull
	GroupInvite.ConfirmationMode confirmationMode;

	/**
	 *
	 * @param adminIdentity Invite admin identity
	 * @param token Invite token
	 * @param groupName Group name
	 * @param confirmationMode if the requests through the invite have the be manually accepted or not
	 * @throws BadMessageException if invalid data was provided
	 */
	public GroupInviteData(@NonNull String adminIdentity, @NonNull GroupInviteToken token,
	                       @NonNull String groupName, @NonNull GroupInvite.ConfirmationMode confirmationMode) {
		this.adminIdentity = adminIdentity;
		this.token = token;
		this.groupName = groupName;
		this.confirmationMode = confirmationMode;
	}

	@NonNull
	public String getAdminIdentity() {
		return adminIdentity;
	}

	@NonNull
	public GroupInviteToken getToken() {
		return token;
	}

	@NonNull
	public String getGroupName() {
		return groupName;
	}

	@NonNull
	public GroupInvite.ConfirmationMode getConfirmationMode() {
		return confirmationMode;
	}

	@NonNull
	public static GroupInviteData fromProtobuf(@NonNull byte[] rawProtobufMessage) throws BadMessageException {
		try {
			GroupInvite protobufMessage = GroupInvite.parseFrom(rawProtobufMessage);
			return new GroupInviteData(
				protobufMessage.getAdminIdentity(),
				new GroupInviteToken(protobufMessage.getToken().toByteArray()),
				protobufMessage.getGroupName(),
				protobufMessage.getConfirmationMode()
			);
		} catch (InvalidProtocolBufferException e) {
			throw new BadMessageException("Invalid Group Join Request Protobuf Data", true);
		} catch (GroupInviteToken.InvalidGroupInviteTokenException e) {
			throw new BadMessageException("Invalid Group Invite Token Length", true);
		}
	}

	@NonNull
	@Override
	public GroupInvite toProtobufMessage() {
		return GroupInvite.newBuilder()
			.setAdminIdentity(this.adminIdentity)
			.setToken(ByteString.copyFrom(this.token.get()))
			.setGroupName(this.groupName)
			.setConfirmationMode(this.confirmationMode).build();
	}

	@Override
	public byte[] toProtobufBytes() {
		return new byte[0];
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || this.getClass() != o.getClass()) return false;
		GroupInviteData that = (GroupInviteData) o;
		return this.adminIdentity.equals(that.adminIdentity) &&
			this.token.equals(that.token) &&
			this.groupName.equals(that.groupName) &&
			this.confirmationMode.equals(that.confirmationMode);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.adminIdentity, this.token, this.groupName, this.confirmationMode);
	}
}

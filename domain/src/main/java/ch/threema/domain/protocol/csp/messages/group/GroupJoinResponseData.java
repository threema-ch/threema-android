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

package ch.threema.domain.protocol.csp.messages.group;

import androidx.annotation.NonNull;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface;
import ch.threema.protobuf.Common;
import ch.threema.protobuf.csp.e2e.GroupJoinResponse;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;

public class GroupJoinResponseData implements ProtobufDataInterface<GroupJoinResponse> {
    private final @NonNull GroupInviteToken token;
    private final @NonNull Response response;

    /**
     * @param token    Join Request Token
     * @param response type of the join response
     */
    public GroupJoinResponseData(@NonNull GroupInviteToken token, @NonNull Response response) {
        this.token = token;
        this.response = response;
    }

    @NonNull
    public GroupInviteToken getToken() {
        return this.token;
    }

    @NonNull
    public Response getResponse() {
        return this.response;
    }

    //region Response

    public interface Response {
        GroupJoinResponse.Builder addToProtobufBuilder(GroupJoinResponse.Builder builder);
    }

    public static class Accept implements Response {
        private final long groupId;


        public Accept(long groupId) {
            this.groupId = groupId;
        }

        public GroupJoinResponse.Builder addToProtobufBuilder(GroupJoinResponse.Builder builder) {
            return builder.setResponse(
                GroupJoinResponse.Response.newBuilder().setAccept(
                    GroupJoinResponse.Response.Accept.newBuilder().setGroupId(this.groupId)
                )
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || this.getClass() != o.getClass()) return false;
            Accept accept = (Accept) o;
            return this.groupId == accept.groupId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId);
        }

        public long getGroupId() {
            return this.groupId;
        }
    }

    public static class Expired implements Response {
        public GroupJoinResponse.Builder addToProtobufBuilder(GroupJoinResponse.Builder builder) {
            return builder.setResponse(GroupJoinResponse.Response.newBuilder().setExpired(
                Common.Unit.newBuilder()
            ));
        }

        @Override
        public int hashCode() {
            return 7;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Expired;
        }
    }

    public static class GroupFull implements Response {
        public GroupJoinResponse.Builder addToProtobufBuilder(GroupJoinResponse.Builder builder) {
            return builder.setResponse(GroupJoinResponse.Response.newBuilder().setGroupFull(
                Common.Unit.newBuilder()
            ));
        }

        @Override
        public int hashCode() {
            return 13;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof GroupFull;
        }
    }

    public static class Reject implements Response {
        public GroupJoinResponse.Builder addToProtobufBuilder(GroupJoinResponse.Builder builder) {
            return builder.setResponse(GroupJoinResponse.Response.newBuilder().setReject(
                Common.Unit.newBuilder()
            ));
        }

        @Override
        public int hashCode() {
            return 17;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Reject;
        }
    }

    //endregion


    //region Serialization

    public static GroupJoinResponseData fromProtobuf(@NonNull byte[] rawProtobufMessage) throws BadMessageException {
        try {
            GroupJoinResponse protobufMessage = GroupJoinResponse.parseFrom(rawProtobufMessage);

            final GroupJoinResponse.Response protobufResponse = protobufMessage.getResponse();
            final Response response;
            if (protobufResponse.hasAccept()) {
                response = new Accept(protobufResponse.getAccept().getGroupId());
            } else if (protobufResponse.hasExpired()) {
                response = new Expired();
            } else if (protobufResponse.hasGroupFull()) {
                response = new GroupFull();
            } else if (protobufResponse.hasReject()) {
                response = new Reject();
            } else {
                throw new BadMessageException("Invalid Group Join Response Data: Unknown Response");
            }

            final GroupInviteToken token = new GroupInviteToken(protobufMessage.getToken().toByteArray());
            return new GroupJoinResponseData(token, response);
        } catch (InvalidProtocolBufferException e) {
            throw new BadMessageException("Invalid Group Join Response Protobuf Data");
        } catch (GroupInviteToken.InvalidGroupInviteTokenException e) {
            throw new BadMessageException("Invalid Group Join Token Length");
        }
    }

    @Override
    public @NonNull GroupJoinResponse toProtobufMessage() {
        final GroupJoinResponse.Builder builder = GroupJoinResponse.newBuilder()
            .setToken(ByteString.copyFrom(this.token.get()));

        return this.response.addToProtobufBuilder(builder).build();
    }

    //endregion

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        final GroupJoinResponseData that = (GroupJoinResponseData) o;
        return this.token.equals(that.token) &&
            this.response.equals(that.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.token, this.response);
    }
}


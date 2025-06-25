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

package ch.threema.app.services.group;

import java.util.Date;

import androidx.annotation.NonNull;
import ch.threema.base.Result;
import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteData;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestData;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.factories.OutgoingGroupJoinRequestModelFactory;
import ch.threema.storage.models.group.OutgoingGroupJoinRequestModel;
import java8.util.Optional;

public class OutgoingGroupJoinRequestServiceImpl implements OutgoingGroupJoinRequestService {

    private final @NonNull OutgoingGroupJoinRequestModelFactory outgoingGroupJoinRequestModelFactory;

    public OutgoingGroupJoinRequestServiceImpl(
        @NonNull final DatabaseService databaseService
    ) {
        this.outgoingGroupJoinRequestModelFactory = databaseService.getOutgoingGroupJoinRequestModelFactory();
    }

    /**
     * Sends a join request for a corresponding group invite with a join request message for the group admin
     *
     * @param groupInvite    GroupInvite for which a request is sent
     * @param requestMessage String join request message
     */
    @Override
    public void send(
        @NonNull GroupInviteData groupInvite,
        @NonNull String requestMessage) throws ThreemaException {

        final GroupJoinRequestMessage message = new GroupJoinRequestMessage(new GroupJoinRequestData(
            GroupInviteToken.fromHexString(groupInvite.getToken().toString()),
            groupInvite.getGroupName(),
            requestMessage)
        );

        Optional<OutgoingGroupJoinRequestModel> previousRequest = this.outgoingGroupJoinRequestModelFactory
            .getByInviteToken(groupInvite.getToken().toString());

        // reset state if resending a request else create and persists new request
        if (previousRequest.isPresent()) {
            this.outgoingGroupJoinRequestModelFactory
                .updateStatusAndSentDate(previousRequest.get(), OutgoingGroupJoinRequestModel.Status.UNKNOWN);
        } else {
            final Result<OutgoingGroupJoinRequestModel, Exception> insertResult =
                this.outgoingGroupJoinRequestModelFactory.insert(
                    new OutgoingGroupJoinRequestModel(-1,
                        groupInvite.getToken().toString(),
                        groupInvite.getGroupName(),
                        requestMessage,
                        groupInvite.getAdminIdentity(),
                        new Date(),
                        OutgoingGroupJoinRequestModel.Status.UNKNOWN,
                        null)
                );

            if (insertResult.isFailure()) {
                throw new ThreemaException("Database insertion failed {}", insertResult.getError());
            }
        }
        message.setToIdentity(groupInvite.getAdminIdentity());
        // TODO(ANDR-2607): message was enqueued here in the message queue. Create a task for this.
    }

    /**
     * Resend a join request for a existing outgoing request with a new request message for the group admin
     *
     * @param groupJoinRequestModel OutgoingGroupJoinRequestModel for which a request is resent
     * @param requestMessage        String join request message
     */
    @Override
    public void resendRequest(
        @NonNull OutgoingGroupJoinRequestModel groupJoinRequestModel,
        @NonNull String requestMessage) throws ThreemaException {

        final GroupJoinRequestMessage message = new GroupJoinRequestMessage(new GroupJoinRequestData(
            GroupInviteToken.fromHexString(groupJoinRequestModel.getInviteToken()),
            groupJoinRequestModel.getGroupName(),
            requestMessage)
        );

        Optional<OutgoingGroupJoinRequestModel> previousRequest = this.outgoingGroupJoinRequestModelFactory
            .getByInviteToken(groupJoinRequestModel.getInviteToken());

        // reset state if request is actually there
        if (previousRequest.isPresent()) {
            this.outgoingGroupJoinRequestModelFactory
                .updateStatusAndSentDate(previousRequest.get(), OutgoingGroupJoinRequestModel.Status.UNKNOWN);
        } else {
            throw new ThreemaException("No previous request found to resend");
        }
        message.setToIdentity(groupJoinRequestModel.getAdminIdentity());
        // TODO(ANDR-2607): message was enqueued here in the message queue. Create a task for this.
    }
}

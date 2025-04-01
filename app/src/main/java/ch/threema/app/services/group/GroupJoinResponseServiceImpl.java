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

package ch.threema.app.services.group;


import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.managers.ListenerManager;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseData;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseMessage;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.OutgoingGroupJoinRequestModelFactory;
import ch.threema.storage.models.group.OutgoingGroupJoinRequestModel;
import java8.util.Optional;


@WorkerThread
public class GroupJoinResponseServiceImpl implements GroupJoinResponseService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupJoinResponseServiceImpl");

    private final @NonNull OutgoingGroupJoinRequestModelFactory outgoingGroupJoinRequestModelFactory;

    public GroupJoinResponseServiceImpl(
        @NonNull DatabaseServiceNew databaseService
    ) {
        this.outgoingGroupJoinRequestModelFactory = databaseService.getOutgoingGroupJoinRequestModelFactory();
    }

    /**
     * Processes a GroupJoinResponseMessage, updates de corresponding outgoing request state and triggers UI listeners
     *
     * @param message GroupJoinResponse protobuf message to be processed
     * @return MessageProcessor.ProcessingResult whether the processing was successful, failed or ignored
     */
    @Override
    public @NonNull boolean process(@NonNull GroupJoinResponseMessage message) {

        final @NonNull GroupJoinResponseData responseData = message.getData();
        final @NonNull GroupInviteToken token = responseData.getToken();

        Optional<OutgoingGroupJoinRequestModel> joinRequest = outgoingGroupJoinRequestModelFactory
            .getByInviteToken(token.toString());

        if (joinRequest.isEmpty()) {
            logger.info("Group Join Response: Ignore with unknown request");
            return false;
        }

        OutgoingGroupJoinRequestModel outgoingGroupJoinRequestModel = joinRequest.get();

        final @NonNull String sender = message.getFromIdentity();
        if (!outgoingGroupJoinRequestModel.getAdminIdentity().equals(sender)) {
            logger.info("Group Join Response: Ignore with invalid sender {}", sender);
            return false;
        }

        final GroupJoinResponseData.Response response = responseData.getResponse();
        final OutgoingGroupJoinRequestModel.Status status;

        OutgoingGroupJoinRequestModel.Builder updatedRequestBuilder = new OutgoingGroupJoinRequestModel.Builder(outgoingGroupJoinRequestModel);

        if (response instanceof GroupJoinResponseData.Accept) {
            status = OutgoingGroupJoinRequestModel.Status.ACCEPTED;
            final long groupId = ((GroupJoinResponseData.Accept) response).getGroupId();
            updatedRequestBuilder
                .withGroupApiId(new GroupId(groupId)).build();
        } else if (response instanceof GroupJoinResponseData.Reject) {
            status = OutgoingGroupJoinRequestModel.Status.REJECTED;
        } else if (response instanceof GroupJoinResponseData.GroupFull) {
            status = OutgoingGroupJoinRequestModel.Status.GROUP_FULL;
        } else if (response instanceof GroupJoinResponseData.Expired) {
            status = OutgoingGroupJoinRequestModel.Status.EXPIRED;
        } else {
            throw new IllegalStateException("Invalid response: " + responseData.getResponse());
        }
        updatedRequestBuilder.withResponseStatus(status);

        OutgoingGroupJoinRequestModel updateModel = updatedRequestBuilder.build();
        outgoingGroupJoinRequestModelFactory.update(
            updateModel
        );

        ListenerManager.groupJoinResponseListener.handle(listener -> listener.onReceived(updateModel, status));

        return false;
    }

    /**
     * Sends a Join Response Message.
     *
     * @param identity String ID of the targeted receiver
     * @param token    GroupInviteToken of the corresponding group invite that is being responded to
     * @param response GroupJoinResponseData.Response response to send
     * @throws ThreemaException if message could not be created or sent
     */
    public void send(
        @NonNull String identity,
        @NonNull GroupInviteToken token,
        @NonNull GroupJoinResponseData.Response response
    ) throws ThreemaException {
        final GroupJoinResponseMessage message = new GroupJoinResponseMessage(new GroupJoinResponseData(token, response));
        message.setToIdentity(identity);
        // TODO(ANDR-2607): message was enqueued here in the message queue. Create a task for this.
    }
}

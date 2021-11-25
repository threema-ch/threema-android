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

package ch.threema.app.services.group;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.processors.MessageProcessor;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseData;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseMessage;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.OutgoingGroupJoinRequestModelFactory;
import ch.threema.storage.models.group.OutgoingGroupJoinRequestModel;
import java8.util.Optional;


@WorkerThread
public class GroupJoinResponseServiceImpl implements GroupJoinResponseService {
	private static final Logger logger = LoggerFactory.getLogger(GroupJoinResponseServiceImpl.class);

	private final @NonNull OutgoingGroupJoinRequestModelFactory outgoingGroupJoinRequestModelFactory;
	private final @NonNull MessageQueue messageQueue;

	public GroupJoinResponseServiceImpl(
		@NonNull DatabaseServiceNew databaseService,
		@NonNull MessageQueue messageQueue
	) {
		this.outgoingGroupJoinRequestModelFactory = databaseService.getOutgoingGroupJoinRequestModelFactory();
		this.messageQueue = messageQueue;
	}

	/**
	 * Processes a GroupJoinResponseMessage, updates de corresponding outgoing request state and triggers UI listeners
	 * @param message GroupJoinResponse protobuf message to be processed
	 * @return MessageProcessor.ProcessingResult whether the processing was successful, failed or ignored
	 */
	@Override
	public @NonNull MessageProcessor.ProcessingResult process(@NonNull GroupJoinResponseMessage message) {

		final @NonNull GroupJoinResponseData responseData = message.getData();
		final @NonNull GroupInviteToken token = responseData.getToken();

		Optional<OutgoingGroupJoinRequestModel> joinRequest = outgoingGroupJoinRequestModelFactory
			.getByInviteToken(token.toString());

		if (joinRequest.isEmpty()) {
			logger.info("Group Join Response: Ignore with unknown request");
			return MessageProcessor.ProcessingResult.IGNORED;
		}

		final @NonNull String sender = message.getFromIdentity();
		if (!joinRequest.get().getAdminIdentity().equals(sender)) {
			logger.info("Group Join Response: Ignore with invalid sender {}", sender);
			return MessageProcessor.ProcessingResult.IGNORED;
		}

		final GroupJoinResponseData.Response response = responseData.getResponse();
		final OutgoingGroupJoinRequestModel.Status status;

		if (response instanceof GroupJoinResponseData.Accept) {
			status = OutgoingGroupJoinRequestModel.Status.ACCEPTED;
			final long groupId = ((GroupJoinResponseData.Accept)response).getGroupId();
			outgoingGroupJoinRequestModelFactory.updateGroupApiId(
				joinRequest.get(),
				new GroupId(groupId)
			);
		} else if (response instanceof GroupJoinResponseData.Reject) {
			status = OutgoingGroupJoinRequestModel.Status.REJECTED;
		} else if (response instanceof GroupJoinResponseData.GroupFull) {
			status = OutgoingGroupJoinRequestModel.Status.GROUP_FULL;
		} else if (response instanceof GroupJoinResponseData.Expired) {
			status = OutgoingGroupJoinRequestModel.Status.EXPIRED;
		} else {
			throw new IllegalStateException("Invalid response: " + responseData.getResponse());
		}

		logger.info("Group Join Response: update group request with status {}", status);
		outgoingGroupJoinRequestModelFactory.updateStatus(joinRequest.get(), status);

		ListenerManager.groupJoinResponseListener.handle(listener -> listener.onReceived(joinRequest.get(), status));

		return MessageProcessor.ProcessingResult.SUCCESS;
	}

	/**
	 * Sends a Join Response Message.
	 * @param identity String ID of the targeted receiver
	 * @param token GroupInviteToken of the corresponding group invite that is being responded to
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
		this.messageQueue.enqueue(message);
	}
}

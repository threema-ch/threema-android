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

package ch.threema.app.services.group;

import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.grouplinks.IncomingGroupJoinRequestListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.processors.MessageProcessor;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.UserService;
import ch.threema.base.Result;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestData;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseData;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.factories.IncomingGroupJoinRequestModelFactory;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.GroupInviteModel;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;
import java8.util.Optional;

public class IncomingGroupJoinRequestServiceImpl implements IncomingGroupJoinRequestService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("IncomingGroupJoinRequestServiceImpl");

	private final @NonNull GroupJoinResponseService groupJoinResponseService;
	private final @NonNull GroupService groupService;
	private final @NonNull UserService userService;
	private final @NonNull DatabaseServiceNew databaseServiceNew;

	private static final String GROUP_INVITE_NOT_FOUND_MSG = "Group Join Request: Group invite not found";

	public IncomingGroupJoinRequestServiceImpl(
		@NonNull final GroupJoinResponseService groupJoinResponseService,
		@NonNull final GroupService groupService,
		@NonNull final UserService userService,
		@NonNull final DatabaseServiceNew databaseService
	) {
		this.groupJoinResponseService = groupJoinResponseService;
		this.groupService = groupService;
		this.userService = userService;
		this.databaseServiceNew = databaseService;
	}

	/**
	 * Processes a GroupJoinRequestMessage, persists or updates the corresponding incoming request,
	 * directly responds with an accept response message if the group invite is not administered and triggers UI listeners
	 * @param message GroupJoinRequestMessage to be processed
	 * @return MessageProcessor.ProcessingResult whether the processing was successful, failed or ignored
	 */
	@Override
	public @NonNull
	MessageProcessor.ProcessingResult process(@NonNull final GroupJoinRequestMessage message) {
		final GroupJoinRequestData joinRequest = message.getData();
		final GroupInviteToken token = joinRequest.getToken();
		GroupInviteModelFactory groupInviteModelFactory = databaseServiceNew.getGroupInviteModelFactory();
		final Optional<GroupInviteModel> optionalGroupInvite = groupInviteModelFactory
			.getByToken(token.toString());

		if (optionalGroupInvite.isEmpty()) {
			logger.info("Group Join Request: Ignore with unknown token");
			return MessageProcessor.ProcessingResult.IGNORED;
		}

		final GroupInviteModel groupInvite  = optionalGroupInvite.get();
		if (!groupInvite.getOriginalGroupName().equals(joinRequest.getGroupName())) {
			logger.warn("Group Join Request: Received with invalid group name");
			return MessageProcessor.ProcessingResult.IGNORED;
		}

		final @Nullable Date expirationDate = groupInvite.getExpirationDate();
		if ((expirationDate != null && expirationDate.before(new Date())) || groupInvite.isInvalidated()) {
			logger.info("Group Join Request: Received with expired date or group invite was invalidated/deleted");
			return trySendingResponse(message, new GroupJoinResponseData.Expired()) ?
				MessageProcessor.ProcessingResult.SUCCESS :
				MessageProcessor.ProcessingResult.FAILED;
		}

		final GroupModel group = databaseServiceNew.getGroupModelFactory().getByApiGroupIdAndCreator(groupInvite.getGroupApiId().toString(), userService.getIdentity());
		if (group == null) {
			logger.error("Group Join Request: Corresponding group not found");
			return MessageProcessor.ProcessingResult.FAILED;
		}

		if (Arrays.asList(this.groupService.getGroupIdentities(group)).contains(message.getFromIdentity())) {
			logger.info("Group Join Request: Requesting identity already part of the group, accept and return a group sync message");
			groupService.sendSync(group, new String[]{message.getFromIdentity()});
			return trySendingResponse(message, new GroupJoinResponseData.Accept(groupInvite.getGroupApiId().toLong())) ?
				MessageProcessor.ProcessingResult.SUCCESS :
				MessageProcessor.ProcessingResult.FAILED;
		}

		final @Nullable IncomingGroupJoinRequestModel joinRequestModel;
		IncomingGroupJoinRequestModelFactory incomingGroupJoinRequestModelFactory = databaseServiceNew.getIncomingGroupJoinRequestModelFactory();
		Optional<IncomingGroupJoinRequestModel> previousRequest = incomingGroupJoinRequestModelFactory
			.getRequestByGroupInviteAndIdentity(groupInvite.getId(), message.getFromIdentity());

		// persist new request or reopen previously received request and update message and response state
		if (previousRequest.isEmpty()) {
			try {
				joinRequestModel = this.persistRequest(message, groupInvite.getId());
			} catch(ThreemaException e) {
				logger.error("Group Join Request: failed to insert request to db ", e);
				return MessageProcessor.ProcessingResult.FAILED;
			}
		}
		else {
			joinRequestModel = previousRequest.get();
			incomingGroupJoinRequestModelFactory
				.updateStatus(joinRequestModel,IncomingGroupJoinRequestModel.ResponseStatus.OPEN);

			incomingGroupJoinRequestModelFactory
				.updateRequestMessage(joinRequestModel, message);
		}

		if (this.groupService.isFull(group)) {
			incomingGroupJoinRequestModelFactory
				.updateStatus(joinRequestModel,IncomingGroupJoinRequestModel.ResponseStatus.GROUP_FULL);

			return trySendingResponse(message, new GroupJoinResponseData.GroupFull()) ?
				MessageProcessor.ProcessingResult.SUCCESS :
				MessageProcessor.ProcessingResult.FAILED;
		}

		if (groupInvite.getManualConfirmation()) {
			// fire a on received event that is being observed in the UI
			ListenerManager.incomingGroupJoinRequestListener.handle(
				listener -> listener.onReceived(joinRequestModel, group)
			);
			return MessageProcessor.ProcessingResult.SUCCESS;
		}

		try {
			this.accept(joinRequestModel);
		} catch(Exception e) {
			logger.error("Group Join Request: Group Service Exception", e);
			return MessageProcessor.ProcessingResult.FAILED;
		}

		return MessageProcessor.ProcessingResult.SUCCESS;
	}

	/**
	 * Sends a accept messages and updates the request status of all requests from the requester identity in the database
	 * @param model IncomingGroupJoinRequestModel to be accepted
	 */
	@Override
	public void accept(@NonNull IncomingGroupJoinRequestModel model) throws Exception {
		Optional<GroupInviteModel> invite = this.databaseServiceNew.getGroupInviteModelFactory()
			.getById(model.getGroupInviteId());

		if (invite.isEmpty()) {
			throw new ThreemaException(GROUP_INVITE_NOT_FOUND_MSG);
		}

		GroupModel group = this.databaseServiceNew.getGroupModelFactory()
			.getByApiGroupIdAndCreator(invite.get().getGroupApiId().toString(), userService.getIdentity());
		if (group == null) {
			throw new ThreemaException("Group could not be found");
		}
		this.groupService.addMemberToGroup(group, model.getRequestingIdentity());
		String groupNameFallback = null;
		if (group.getName() == null) {
			groupNameFallback = groupService.getMembersString(group);
		}

		this.groupService.updateGroup(
			group,
			groupNameFallback != null ? groupNameFallback : group.getName(),
			null,
			this.groupService.getGroupIdentities(group),
			null,
			false
		);

		this.databaseServiceNew.getIncomingGroupJoinRequestModelFactory()
			.updateStatus(model, IncomingGroupJoinRequestModel.ResponseStatus.ACCEPTED);

		this.groupJoinResponseService.send(
			model.getRequestingIdentity(),
			invite.get().getToken(),
			new GroupJoinResponseData.Accept(group.getApiGroupId().toLong())
		);

		fireOnRespondEvent();
	}

	/**
	 * Sends a reject messages and updates the request status of all requests from the requester identity in the database
	 * @param model IncomingGroupJoinRequestModel to be rejected
	 */
	@Override
	public void reject(@NonNull IncomingGroupJoinRequestModel model) throws ThreemaException {
		Optional<GroupInviteModel> invite = this.databaseServiceNew.getGroupInviteModelFactory()
			.getById(model.getGroupInviteId());

		if (invite.isEmpty()) {
			throw new ThreemaException(GROUP_INVITE_NOT_FOUND_MSG);
		}

		this.databaseServiceNew.getIncomingGroupJoinRequestModelFactory()
			.updateStatus(model, IncomingGroupJoinRequestModel.ResponseStatus.REJECTED);

		this.groupJoinResponseService.send(
			model.getRequestingIdentity(),
			invite.get().getToken(),
			new GroupJoinResponseData.Reject()
		);

		fireOnRespondEvent();
	}

	/**
	 * Checks db for other group invites and requests from an Identity for a certain group
	 * @param groupId for which group other requests should be filtered
	 * @param identity String identity for which other requests should be filtered
	 * @param responseStatus status that should be updated to
	 * */
	private void markAllOtherRequestsFromIDForGroup(
		GroupId groupId,
		@NonNull String identity,
		@NonNull IncomingGroupJoinRequestModel.ResponseStatus responseStatus
	) {
		IncomingGroupJoinRequestModelFactory incomingGroupJoinRequestModelFactory = databaseServiceNew.getIncomingGroupJoinRequestModelFactory();
		List<IncomingGroupJoinRequestModel> otherOpenRequests = incomingGroupJoinRequestModelFactory
			.getAllOpenRequestsByGroupIdAndIdentity(groupId, identity);

		for (IncomingGroupJoinRequestModel otherOpenRequest : otherOpenRequests) {
			incomingGroupJoinRequestModelFactory.updateStatus(otherOpenRequest, responseStatus);
		}
	}

	/**
	 * Assembles a IncomingGroupJoinRequestModel and inserts it into the database
	 * @param message GroupJoinRequestMessage request message received
	 * @param groupInviteId int id of the group invite through which the request was received
	 * */
	private @NonNull IncomingGroupJoinRequestModel persistRequest(
		@NonNull final GroupJoinRequestMessage message,
		final int groupInviteId
	) throws ThreemaException {
		final IncomingGroupJoinRequestModel provisionalModel = new IncomingGroupJoinRequestModel(
			-1,
			groupInviteId,
			message.getData().getMessage(),
			message.getFromIdentity(),
			message.getDate(),
			IncomingGroupJoinRequestModel.ResponseStatus.OPEN
		);

		final Result<IncomingGroupJoinRequestModel, Exception> insertResult =
			this.databaseServiceNew.getIncomingGroupJoinRequestModelFactory().insert(provisionalModel);

		if (insertResult.isFailure()) {
			logger.error("Group Join Request: Could not insert database record", insertResult.getError());
			throw new ThreemaException("Database insertion failed");
		}

		return Objects.requireNonNull(insertResult.getValue());
	}

	/**
	 * Fires a onRespond event that is being observed in the UI
	 * */
	private void fireOnRespondEvent() {
		ListenerManager.incomingGroupJoinRequestListener.handle(IncomingGroupJoinRequestListener::onRespond);
	}

	/**
	 * Tries to send a response and catches ThreemaException if the response could not be created or sent.
	 * @param message GroupJoinRequestMessage to be answered
	 * @param response GroupJoinResponseData.Response to be sent
	 */
	private boolean trySendingResponse(
		GroupJoinRequestMessage message,
		GroupJoinResponseData.Response response
	) {
		try {
			this.groupJoinResponseService.send(
				message.getFromIdentity(),
				message.getData().getToken(),
				response
			);
		} catch (ThreemaException e) {
			logger.error("Group Join Request: Error sending response {}", response);
			return false;
		}
		return true;
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.base.utils.Utils;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import java8.util.J8Arrays;
import java8.util.Objects;
import java8.util.stream.Stream;

/**
 * {@inheritDoc}
 */
public class GroupMessagingServiceImpl implements GroupMessagingService {
	private static final Logger logger = LoggerFactory.getLogger(GroupMessagingServiceImpl.class);

	private final UserService userService;
	private final ContactService contactService;
	private final MessageQueue messageQueue;

	public GroupMessagingServiceImpl(
		UserService userService,
		ContactService contactService,
		MessageQueue messageQueue
	) {
		this.userService = userService;
		this.contactService = contactService;
		this.messageQueue = messageQueue;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int sendMessage(
		@NonNull GroupModel group,
		@NonNull String[] identities,
		@NonNull CreateApiMessage createApiMessage
	) throws ThreemaException  {
		return this.sendMessage(
			group,
			identities,
			createApiMessage,
			null
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int sendMessage(
		@NonNull GroupModel group,
		@NonNull String[] identities,
		@NonNull CreateApiMessage createApiMessage,
		@Nullable GroupMessageQueued queued
	) throws ThreemaException  {
		return this.sendMessage(
			group.getApiGroupId(),
			group.getCreatorIdentity(),
			identities,
			createApiMessage,
			queued
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int sendMessage(
		@NonNull final GroupId groupId,
		@NonNull final String groupCreatorId,
		@NonNull String[] identities,
		@NonNull CreateApiMessage createApiMessage,
		@Nullable GroupMessageQueued queued
	) throws ThreemaException{
		// Generate a new, random message ID that will be used for all group messages
		final MessageId messageId = new MessageId();

		// Remove duplicate identities, null and our own identity
		final String ownIdentity = this.userService.getIdentity();
		final Stream<String> uniqueIdentities = J8Arrays.stream(identities)
			.filter(Objects::nonNull)
			.filter(id -> !id.equals(ownIdentity))
			.distinct();

		// Create an AbstractGroupMessage for every recipient identity
		final List<AbstractGroupMessage> pendingGroupMessages = new ArrayList<>();
		uniqueIdentities.sequential().forEach((@NonNull String identity) -> {
			// Fetch contact
			ContactModel contactModel = this.contactService.getByIdentity(identity);

			// Ensure that contact already exists for this identity
			if (contactModel == null) {
				try {
					contactModel = this.contactService.createContactByIdentity(identity, true, true);
				} catch (EntryAlreadyExistsException e) {
					// Identity exists, this should not happen
					logger.error("Got EntryAlreadyExistsException when creating contact, even though getByIdentity returned null");
					return;
				} catch (InvalidEntryException | PolicyViolationException e) {
					// Do not send message to this identity
					logger.error("Could not create contact for identity " + identity, e);
					return;
				}
			}

			// If contact is valid, enqueue group message
			if (contactModel.getState() != ContactModel.State.INVALID) {
				final AbstractGroupMessage groupMessage = createApiMessage.create(messageId);
				groupMessage.setGroupId(groupId);
				groupMessage.setGroupCreator(groupCreatorId);
				groupMessage.setFromIdentity(ownIdentity);
				groupMessage.setToIdentity(identity);
				pendingGroupMessages.add(groupMessage);
			}
		});

		//fire queued first!
		if(queued != null) {
			for (AbstractGroupMessage groupMessage : pendingGroupMessages) {
				queued.onQueued(groupMessage);
			}
		}

		// Enqueue every message
		int enqueuedMessagesCount = 0;
		for (AbstractGroupMessage groupMessage : pendingGroupMessages) {
			logger.debug("Sending group message {}", groupMessage);
			final MessageBox messageBox = this.messageQueue.enqueue(groupMessage);
			if (messageBox == null) {
				logger.error("Failed to enqueue group message to {}", groupMessage.getToIdentity());
			} else {
				enqueuedMessagesCount++;
				if (logger.isDebugEnabled()) {
					logger.debug(
						"Outgoing group message ID {} from {} to {}",
						messageBox.getMessageId(),
						messageBox.getFromIdentity(),
						messageBox.getToIdentity()
					);
					logger.debug("  Nonce: {}", Utils.byteArrayToHexString(messageBox.getNonce()));
					logger.debug("  Data: {}", Utils.byteArrayToHexString(messageBox.getBox()));
				}
			}
		}

		return enqueuedMessagesCount;
	}
}


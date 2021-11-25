/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.app.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.services.MessageService;
import ch.threema.domain.protocol.csp.connection.MessageAckListener;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.QueueMessageId;
import ch.threema.storage.models.MessageState;

/**
 * Process incoming server acks. These acks are sent when the server has stored
 * the outgoing message in the message queue.
 */
public class MessageAckProcessor implements MessageAckListener {
	private static final Logger logger = LoggerFactory.getLogger(MessageAckProcessor.class);

	// Services
	private MessageService messageService;

	// Bounded list of recently acked message IDs
	private final List<MessageId> recentlyAckedMessageIds = new LinkedList<>();

	private static final int ACK_LIST_MAX_ENTRIES = 20;

	@Override
	public void processAck(@NonNull QueueMessageId queueMessageId) {
		logger.info(
			"Processing server ack for message ID {} from {}",
			queueMessageId.getMessageId(),
			queueMessageId.getRecipientId()
		);

		synchronized (recentlyAckedMessageIds) {
			while (recentlyAckedMessageIds.size() >= ACK_LIST_MAX_ENTRIES) {
				recentlyAckedMessageIds.remove(0);
			}
			recentlyAckedMessageIds.add(queueMessageId.getMessageId());
		}

		if (this.messageService != null) {
			this.messageService.updateMessageStateAtOutboxed(
				queueMessageId.getMessageId(),
				MessageState.SENT,
				null
			);
		}
	}

	/**
	 * Set the message service instance.
	 *
	 * This is required because there is a circular dependency between the
	 * {@link MessageService} and the {@link MessageAckProcessor}.
	 */
	public void setMessageService(MessageService messageService) {
		this.messageService = messageService;
	}

	/**
	 * Return true if the specified messageId was recently acked.
	 *
	 * Note: Only the last {@link #ACK_LIST_MAX_ENTRIES} message IDs are considered!
	 */
	public boolean wasRecentlyAcked(MessageId messageId) {
		synchronized (recentlyAckedMessageIds) {
			return recentlyAckedMessageIds.contains(messageId);
		}
	}
}

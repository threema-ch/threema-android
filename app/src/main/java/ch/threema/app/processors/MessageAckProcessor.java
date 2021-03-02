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
import ch.threema.client.MessageAck;
import ch.threema.client.MessageAckListener;
import ch.threema.client.MessageId;
import ch.threema.storage.models.MessageState;

public class MessageAckProcessor implements MessageAckListener {
	private MessageService messageService;
	private final List<MessageId> ackedMessageIds = new LinkedList<>();

	private static final Logger logger = LoggerFactory.getLogger(MessageAckProcessor.class);
	private static final int ACK_LIST_MAX_ENTRIES = 20;

	@Override
	public void processAck(@NonNull MessageAck ack) {
		logger.info("Processing server ack for message ID {} from {}", ack.getMessageId(), ack.getRecipientId());

		synchronized (ackedMessageIds) {
			while (ackedMessageIds.size() >= ACK_LIST_MAX_ENTRIES) {
				ackedMessageIds.remove(0);
			}
			ackedMessageIds.add(ack.getMessageId());
		}

		if (this.messageService != null) {
			this.messageService.updateMessageStateAtOutboxed(ack.getMessageId(), MessageState.SENT, null);
		}
	}

	public void setMessageService(MessageService messageService) {
		this.messageService = messageService;
	}

	public boolean isMessageIdAcked(MessageId messageId) {
		synchronized (ackedMessageIds) {
			return ackedMessageIds.contains(messageId);
		}
	}
}

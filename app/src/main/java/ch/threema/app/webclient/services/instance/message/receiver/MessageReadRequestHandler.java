/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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

package ch.threema.app.webclient.services.instance.message.receiver;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import ch.threema.app.routines.ReadMessagesRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;

/**
 * Mark all message (id <= requested message id) as read
 */
@WorkerThread
public class MessageReadRequestHandler extends MessageReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageReadRequestHandler");

	private final ContactService contactService;
	private final GroupService groupService;
	private final MessageService messageService;
	private final NotificationService notificationService;

	@AnyThread
	public MessageReadRequestHandler(ContactService contactService,
	                                 GroupService groupService,
	                                 MessageService messageService,
	                                 NotificationService notificationService) {
		super(Protocol.SUB_TYPE_READ);
		this.contactService = contactService;
		this.groupService = groupService;
		this.messageService = messageService;
		this.notificationService = notificationService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received message read");
		final Map<String, Value> args = this.getArguments(message, false);

		// Is typing or stopped typing
		final String messageIdStr = args.get(Protocol.ARGUMENT_MESSAGE_ID).asStringValue().asString();
		final int messageId = Integer.parseInt(messageIdStr);
		final String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().asString();
		final String receiverId = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();

		ch.threema.app.messagereceiver.MessageReceiver receiver = null;
		switch (type) {
			case Receiver.Type.CONTACT:
				ContactModel contactModel = contactService.getByIdentity(receiverId);
				if (contactModel != null) {
					receiver = contactService.createReceiver(contactModel);
				}
				break;
			case Receiver.Type.GROUP:
				GroupModel groupModel = groupService.getById(Integer.valueOf(receiverId));
				if (groupModel != null) {
					receiver = groupService.createReceiver(groupModel);
				}
				break;
		}

		if(receiver != null) {
			try {
				final MessageService.MessageFilter filter = new MessageService.MessageFilter() {
					@Override
					public long getPageSize() {
						return 0;
					}

					@Override
					public Integer getPageReferenceId() {
						return null;
					}

					@Override
					public boolean withStatusMessages() {
						return false;
					}

					@Override
					public boolean withUnsaved() {
						return false;
					}

					@Override
					public boolean onlyUnread() {
						return true;
					}

					@Override
					public boolean onlyDownloaded() {
						return false;
					}

					@Override
					public MessageType[] types() {
						return new MessageType[0];
					}

					@Override
					public int[] contentTypes() {
						return null;
					}
				};
				(new ReadMessagesRoutine(receiver.loadMessages(filter), messageService, notificationService)).run();
			} catch (SQLException e) {
				logger.error("Exception", e);
				//do nothing more
			}
		}
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return true;
	}
}


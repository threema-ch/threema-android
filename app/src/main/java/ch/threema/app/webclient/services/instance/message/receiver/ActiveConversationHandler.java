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

package ch.threema.app.webclient.services.instance.message.receiver;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import ch.threema.app.routines.ReadMessagesRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.ConversationTagServiceImpl;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;

@WorkerThread
public class ActiveConversationHandler extends MessageReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ActiveConversationHandler");
	private final ConversationService conversationService;
	private final ConversationTagService conversationTagService;
	private final ContactService contactService;
	private final GroupService groupService;
	private final MessageService messageService;
	private final NotificationService notificationService;

	@AnyThread
	public ActiveConversationHandler(ContactService contactService,
	                                 GroupService groupService,
	                                 ConversationService conversationService,
	                                 ConversationTagService conversationTagService,
		                             MessageService messageService,
	                                 NotificationService notificationService) {
		super(Protocol.SUB_TYPE_ACTIVE_CONVERSATION);
		this.contactService = contactService;
		this.groupService = groupService;
		this.conversationService = conversationService;
		this.conversationTagService = conversationTagService;
		this.messageService = messageService;
		this.notificationService = notificationService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received active conversation update");

		// Get args
		final Map<String, Value> args = this.getArguments(message, false, new String[]{
			Protocol.ARGUMENT_RECEIVER_TYPE,
			Protocol.ARGUMENT_RECEIVER_ID,
		});
		final String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().asString();
		final String receiverId = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();

		// get MessageReceiver from message
		ch.threema.app.messagereceiver.MessageReceiver messageReceiver = null;
		switch (type) {
			case Receiver.Type.CONTACT:
				ContactModel contactModel = contactService.getByIdentity(receiverId);
				if (contactModel != null) {
					messageReceiver = contactService.createReceiver(contactModel);
				}
				break;
			case Receiver.Type.GROUP:
				GroupModel groupModel = groupService.getById(Integer.valueOf(receiverId));
				if (groupModel != null) {
					messageReceiver = groupService.createReceiver(groupModel);
				}
				break;
		}

		// get ConversationModel from MessageReceiver
		if (messageReceiver != null) {
			final ConversationModel conversationModel = this.conversationService.refresh(messageReceiver);
			if (conversationModel != null) {
				conversationTagService.removeTagAndNotify(conversationModel, conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_UNREAD));
			}

			// TODO(ANDR-3141): Remove workaround
			// workaround: mark all unread messages as read if last message is a group call status message as the web client does not support this
			List<AbstractMessageModel> unreadGroupCallStatusMessages = messageReceiver.loadMessages(new MessageService.MessageFilter() {
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
					return new MessageType[]{MessageType.GROUP_CALL_STATUS};
				}

				@Override
				public int[] contentTypes() {
					return new int[0];
				}

				@Override
				public int[] displayTags() {
					return new int[0];
				}
			});

			if (!unreadGroupCallStatusMessages.isEmpty()) {
				new ReadMessagesRoutine(unreadGroupCallStatusMessages, messageService, notificationService).run();
			}
		}
	}

	@Override
	protected boolean maybeNeedsConnection() {
		// We don't need to send or receive Threema messages in reaction to this webclient message
		return false;
	}
}

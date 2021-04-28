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

package ch.threema.app.webclient.services.instance.message.receiver;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.ConversationTagServiceImpl;
import ch.threema.app.services.GroupService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.GroupModel;

@WorkerThread
public class ActiveConversationHandler extends MessageReceiver {
	private static final Logger logger = LoggerFactory.getLogger(ActiveConversationHandler.class);
	private final ConversationService conversationService;
	private final ConversationTagService conversationTagService;
	private final ContactService contactService;
	private final GroupService groupService;

	@AnyThread
	public ActiveConversationHandler(ContactService contactService,
	                                 GroupService groupService,
	                                 ConversationService conversationService,
	                                 ConversationTagService conversationTagService) {
		super(Protocol.SUB_TYPE_ACTIVE_CONVERSATION);
		this.contactService = contactService;
		this.groupService = groupService;
		this.conversationService = conversationService;
		this.conversationTagService = conversationTagService;
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
				conversationTagService.unTag(conversationModel, conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_UNREAD));
			}
		}
	}

	@Override
	protected boolean maybeNeedsConnection() {
		// We don't need to send or receive Threema messages in reaction to this webclient message
		return false;
	}
}

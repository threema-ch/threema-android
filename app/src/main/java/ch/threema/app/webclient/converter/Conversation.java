/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.app.webclient.converter;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ConversationTagServiceImpl;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.TagModel;

@AnyThread
public class Conversation extends Converter {
	public final static String POSITION = "position";
	public final static String MESSAGE_COUNT = "messageCount";
	public final static String UNREAD_COUNT = "unreadCount";
	public final static String LATEST_MESSAGE = "latestMessage";
	public final static String NOTIFICATIONS = "notifications";
	public final static String IS_STARRED = "isStarred";
	public final static String IS_UNREAD = "isUnread";

	public interface Append {
		void append(MsgpackObjectBuilder builder, ConversationModel conversation, Utils.ModelWrapper modelWrapper);
	}

	/**
	 * Converts multiple conversations to MsgpackObjectBuilder instances.
	 */
	public static List<MsgpackBuilder> convert(List<ConversationModel> conversations, Append append) throws ConversionException {
		List<MsgpackBuilder> list = new ArrayList<>();
		for (ConversationModel conversation : conversations) {
			list.add(convert(conversation, append));
		}
		return list;
	}

	/**
	 * Converts a conversation to a MsgpackObjectBuilder.
	 */
	public static MsgpackBuilder convert(ConversationModel conversation) throws ConversionException {
		return convert(conversation, null);
	}
	/**
	 * Converts a conversation to a MsgpackObjectBuilder.
	 */
	public static MsgpackBuilder convert(ConversationModel conversation, Append append) throws ConversionException {
		MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		final ServiceManager serviceManager = getServiceManager();
		if (serviceManager == null) {
			throw new ConversionException("Service manager is null");
		}
		try {
			final Utils.ModelWrapper model = Utils.ModelWrapper.getModel(conversation);
			builder.put(Receiver.TYPE, model.getType());
			builder.put(Receiver.ID, model.getId());
			builder.put(POSITION, conversation.getPosition());
			builder.put(MESSAGE_COUNT, conversation.getMessageCount());
			builder.put(UNREAD_COUNT, conversation.getUnreadCount());
			maybePutLatestMessage(builder, LATEST_MESSAGE, conversation);

			builder.put(NOTIFICATIONS, NotificationSettings.convert(conversation));

			final TagModel starTagModel = serviceManager.getConversationTagService()
				.getTagModel(ConversationTagServiceImpl.FIXED_TAG_PIN);
			final boolean isStarred = serviceManager.getConversationTagService()
				.isTaggedWith(conversation, starTagModel);
			if (isStarred) {
				builder.put(IS_STARRED, isStarred);
			}

			final TagModel unreadTagModel = serviceManager.getConversationTagService()
				.getTagModel(ConversationTagServiceImpl.FIXED_TAG_UNREAD);
			final boolean isUnread = serviceManager.getConversationTagService()
				.isTaggedWith(conversation, unreadTagModel);
			builder.put(IS_UNREAD, isUnread);

			if(append != null) {
				append.append(builder, conversation, model);
			}
		} catch (NullPointerException e) {
			throw new ConversionException(e.toString());
		}
		return builder;
	}

	private static void maybePutLatestMessage(
		MsgpackObjectBuilder builder,
		String field,
		ConversationModel conversation
	) throws ConversionException {
		AbstractMessageModel message = conversation.getLatestMessage();
		if (message != null) {
			builder.put(field, Message.convert(message, conversation.getReceiverType(), false, Message.DETAILS_NO_QUOTE));
		}
	}
}

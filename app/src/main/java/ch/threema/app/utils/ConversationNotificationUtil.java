/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;

import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.ShortcutService;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;

public class ConversationNotificationUtil {
	private static final Logger logger = LoggerFactory.getLogger(ConversationNotificationUtil.class);

	protected static final HashMap<String, NotificationService.ConversationNotificationGroup> notificationGroupHashMap = new HashMap<>();

	public static NotificationService.ConversationNotification convert(Context context,
	                                                                   AbstractMessageModel messageModel,
	                                                                   ContactService contactService,
	                                                                   GroupService groupService,
	                                                                   DeadlineListService hiddenChatsListService) {
		NotificationService.ConversationNotification conversationNotification = null;
		if(messageModel instanceof MessageModel) {
			conversationNotification = create(context, (MessageModel) messageModel, contactService, hiddenChatsListService);
		}
		else if(messageModel instanceof GroupMessageModel) {
			conversationNotification = create(context, (GroupMessageModel) messageModel, groupService, hiddenChatsListService);
		}

		return conversationNotification;
		/*if(conversationNotification != null) {
			conversationNotification.
		}*/
	}

	private static MessageService.MessageString getMessage(AbstractMessageModel messageModel) {
		//load lazy
		try {
			return ThreemaApplication.getServiceManager().getMessageService()
						.getMessageString(messageModel, -1, !ConfigUtils.canDoGroupedNotifications());
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			return new MessageService.MessageString(null);
		}
	}

	private static Person getSender(AbstractMessageModel messageModel) {
		//load lazy
		try {
			final ContactService contactService = ThreemaApplication.getServiceManager().getContactService();
			final ContactModel contactModel = contactService.getByIdentity(messageModel.getIdentity());

			Person.Builder builder = new Person.Builder().setKey(contactService.getUniqueIdString(contactModel)).setName(NameUtil.getShortName(ThreemaApplication.getAppContext(), messageModel, contactService));
			Bitmap avatar = contactService.getAvatar(contactModel, false);
			if (avatar != null) {
				IconCompat iconCompat = IconCompat.createWithBitmap(avatar);
				builder.setIcon(iconCompat);
			}
			return builder.build();
		} catch (ThreemaException e) {
			logger.error("ThreemaException", e);
			return null;
		}
	}

	private static MessageType getMessageType(AbstractMessageModel messageModel) {
		return messageModel.getType();
	}

	private static Date getWhen(AbstractMessageModel messageModel) {
		return messageModel.getCreatedAt();
	}

	private static NotificationService.ConversationNotification create(final Context context, final MessageModel messageModel,
	                                                                   final ContactService contactService, final DeadlineListService hiddenChatsListService) {
		final ContactModel contactModel = contactService.getByIdentity(messageModel.getIdentity());
		String groupUid = "i" + messageModel.getIdentity();
		synchronized (notificationGroupHashMap) {
			NotificationService.ConversationNotificationGroup group = notificationGroupHashMap.get(groupUid);
			String longName = hiddenChatsListService.has(contactService.getUniqueIdString(contactModel)) ? context.getString(R.string.private_chat_subject) :
					NameUtil.getDisplayNameOrNickname(contactModel, true);
			String shortName = hiddenChatsListService.has(contactService.getUniqueIdString(contactModel)) ? context.getString(R.string.private_chat_subject) :
					NameUtil.getShortName(contactModel);
			String contactLookupUri = contactService.getAndroidContactLookupUriString(contactModel);

			if(group == null) {
				group = new NotificationService.ConversationNotificationGroup(
						groupUid,
						longName,
						shortName,
						contactService.createReceiver(contactModel),
						new NotificationService.FetchBitmap() {
							@Override
							public Bitmap fetch() {
								//lacy stuff
								if (contactService != null) {
									return contactService.getAvatar(
											hiddenChatsListService.has(contactService.getUniqueIdString(contactModel)) ? null :
											contactModel,
											false
									);
								}
								return null;
							}
						},
						contactLookupUri);
				notificationGroupHashMap.put(groupUid, group);
			} else {
				// contact name may change between notifications - set it again
				group.setName(longName);
				group.setShortName(shortName);
			}

			return new NotificationService.ConversationNotification(
					getMessage(messageModel),
					getWhen(messageModel),
					getId(messageModel),
					getUid(messageModel),
					group,
					getFetchThumbnail(messageModel),
					getShortcut(messageModel),
					getSender(messageModel),
					getMessageType(messageModel)
			);

		}
	}

	private static NotificationService.ConversationNotification create(final Context context, final GroupMessageModel messageModel, final GroupService groupService, final DeadlineListService hiddenChatsListService) {
		final GroupModel groupModel = groupService.getById(messageModel.getGroupId());

		String groupUid = "g" + messageModel.getGroupId();
		synchronized (notificationGroupHashMap) {
			NotificationService.ConversationNotificationGroup group = notificationGroupHashMap.get(groupUid);
			String name = hiddenChatsListService.has(groupService.getUniqueIdString(groupModel)) ? context.getString(R.string.private_chat_subject) :
				NameUtil.getDisplayName(groupService.getById(messageModel.getGroupId()), groupService);

			if (group == null) {
				group = new NotificationService.ConversationNotificationGroup(
					groupUid,
					name,
					name,
					groupService.createReceiver(groupModel),
					new NotificationService.FetchBitmap() {
						@Override
						public Bitmap fetch() {
							if (groupService != null) {
								return groupService.getAvatar(
										hiddenChatsListService.has(groupService.getUniqueIdString(groupModel)) ? null :
										groupModel,
										false
								);
							}
							return null;
						}
					},
					null
				);
				notificationGroupHashMap.put(groupUid, group);
			} else {
				// group name may change between notifications - set it again
				group.setName(name);
				group.setShortName(name);
			}

			return new NotificationService.ConversationNotification(
					getMessage(messageModel),
					getWhen(messageModel),
					getId(messageModel),
					getUid(messageModel),
					group,
					getFetchThumbnail(messageModel),
					getShortcut(messageModel),
					getSender(messageModel),
					getMessageType(messageModel)
			);
		}
	}

	public static String getUid(AbstractMessageModel messageModel) {
		return messageModel.getUid();
	}

	public static int getId(AbstractMessageModel messageModel) {
		return messageModel.getId();
	}

	public static NotificationService.FetchBitmap getFetchThumbnail(final AbstractMessageModel messageModel) {
		if (messageModel.getType() == MessageType.IMAGE ||
				messageModel.getType() == MessageType.VIDEO) {
			return new NotificationService.FetchBitmap() {
				@Override
				public Bitmap fetch() {
					//get file service directly...
					//... its the evil way!
					FileService f = null;
					try {
						f = ThreemaApplication.getServiceManager().getFileService();
					} catch (FileSystemNotPresentException e) {
						logger.error("FileSystemNotPresentException", e);
					}

					if (f != null) {
						try {
							return f.getMessageThumbnailBitmap(messageModel, null);
						} catch (Exception e) {
							logger.debug("cannot fetch thumbnail, abort");
						}
					}

					return null;
				}
			};
		}

		return null;
	}

	public static ShortcutInfo getShortcut(final AbstractMessageModel messageModel){
		ShortcutService shortcutService = null;
		ContactService contactService = null;
		GroupService groupService = null;
		try {
			shortcutService = ThreemaApplication.getServiceManager().getShortcutService();
			contactService = ThreemaApplication.getServiceManager().getContactService();
			groupService = ThreemaApplication.getServiceManager().getGroupService();
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			return null;
		}

		if (TestUtil.required(shortcutService, contactService, groupService) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
			try {
				if(messageModel instanceof MessageModel){
					return shortcutService.getShortcutInfo(contactService.getByIdentity(messageModel.getIdentity()), ShortcutService.TYPE_NONE);
				}
				if(messageModel instanceof GroupMessageModel){
					return shortcutService.getShortcutInfo(groupService.getById(((GroupMessageModel) messageModel).getGroupId()));
				}
			} catch (Exception e) {
				logger.error("cannot fetch shortcut, abort");
				return null;
			}
		}
		return null;
	}

}

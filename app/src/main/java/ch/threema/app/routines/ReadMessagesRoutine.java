/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.app.routines;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.utils.ConversationNotificationUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class ReadMessagesRoutine implements Runnable {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ReadMessagesRoutine");

	private final List<AbstractMessageModel> messages;
	private final MessageService messageService;
	private final NotificationService notificationService;

	private final List<OnFinished> onFinished = new ArrayList<>();

	public interface OnFinished {
		void finished(boolean success);
	}

	public ReadMessagesRoutine(List<AbstractMessageModel> messages, MessageService messageService, NotificationService notificationService) {
		this.messages = messages;
		this.messageService = messageService;
		this.notificationService = notificationService;
	}

	@Override
	public void run() {
		logger.debug("ReadMessagesRoutine.run()");
		boolean success = true;

		if (this.messages != null && this.messages.size() > 0) {
		/* It is possible that the list gets modified while we're iterating. In that case,
		   we repeat the operation on exception (to avoid the overhead of copying the whole
		   list before we start).
		 */
			for (int tries = 0; tries < 10; tries++) {
				try {
					final List<AbstractMessageModel> modifiedMessageModels = new ArrayList<>();

					for (AbstractMessageModel messageModel : this.messages) {
						if (MessageUtil.canMarkAsRead(messageModel)) {
							try {
								if (this.messageService.markAsRead(messageModel, true)) {
									modifiedMessageModels.add(messageModel);
								}
							} catch (ThreemaException e) {
								logger.error("Exception", e);
								success = false;
							}
						}
					}

					if (modifiedMessageModels.size() > 0) {
						// Get notification UIDs of the messages that have just been marked as read
						final String[] notificationUids = new String[modifiedMessageModels.size()];
						int pos = 0;
						for (AbstractMessageModel m : modifiedMessageModels) {
							notificationUids[pos++] = ConversationNotificationUtil.getUid(m);
						}

						// Notify listeners
						ListenerManager.messageListeners.handle(listener -> listener.onModified(modifiedMessageModels));

						// Cancel notifications
						this.notificationService.cancelConversationNotification(notificationUids);
					}
					break;
				} catch (ConcurrentModificationException e) {
					logger.error("Exception", e);
				}
			}
		}

		for (OnFinished f : this.onFinished) {
			f.finished(success);
		}

	}

	public void addOnFinished(OnFinished onFinished) {
		this.onFinished.add(onFinished);
	}

	public void removeOnFinished(OnFinished onFinished) {
		this.onFinished.remove(onFinished);
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

package ch.threema.storage.models;

import java.util.Date;

import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver.MessageReceiverType;
import ch.threema.app.utils.ConversationUtil;

public class ConversationModel {

	private long messageCount;
	private AbstractMessageModel latestMessage;
	private final MessageReceiver receiver;
	private long unreadCount;
	private String uid = null;
	private int position = -1;
	private boolean isTyping = false;

	public ConversationModel(MessageReceiver receiver) {
		this.receiver = receiver;
	}

	public long getMessageCount() {
		return messageCount;
	}

	public void setMessageCount(long messageCount) {
		this.messageCount = messageCount;
	}

	public Date getSortDate() {
		if(this.getLatestMessage() != null) {
			return this.getLatestMessage().getCreatedAt();
		}

		if(this.isGroupConversation()) {
			if (getMessageCount() > 0) {
				return this.getGroup().getCreatedAt();
			}
			return new Date(0);
		}

		if(this.isDistributionListConversation()) {
			if (getMessageCount() > 0) {
				return this.getDistributionList().getCreatedAt();
			}
			return new Date(0);
		}

		return null;
	}

	public AbstractMessageModel getLatestMessage() {
		return latestMessage;
	}

	public long getUnreadCount() {
		return this.unreadCount;
	}

	public void setUnreadCount(long unreadCount) {
		this.unreadCount = unreadCount;
	}
	public void setLatestMessage(AbstractMessageModel latestMessage) {
		this.latestMessage = latestMessage;
	}

	public boolean hasUnreadMessage() {
		return this.unreadCount > 0;
	}

	public GroupModel getGroup() {
		if(this.isGroupConversation()) {
			return ((GroupMessageReceiver)this.receiver).getGroup();
		}

		return null;
	}

	public ContactModel getContact() {
		if(this.isContactConversation()) {
			return ((ContactMessageReceiver)this.receiver).getContact();
		}

		return null;
	}

	public DistributionListModel getDistributionList() {
		if(this.isDistributionListConversation()) {
			return ((DistributionListMessageReceiver)this.receiver).getDistributionList();
		}

		return null;
	}

	public boolean isContactConversation() {
		return this.receiver.getType() == MessageReceiver.Type_CONTACT;
	}

	public boolean isGroupConversation() {
		return this.receiver.getType() == MessageReceiver.Type_GROUP;
	}

	public boolean isDistributionListConversation() {
		return this.receiver.getType() == MessageReceiver.Type_DISTRIBUTION_LIST;
	}

	public @MessageReceiverType int getReceiverType() {
		return this.receiver.getType();
	}

	public MessageReceiver getReceiver() {
		return this.receiver;
	}

	public String getUid() {
		if(this.uid == null) {
			if (this.isContactConversation()) {
				this.uid = ConversationUtil.getIdentityConversationUid(this.getContact() != null ? this.getContact().getIdentity() : null);
			} else if (this.isGroupConversation()) {
				this.uid = ConversationUtil.getGroupConversationUid(this.getGroup() != null ? this.getGroup().getId() : null);
			} else if (this.isDistributionListConversation()) {
				this.uid = ConversationUtil.getDistributionListConversationUid(this.getDistributionList() != null ? this.getDistributionList().getId() : null);
			}
		}
		return this.uid;
	}

	public int getPosition() {
		return this.position;
	}

	public ConversationModel setPosition(int position) {
		this.position = position;
		return this;
	}

	public boolean isTyping() {
		return this.isTyping;
	}

	public ConversationModel setIsTyping(boolean is) {
		this.isTyping = is;
		return this;
	}

	@Override
	public String toString() {
		return getReceiver().getDisplayName();
	}
}

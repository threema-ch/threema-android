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

package ch.threema.storage.models;

import android.content.Context;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver.MessageReceiverType;
import ch.threema.app.utils.ConversationUtil;

public class ConversationModel {

	public static final int NO_RESOURCE = -1;

	private final @NonNull Context context;

	private MessageReceiver<?> receiver;

	private long messageCount;

	private @Nullable AbstractMessageModel latestMessage;

	private long unreadCount;
	private boolean isUnreadTagged;

	private String uid = null;
	private int position = -1;

	private @Nullable Date lastUpdate;

	private boolean isTyping = false;
	private boolean isPinTagged = false;

	public ConversationModel(@NonNull Context context, MessageReceiver<?> receiver) {
		this.context = context;
		this.receiver = receiver;
	}

	public @NonNull Context getContext() {
		return context;
	}

	public void setMessageCount(long messageCount) {
		this.messageCount = messageCount;
	}

	public long getMessageCount() {
		return messageCount;
	}

	public void setLastUpdate(@Nullable Date lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	public @Nullable Date getLastUpdate() {
		return lastUpdate;
	}

	/**
	 * Return the date used for sorting.
	 * Corresponds to {@link #getLastUpdate()} if set.
	 */
	public @NonNull Date getSortDate() {
		if (this.lastUpdate != null) {
			return this.lastUpdate;
		}
		return new Date(0);
	}

	public void setLatestMessage(@Nullable AbstractMessageModel latestMessage) {
		this.latestMessage = latestMessage;
	}

	@Nullable
	public AbstractMessageModel getLatestMessage() {
		return latestMessage;
	}

	public boolean hasUnreadMessage() {
		return this.unreadCount > 0;
	}

	@Nullable
	public ContactModel getContact() {
		if (this.isContactConversation()) {
			return ((ContactMessageReceiver) this.receiver).getContact();
		}

		return null;
	}

	@Nullable
	public GroupModel getGroup() {
		if (this.isGroupConversation()) {
			return ((GroupMessageReceiver) this.receiver).getGroup();
		}

		return null;
	}

	@Nullable
	public DistributionListModel getDistributionList() {
		if (this.isDistributionListConversation()) {
			return ((DistributionListMessageReceiver) this.receiver).getDistributionList();
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

	public @MessageReceiverType
	int getReceiverType() {
		return this.receiver.getType();
	}

	public MessageReceiver<?> getReceiver() {
		return this.receiver;
	}
	public void setReceiver(MessageReceiver<?> receiver) {
		this.receiver = receiver;
	}

	@NonNull
	public String getUid() {
		if (this.uid == null) {
			if (this.isContactConversation()) {
				this.uid = ConversationUtil.getIdentityConversationUid(this.getContact() != null ? this.getContact().getIdentity() : null);
			} else if (this.isGroupConversation()) {
				this.uid = ConversationUtil.getGroupConversationUid(this.getGroup() != null ? this.getGroup().getId() : -1);
			} else if (this.isDistributionListConversation()) {
				this.uid = ConversationUtil.getDistributionListConversationUid(this.getDistributionList() != null ? this.getDistributionList().getId() : -1);
			}
		}
		return this.uid;
	}

	public ConversationModel setPosition(int position) {
		this.position = position;
		return this;
	}

	public int getPosition() {
		return this.position;
	}

	public boolean isTyping() {
		return this.isTyping;
	}

	public ConversationModel setIsTyping(boolean is) {
		this.isTyping = is;
		return this;
	}

	public void setIsPinTagged(boolean isPinTagged) {
		this.isPinTagged = isPinTagged;
	}

	public boolean isPinTagged() {
		return isPinTagged;
	}

	public void setIsUnreadTagged(boolean isUnreadTagged) {
		this.isUnreadTagged = isUnreadTagged;
	}

	public boolean getIsUnreadTagged() {
		return isUnreadTagged;
	}

	public void setUnreadCount(long unreadCount) {
		this.unreadCount = unreadCount;
		if (this.unreadCount == 0) {
			isUnreadTagged = false;
		}
	}

	public long getUnreadCount() {
		return this.unreadCount;
	}

	@Override
	public @NonNull String toString() {
		return getReceiver().getDisplayName();
	}
}

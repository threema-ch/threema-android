/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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
import ch.threema.app.utils.ConversationUtil;


public class ConversationTagModel {
	public static final String TABLE = "conversation_tag";
	public static final String COLUMN_CONVERSATION_UID = "conversationUid";
	public static final String COLUMN_TAG = "tag";
	public static final String COLUMN_CREATED_AT = "createdAt";

	private String conversationUid;
	private String tag;
	private Date createdAt;

	public ConversationTagModel(String conversationUid, String tag) {
		this.conversationUid = conversationUid;
		this.tag = tag;
		this.createdAt = new Date();
	}

	public ConversationTagModel() {
	}


	public ConversationTagModel setConversationUid(String conversationUid) {
		this.conversationUid = conversationUid;
		return this;
	}

	public String getConversationUid() {
		return this.conversationUid;
	}

	public ConversationTagModel setTag(String tag) {
		this.tag = tag;
		return this;
	}

	public String getTag() {
		return this.tag;
	}

	public ConversationTagModel setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	public Date getCreatedAt() {
		return this.createdAt;
	}
}

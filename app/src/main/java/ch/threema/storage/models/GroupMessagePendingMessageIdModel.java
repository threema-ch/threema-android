/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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

public class GroupMessagePendingMessageIdModel {
	public static final String TABLE = "m_group_message_pending_msg_id";
	public static final String COLUMN_ID = "id";
	public static final String COLUMN_GROUP_MESSAGE_ID = "groupMessageId";
	public static final String COLUMN_API_MESSAGE_ID = "apiMessageId";

	private int groupMessageId;
	private String apiMessageId;

	public GroupMessagePendingMessageIdModel(int groupMessageId, String apiMessageId) {
		this.groupMessageId = groupMessageId;
		this.apiMessageId = apiMessageId;
	}

	public int getGroupMessageId() {
		return groupMessageId;
	}

	public String getApiMessageId() {
		return apiMessageId;
	}
}

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

import androidx.annotation.Nullable;

import java.util.Map;

public class GroupMessageModel extends AbstractMessageModel {
	public static final String TABLE = "m_group_message";
	public static final String COLUMN_GROUP_ID = "groupId";
	public static final String COLUMN_GROUP_MESSAGE_STATES = "groupMessageStates";

	private int groupId;
	private Map<String, Object> groupMessageStates;

	public GroupMessageModel() {
		super();
	}

	public GroupMessageModel(boolean isStatusMessage) {
		super(isStatusMessage);
	}

	/**
	 * Returns the ID of the group model this message belongs to. This is different from the GroupID object!
	 * @return ID of group model
	 */
	public int getGroupId() {
		return this.groupId;
	}

	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}

	@Nullable public Map<String, Object> getGroupMessageStates() {
		return this.groupMessageStates;
	}

	public GroupMessageModel setGroupMessageStates(@Nullable Map<String, Object> groupMessageStates) {
		this.groupMessageStates = groupMessageStates;
		return this;
	}

	@Override
	public String toString() {
		return "group_message.id = " + this.getId();
	}

	/**
	 * Copy relevant data (i.e. data that may change later on) from specified source model to this model.
	 * This is used to update the GroupMessageCache. Don't forget to add new fields here!
	 * @param sourceModel GroupMessageModel from which the data should be copied over
	 */
	public void copyFrom(GroupMessageModel sourceModel) {
		this.dataObject = sourceModel.dataObject;

		this
			.setGroupMessageStates(sourceModel.getGroupMessageStates())
			.setCorrelationId(sourceModel.getCorrelationId())
			.setSaved(sourceModel.isSaved())
			.setState(sourceModel.getState())
			.setModifiedAt(sourceModel.getModifiedAt())
			.setDeliveredAt(sourceModel.getDeliveredAt())
			.setReadAt(sourceModel.getReadAt())
			.setEditedAt(sourceModel.getEditedAt())
			.setDeletedAt(sourceModel.getDeletedAt())
			.setRead(sourceModel.isRead())
			.setBody(sourceModel.getBody())
			.setCaption(sourceModel.getCaption())
			.setQuotedMessageId(sourceModel.getQuotedMessageId())
			.setForwardSecurityMode(sourceModel.getForwardSecurityMode())
		;
	}
}

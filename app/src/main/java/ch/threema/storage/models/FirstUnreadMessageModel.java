/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

/**
 * Only for the the divider
 */
public class FirstUnreadMessageModel extends AbstractMessageModel {
	@Override
	public int getId() {
		return 0;
	}

	@Override
	public String getUid() {
		return null;
	}

	@Override
	public boolean isStatusMessage() {
		return true;
	}

	@Override
	public String getIdentity() {
		return null;
	}

	@Override
	public boolean isOutbox() {
		return false;
	}

	@Override
	public MessageType getType() {
		return MessageType.STATUS;
	}

	@Override
	public String getBody() {
		return null;
	}

	@Override
	public boolean isRead() {
		return false;
	}

	@Override
	public boolean isSaved() {
		return false;
	}

	@Override
	public MessageState getState() {
		return null;
	}

	@Override
	public Date getModifiedAt() {
		return null;
	}

	@Override
	public Date getPostedAt() {
		return null;
	}

	@Override
	public String getApiMessageId() {
		return null;
	}
}

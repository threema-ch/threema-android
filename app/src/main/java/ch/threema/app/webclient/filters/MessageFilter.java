/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

package ch.threema.app.webclient.filters;

import ch.threema.annotation.SameThread;
import ch.threema.app.services.MessageService;
import ch.threema.storage.models.MessageType;

@SameThread
public class MessageFilter implements MessageService.MessageFilter {
	private static int MESSAGE_PAGE_SIZE = 50;
	private Integer referenceId = null;

	@Override
	public long getPageSize() {
		//load one more to check if more messages are available
		 return this.getRealPageSize() + 1;
	}

	public long getRealPageSize() {
		return MESSAGE_PAGE_SIZE;
	}
	@Override
	public Integer getPageReferenceId() {
		return this.referenceId;
	}

	public MessageFilter setPageReferenceId(Integer id) {
		this.referenceId = id;
		return this;
	}

	@Override
	public boolean withStatusMessages() {
		return true;
	}

	@Override
	public boolean withUnsaved() {
		return false;
	}

	@Override
	public boolean onlyUnread() {
		return false;
	}

	@Override
	public boolean onlyDownloaded() {
		return false;
	}

	@Override
	public MessageType[] types() {
		return null;
	}

	@Override
	public int[] contentTypes() {
		return null;
	}

	@Override
	public int[] displayTags() {
		return null;
	}
}

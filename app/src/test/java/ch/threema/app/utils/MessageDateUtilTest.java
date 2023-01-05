/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2023 Threema GmbH
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

import org.junit.Test;

import java.util.Date;

import ch.threema.storage.models.MessageModel;

import static junit.framework.Assert.assertTrue;

public class MessageDateUtilTest  {
	@Test
	public void outgoing() {
		Date dateA = new Date(2014, 1, 1);
		Date dateB = new Date(2014, 1, 2);
		Date dateC = new Date(2014, 1, 3);

		MessageModel messageModel = new MessageModel();
		messageModel.setCreatedAt(dateA);
		messageModel.setPostedAt(dateB);
		messageModel.setModifiedAt(dateC);
		messageModel.setOutbox(true);

		assertTrue(messageModel.isOutbox());

//		Date displayDate = MessageUtil.getDisplayDate()
	}
}

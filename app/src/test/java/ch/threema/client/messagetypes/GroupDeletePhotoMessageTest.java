/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2018-2021 Threema GmbH
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

package ch.threema.client.messagetypes;

import ch.threema.client.GroupDeletePhotoMessage;
import ch.threema.client.GroupId;
import org.junit.Test;

public class GroupDeletePhotoMessageTest {

	@Test
	public void testGetBody() throws Exception {
		final GroupDeletePhotoMessage msg = new GroupDeletePhotoMessage();
		msg.setGroupCreator("GRCREATE");
		GroupId groupId = new GroupId();
		msg.setGroupId(groupId);

		org.junit.Assert.assertArrayEquals(
			groupId.getGroupId(),
			msg.getBody()
		);
	}

}

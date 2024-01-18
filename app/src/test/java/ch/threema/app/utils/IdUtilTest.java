/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

import static junit.framework.Assert.assertEquals;

public class IdUtilTest {

	@Test
	public void getTempUniqueId() {
		final ContactModel contact1 = new ContactModel("AAAAAAAA", new byte[] {0});
		final ContactModel contact2 = new ContactModel("BBBBBBBB", new byte[] {1});
		final ContactModel contact3 = new ContactModel("CCCCCCCC", new byte[] {2});
		final GroupModel group1 = new GroupModel(); group1.setId(1);
		assertEquals(1, IdUtil.getTempId(contact1));
		assertEquals(1, IdUtil.getTempId(contact1));
		assertEquals(2, IdUtil.getTempId(contact3));
		assertEquals(1, IdUtil.getTempId(contact1));
		assertEquals(2, IdUtil.getTempId(contact3));
		assertEquals(3, IdUtil.getTempId(group1));
		assertEquals(4, IdUtil.getTempId(contact2));
	}

}

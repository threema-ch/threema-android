/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

import org.junit.Assert;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;

import ch.threema.storage.models.GroupModel;

@PrepareForTest(GroupUtil.class)
public class GroupUtilTest {

	private String[][] getGroup_SendToCreator()  {
		return new String[][] {
			{"The Group", "ECHOECHO"},
			{"☁The Group", "ECHOECHO"},
			{"☁", "ECHOECHO"},
			{"", "ECHOECHO"},
			{"☁The Group", "*THREEMA"},
			{"☁", "*THREEMA"},
		};
	}


	private String[][] getGroup_DontSendToCreator()  {
		return new String[][] {
			{"The Group", "*THREEMA"},
			{"", "*THREEMA"},
			{"No ☁", "*THREEMA"}
		};
	}

	@Test
	public void sendMessageToCreator_WithModel() {
		for (String[] g: this.getGroup_SendToCreator()) {
			Assert.assertTrue(
				String.format("Send to creator should be true with name: \"%s\" and creator: \"%s\"", g[0], g[1]), GroupUtil.sendMessageToCreator((new GroupModel())
					.setName(g[0])
					.setCreatorIdentity(g[1])));
		}

		for (String[] g: this.getGroup_DontSendToCreator()) {
			Assert.assertFalse(
				String.format("Send to creator should be false with name: \"%s\" and creator: \"%s\"", g[0], g[1]),
				GroupUtil.sendMessageToCreator((new GroupModel())
					.setName(g[0])
					.setCreatorIdentity(g[1])));
		}
	}

	@Test
	public void sendMessageToCreator_WithString() {
		for (String[] g: this.getGroup_SendToCreator()) {
			Assert.assertTrue(
				String.format("Send to creator should be true with name: \"%s\" and creator: \"%s\"", g[0], g[1]),
				GroupUtil.sendMessageToCreator(g[1], g[0])
			);
		}

		for (String[] g: this.getGroup_DontSendToCreator()) {
			Assert.assertFalse(
				String.format("Send to creator should be false with name: \"%s\" and creator: \"%s\"", g[0], g[1]),
				GroupUtil.sendMessageToCreator(g[1], g[0])
			);
		}
	}

}

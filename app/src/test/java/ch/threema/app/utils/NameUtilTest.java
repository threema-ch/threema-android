/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;

import androidx.core.util.Pair;
import ch.threema.app.services.UserService;
import ch.threema.storage.models.ContactModel;

import static junit.framework.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
public class NameUtilTest {
	private UserService userServiceMock;

	private static String meIdentity = "MEMEMEME";
	private static String otherIdentity = "OTHERRRR";

	@Before
	public void setUp() {
		this.userServiceMock = PowerMockito.mock(UserService.class);
		when(this.userServiceMock.isMe(Matchers.eq(meIdentity))).thenReturn(true);
		when(this.userServiceMock.isMe(Matchers.eq(otherIdentity))).thenReturn(false);
		when(this.userServiceMock.getIdentity()).thenReturn(meIdentity);
	}

	@Test
	public void testGetQuoteNameNull() {
		final String name = NameUtil.getQuoteName(null, this.userServiceMock);
		assertEquals("", name);
	}

	@Test
	public void testGetQuoteNameMeNickname() {
		when(this.userServiceMock.getPublicNickname()).thenReturn("Mr. Dushi");

		final ContactModel contactModel = new ContactModel(meIdentity, new byte[]{0, 0, 0});
		contactModel.setFirstName("Moi");
		final String name = NameUtil.getQuoteName(contactModel, this.userServiceMock);
		assertEquals("Mr. Dushi", name);
	}

	@Test
	public void testGetQuoteNameMeIdentityNickname() {
		when(this.userServiceMock.getPublicNickname()).thenReturn(meIdentity);

		final ContactModel contactModel = new ContactModel(meIdentity, new byte[]{0, 0, 0});
		contactModel.setFirstName("Moi");
		final String name = NameUtil.getQuoteName(contactModel, this.userServiceMock);
		assertEquals("Moi", name);
	}

	@Test
	public void testGetQuoteNameMeEmptyNickname() {
		when(this.userServiceMock.getPublicNickname()).thenReturn("");

		final ContactModel contactModel = new ContactModel(meIdentity, new byte[]{0, 0, 0});
		contactModel.setFirstName("Moi");
		final String name = NameUtil.getQuoteName(contactModel, this.userServiceMock);
		assertEquals("Moi", name);
	}

	@Test
	public void testGetQuoteNameMeNullNickname() {
		when(this.userServiceMock.getPublicNickname()).thenReturn(null);

		final ContactModel contactModel = new ContactModel(meIdentity, new byte[]{0, 0, 0});
		contactModel.setFirstName("Moi");
		final String name = NameUtil.getQuoteName(contactModel, this.userServiceMock);
		assertEquals("Moi", name);
	}

	@Test
	public void testGetQuoteNameOtherName() {
		final ContactModel contactModel = new ContactModel(otherIdentity, new byte[]{0, 0, 0});
		contactModel.setPublicNickName("nickname");
		contactModel.setFirstName("Joggeli");
		contactModel.setLastName("Rüdisüli");
		final String name = NameUtil.getQuoteName(contactModel, this.userServiceMock);
		assertEquals("Joggeli Rüdisüli", name);
	}

	@Test
	public void testGetQuoteNameOtherNoName() {
		final ContactModel contactModel = new ContactModel(otherIdentity, new byte[]{0, 0, 0});
		contactModel.setPublicNickName("nickname");
		contactModel.setFirstName(null);
		contactModel.setLastName(null);
		final String name = NameUtil.getQuoteName(contactModel, this.userServiceMock);
		assertEquals("~nickname", name);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameNull() {
		final Pair<String, String> firstLastName = NameUtil.getFirstLastNameFromDisplayName(null);
		Assert.assertEquals("", firstLastName.first);
		Assert.assertEquals("", firstLastName.second);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameEmpty() {
		final Pair<String, String> firstLastName = NameUtil.getFirstLastNameFromDisplayName("");
		Assert.assertEquals("", firstLastName.first);
		Assert.assertEquals("", firstLastName.second);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameOnlyFirst() {
		final Pair<String, String> firstLastName = NameUtil.getFirstLastNameFromDisplayName("joe");
		Assert.assertEquals("joe", firstLastName.first);
		Assert.assertEquals("", firstLastName.second);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameTwoParts() {
		final Pair<String, String> firstLastName = NameUtil.getFirstLastNameFromDisplayName("john doe");
		Assert.assertEquals("john", firstLastName.first);
		Assert.assertEquals("doe", firstLastName.second);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameSpanishCraziness() {
		final Pair<String, String> firstLastName = NameUtil.getFirstLastNameFromDisplayName("Pablo Diego José Francisco de Paula Juan Nepomuceno María de los Remedios Cipriano de la Santísima Trinidad Ruiz y Picasso");
		// Yes, this is actually wrong, but we cannot know how to properly split first and last name.
		Assert.assertEquals("Pablo", firstLastName.first);
		Assert.assertEquals("Diego José Francisco de Paula Juan Nepomuceno María de los Remedios Cipriano de la Santísima Trinidad Ruiz y Picasso", firstLastName.second);
	}

}

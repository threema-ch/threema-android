/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import ch.threema.app.services.FileService;
import ch.threema.app.services.PreferenceService;
import ch.threema.storage.models.ContactModel;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(LogUtil.class)
public class ContactUtilTest {

	private ContactModel createModel(String identity) {
		return new ContactModel(identity, new byte[32]);
	}
	@Test
	public void isLinked() {
		Assert.assertFalse(ContactUtil.isLinked(null));
		Assert.assertFalse(ContactUtil.isLinked(createModel("ECHOECHO").setAndroidContactLookupKey(null)));
		Assert.assertTrue(ContactUtil.isLinked(createModel("ECHOECHO").setAndroidContactLookupKey("abc")));
	}

	@Test
	public void canHaveCustomAvatar() {
		Assert.assertFalse(ContactUtil.canHaveCustomAvatar(null));
		// Normal contact, not linked
		Assert.assertTrue(ContactUtil.canHaveCustomAvatar(createModel("ECHOECHO").setAndroidContactLookupKey(null)));
		// Normal contact, linked
		Assert.assertFalse(ContactUtil.canHaveCustomAvatar(createModel("ECHOECHO").setAndroidContactLookupKey("ABC")));
		// Gateway contact, not linked
		Assert.assertFalse(ContactUtil.canHaveCustomAvatar(createModel("*COMPANY").setAndroidContactLookupKey(null)));
		// Gateway contact, linked
		Assert.assertFalse(ContactUtil.canHaveCustomAvatar(createModel("*COMPANY").setAndroidContactLookupKey("ABC")));
	}

	@Test
	public void canChangeFirstName() {
		Assert.assertFalse(ContactUtil.canChangeFirstName(null));

		Assert.assertTrue(ContactUtil.canChangeFirstName(createModel("*COMPANY").setAndroidContactLookupKey(null)));
		Assert.assertFalse(ContactUtil.canChangeFirstName(createModel("*COMPANY").setAndroidContactLookupKey("abc")));

		Assert.assertTrue(ContactUtil.canChangeFirstName(createModel("ECHOECHO").setAndroidContactLookupKey(null)));
		Assert.assertFalse(ContactUtil.canChangeFirstName(createModel("ECHOECHO").setAndroidContactLookupKey("abc")));
	}


	@Test
	public void canChangeLastName() {
		Assert.assertFalse(ContactUtil.canChangeLastName(null));

		Assert.assertFalse(ContactUtil.canChangeLastName(createModel("*COMPANY").setAndroidContactLookupKey(null)));
		Assert.assertFalse(ContactUtil.canChangeLastName(createModel("*COMPANY").setAndroidContactLookupKey("abc")));

		Assert.assertTrue(ContactUtil.canChangeLastName(createModel("ECHOECHO").setAndroidContactLookupKey(null)));
		Assert.assertFalse(ContactUtil.canChangeLastName(createModel("ECHOECHO").setAndroidContactLookupKey("abc")));
	}

	@Test
	public void canChangeAvatar() {
		PreferenceService preferenceServiceMock = PowerMockito.mock(PreferenceService.class);
		FileService fileServiceMock = PowerMockito.mock(FileService.class);

		// Preferences disabled
		when(preferenceServiceMock.getProfilePicReceive()).thenReturn(false);
		// No contact photo defined
		when(fileServiceMock.hasContactPhotoFile(any(ContactModel.class))).thenReturn(false);

		// Normal contact, not linked
		Assert.assertTrue(ContactUtil.canChangeAvatar(createModel("ECHOECHO").setAndroidContactLookupKey(null), preferenceServiceMock, fileServiceMock));
		// Normal contact, linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("ECHOECHO").setAndroidContactLookupKey("ABC"), preferenceServiceMock, fileServiceMock));
		// Gateway contact, not linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("*COMPANY").setAndroidContactLookupKey(null), preferenceServiceMock, fileServiceMock));
		// Gateway contact, linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("*COMPANY").setAndroidContactLookupKey("ABC"), preferenceServiceMock, fileServiceMock));



		// Preferences disabled
		when(preferenceServiceMock.getProfilePicReceive()).thenReturn(false);
		// Contact photo defined
		when(fileServiceMock.hasContactPhotoFile(any(ContactModel.class))).thenReturn(true);

		// Normal contact, not linked
		Assert.assertTrue(ContactUtil.canChangeAvatar(createModel("ECHOECHO").setAndroidContactLookupKey(null), preferenceServiceMock, fileServiceMock));
		// Normal contact, linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("ECHOECHO").setAndroidContactLookupKey("ABC"), preferenceServiceMock, fileServiceMock));
		// Gateway contact, not linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("*COMPANY").setAndroidContactLookupKey(null), preferenceServiceMock, fileServiceMock));
		// Gateway contact, linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("*COMPANY").setAndroidContactLookupKey("ABC"), preferenceServiceMock, fileServiceMock));



		// Preferences enabled
		when(preferenceServiceMock.getProfilePicReceive()).thenReturn(true);
		// No contact photo defined
		when(fileServiceMock.hasContactPhotoFile(any(ContactModel.class))).thenReturn(false);

		// Normal contact, not linked
		Assert.assertTrue(ContactUtil.canChangeAvatar(createModel("ECHOECHO").setAndroidContactLookupKey(null), preferenceServiceMock, fileServiceMock));
		// Normal contact, linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("ECHOECHO").setAndroidContactLookupKey("ABC"), preferenceServiceMock, fileServiceMock));
		// Gateway contact, not linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("*COMPANY").setAndroidContactLookupKey(null), preferenceServiceMock, fileServiceMock));
		// Gateway contact, linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("*COMPANY").setAndroidContactLookupKey("ABC"), preferenceServiceMock, fileServiceMock));



		// Preferences enabled
		when(preferenceServiceMock.getProfilePicReceive()).thenReturn(true);
		// Contact photo defined
		when(fileServiceMock.hasContactPhotoFile(any(ContactModel.class))).thenReturn(true);

		// Normal contact, not linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("ECHOECHO").setAndroidContactLookupKey(null), preferenceServiceMock, fileServiceMock));
		// Normal contact, linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("ECHOECHO").setAndroidContactLookupKey("ABC"), preferenceServiceMock, fileServiceMock));
		// Gateway contact, not linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("*COMPANY").setAndroidContactLookupKey(null), preferenceServiceMock, fileServiceMock));
		// Gateway contact, linked
		Assert.assertFalse(ContactUtil.canChangeAvatar(createModel("*COMPANY").setAndroidContactLookupKey("ABC"), preferenceServiceMock, fileServiceMock));
	}
}

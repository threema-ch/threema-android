/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
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

package ch.threema.app.service;

import android.content.Context;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.threemasafe.ThreemaSafeService;
import ch.threema.app.threemasafe.ThreemaSafeServiceImpl;
import ch.threema.client.APIConnector;
import ch.threema.client.Utils;
import ch.threema.storage.DatabaseServiceNew;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.crypto.*")
public class ThreemaSafeServiceTest {
	@Mock
	private	Context contextMock;
	@Mock
	private	PreferenceService preferenceServiceMock;
	@Mock
	private	UserService userServiceMock;
	@Mock
	private	ContactService contactServiceMock;
	@Mock
	private	LocaleService localeServiceMock;
	@Mock
	private	FileService fileServiceMock;
	@Mock
	private	DatabaseServiceNew databaseServiceNewMock;
	@Mock
	private	IdentityStore identityStoreMock;
	@Mock
	private	APIConnector apiConnectorMock;
	@Mock
	private IdListService profilePicRecipientsService;
	@Mock
	private DeadlineListService hiddenContactsListMock;

	// Test vector: Password "shootdeathstar" and salt "ECHOECHO" should result in this master key
	private static final String MASTER_KEY_HEX = "066384d3695fbbd9f31a7d533900fd0cd8d1373beb6a28678522d2a49980c9c351c3d8d752fb6e1fd3199ead7f0895d6e3893ff691f2a5ee1976ed0897fc2f66";

	private ThreemaSafeService getService() {
		return new ThreemaSafeServiceImpl(
			contextMock, preferenceServiceMock, userServiceMock, contactServiceMock,
			localeServiceMock, fileServiceMock, profilePicRecipientsService, databaseServiceNewMock,
			identityStoreMock, apiConnectorMock, hiddenContactsListMock
		);
	}

	@Test
	public void testDeriveMasterKey() {
		final ThreemaSafeService service = PowerMockito.mock(ThreemaSafeServiceImpl.class);
		doCallRealMethod().when(service).deriveMasterKey(any(String.class), any(String.class));

		// Test case as defined in specification (see confluence)
		final byte[] masterKey = service.deriveMasterKey("shootdeathstar", "ECHOECHO");
		final String masterKeyHex = Utils.byteArrayToHexString(masterKey).toLowerCase();
		Assert.assertEquals(
			MASTER_KEY_HEX,
			masterKeyHex
		);
	}

	@Test
	public void testGetThreemaSafeBackupIdNull() throws Exception {
		final ThreemaSafeService service = getService();

		when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(null);
		final byte[] backupId1 = service.getThreemaSafeBackupId();
		Assert.assertNull(backupId1);

		when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(new byte[0]);
		final byte[] backupId2 = service.getThreemaSafeBackupId();
		Assert.assertNull(backupId2);
	}

	@Test
	public void testGetThreemaSafeBackupId() throws Exception {
		final ThreemaSafeService service = getService();
		when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(Utils.hexStringToByteArray(MASTER_KEY_HEX));
		final byte[] backupId = service.getThreemaSafeBackupId();
		final String backupIdHex = Utils.byteArrayToHexString(backupId).toLowerCase();
		Assert.assertEquals(
			"066384d3695fbbd9f31a7d533900fd0cd8d1373beb6a28678522d2a49980c9c3",
			backupIdHex
		);
	}

	@Test
	public void testGetThreemaSafeEncryptionKeyNull() throws Exception {
		final ThreemaSafeService service = getService();

		when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(null);
		final byte[] encryptionKey1 = service.getThreemaSafeEncryptionKey();
		Assert.assertNull(encryptionKey1);

		when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(new byte[0]);
		final byte[] encryptionKey2 = service.getThreemaSafeEncryptionKey();
		Assert.assertNull(encryptionKey2);
	}

	@Test
	public void testGetThreemaSafeEncryptionKey() throws Exception {
		final ThreemaSafeService service = getService();
		when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(Utils.hexStringToByteArray(MASTER_KEY_HEX));
		final byte[] encryptionKey = service.getThreemaSafeEncryptionKey();
		final String encryptionKeyHex = Utils.byteArrayToHexString(encryptionKey).toLowerCase();
		Assert.assertEquals(
			"51c3d8d752fb6e1fd3199ead7f0895d6e3893ff691f2a5ee1976ed0897fc2f66",
			encryptionKeyHex
		);
	}
}

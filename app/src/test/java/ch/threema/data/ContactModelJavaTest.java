/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.data;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Date;

import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.storage.DatabaseBackend;
import ch.threema.domain.models.ContactSyncState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.ReadReceiptPolicy;
import ch.threema.domain.models.TypingIndicatorPolicy;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.models.WorkVerificationLevel;
import ch.threema.storage.models.ContactModel.AcquaintanceLevel;
import ch.threema.storage.models.ContactModel.State;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.powermock.api.mockito.PowerMockito.mock;

public class ContactModelJavaTest {
	private final DatabaseBackend databaseBackendMock = mock(DatabaseBackend.class);

	/**
     * Test the construction using the primary constructor from Java.
     */
    @Test
    public void testConstruction() {
		final Date createdAt = new Date();
		final byte[] publicKey = new byte[32];
		final BigInteger largeBigInteger = new BigInteger("18446744073709551600");
		final String identity = "TESTTEST";
        final ContactModel contact = new ContactModel(identity, ContactModelData.javaCreate(
	        identity,
	        publicKey,
	        createdAt,
	        "Test",
	        "Contact",
	        null,
	        42,
	        VerificationLevel.SERVER_VERIFIED,
	        WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
	        IdentityType.NORMAL,
	        AcquaintanceLevel.DIRECT,
	        State.ACTIVE,
	        largeBigInteger,
	        ContactSyncState.CUSTOM,
			ReadReceiptPolicy.DONT_SEND,
	        TypingIndicatorPolicy.SEND,
	        "asdf",
	        null,
	        false,
	        new byte[] { 1, 2, 3 }
        ), databaseBackendMock);

		final ContactModelData data = contact.getData().getValue();
        assertEquals("TESTTEST", data.identity);
		assertEquals(publicKey, data.publicKey);
        assertEquals("Test", data.firstName);
        assertEquals("Contact", data.lastName);
	    assertNull(data.nickname);
	    assertEquals(42, data.colorIndexInt());
	    assertEquals(VerificationLevel.SERVER_VERIFIED, data.verificationLevel);
	    assertEquals(WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED, data.workVerificationLevel);
	    assertEquals(IdentityType.NORMAL, data.identityType);
	    assertEquals(AcquaintanceLevel.DIRECT, data.acquaintanceLevel);
	    assertEquals(State.ACTIVE, data.activityState);
	    assertEquals(largeBigInteger, data.featureMaskBigInteger());
	    try {
		    data.featureMaskLong();
			fail("featureMaskLong did not throw");
	    } catch (IllegalArgumentException exception) {
			assertEquals("Feature mask does not fit in a signed long", exception.getMessage());
	    }
	    assertEquals(ContactSyncState.CUSTOM, data.syncState);
		assertEquals(ReadReceiptPolicy.DONT_SEND, data.readReceiptPolicy);
		assertEquals(TypingIndicatorPolicy.SEND, data.typingIndicatorPolicy);
		assertEquals("asdf", data.androidContactLookupKey);
		assertNull(data.localAvatarExpires);
		assertFalse(data.isRestored);
	    assertArrayEquals(new byte[] { 1, 2, 3 }, data.profilePictureBlobId);
    }
}

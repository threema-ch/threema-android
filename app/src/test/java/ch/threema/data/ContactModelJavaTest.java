/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Date;

import ch.threema.app.managers.CoreServiceManager;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.data.storage.DatabaseBackend;
import ch.threema.domain.models.ContactSyncState;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.ReadReceiptPolicy;
import ch.threema.domain.models.TypingIndicatorPolicy;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.models.WorkVerificationLevel;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.storage.models.ContactModel.AcquaintanceLevel;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

public class ContactModelJavaTest {
    private final DatabaseBackend databaseBackendMock = mock(DatabaseBackend.class);
    private final CoreServiceManager coreServiceManagerMock = mock(CoreServiceManager.class);
    private final ContactModelRepository contactModelRepository = new ContactModelRepository(
        new ModelTypeCache<>(), databaseBackendMock, coreServiceManagerMock
    );
    private final MultiDeviceManager multiDeviceManagerMock = mock(MultiDeviceManager.class);
    private final TaskManager taskManagerMock = mock(TaskManager.class);

    @Before
    public void init() {
        when(coreServiceManagerMock.getMultiDeviceManager()).thenReturn(multiDeviceManagerMock);
        when(coreServiceManagerMock.getTaskManager()).thenReturn(taskManagerMock);
    }

    /**
     * Test the construction using the primary constructor from Java.
     */
    @Test
    public void testConstruction() {
        final Date createdAt = new Date();
        final byte[] publicKey = new byte[32];
        final BigInteger largeBigInteger = new BigInteger("18446744073709551600");
        final String identity = "TESTTEST";
        final ContactModel contact = new ContactModel(
            identity,
            ContactModelData.javaCreate(
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
                IdentityState.ACTIVE,
                largeBigInteger,
                ContactSyncState.CUSTOM,
                ReadReceiptPolicy.DONT_SEND,
                TypingIndicatorPolicy.SEND,
                false,
                "asdf",
                null,
                false,
                new byte[]{1, 2, 3},
                null,
                null,
                null
            ),
            databaseBackendMock,
            contactModelRepository,
            coreServiceManagerMock
        );

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
        assertEquals(IdentityState.ACTIVE, data.activityState);
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
        assertArrayEquals(new byte[]{1, 2, 3}, data.profilePictureBlobId);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_firstname_trimmed_1() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("  Firstname  ");
        javaContactModel.setLastName("");
        javaContactModel.setPublicNickName("");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true);

        // assert
        Assert.assertEquals("Firstname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_firstname_trimmed_2() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("  Firstname  ");
        javaContactModel.setLastName("  ");
        javaContactModel.setPublicNickName("");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(false);

        // assert
        Assert.assertEquals("Firstname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_lastname_trimmed_1() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("  ");
        javaContactModel.setLastName("  Lastname  ");
        javaContactModel.setPublicNickName("");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true);

        // assert
        Assert.assertEquals("Lastname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_lastname_trimmed_2() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("  ");
        javaContactModel.setLastName("  Lastname  ");
        javaContactModel.setPublicNickName("");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(false);

        // assert
        Assert.assertEquals("Lastname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_firstname_lastname() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("Firstname");
        javaContactModel.setLastName("Lastname");
        javaContactModel.setPublicNickName("");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true);

        // assert
        Assert.assertEquals("Firstname Lastname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_lastname_firstname() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("Firstname");
        javaContactModel.setLastName("Lastname");
        javaContactModel.setPublicNickName("");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(false);

        // assert
        Assert.assertEquals("Lastname Firstname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_firstname_lastname_trimmed() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("  Firstname  ");
        javaContactModel.setLastName("  Lastname  ");
        javaContactModel.setPublicNickName("");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true);

        // assert
        Assert.assertEquals("Firstname Lastname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_lastname_firstname_trimmed() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("  Firstname  ");
        javaContactModel.setLastName("  Lastname  ");
        javaContactModel.setPublicNickName("");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(false);

        // assert
        Assert.assertEquals("Lastname Firstname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_value_first_lastname_over_nickname() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("Firstname");
        javaContactModel.setLastName("Lastname");
        javaContactModel.setPublicNickName("Nickname");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true);

        // assert
        Assert.assertEquals("Firstname Lastname", contactListItemTextTopLeft);
        Assert.assertFalse(contactListItemTextTopLeft.contains("Nickname"));
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_nickname() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("");
        javaContactModel.setLastName("");
        javaContactModel.setPublicNickName("Nickname");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true);

        // assert
        Assert.assertEquals("~Nickname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_nickname_when_first_lastname_blank() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("   ");
        javaContactModel.setLastName("   ");
        javaContactModel.setPublicNickName("Nickname");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true);

        // assert
        Assert.assertEquals("~Nickname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_nickname_trimmed() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("");
        javaContactModel.setLastName("");
        javaContactModel.setPublicNickName("   Nickname   ");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true);

        // assert
        Assert.assertEquals("~Nickname", contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextTopLeft_should_return_identity() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setFirstName("  ");
        javaContactModel.setLastName("  ");
        javaContactModel.setPublicNickName("  ");

        // act
        final String contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true);

        // assert
        Assert.assertEquals(identity, contactListItemTextTopLeft);
    }

    @Test
    public void getContactListItemTextBottomLeft_should_return_job_title() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setJobTitle("Android Dev");
        javaContactModel.setPublicNickName("Nickname");

        // act
        final String contactListItemTextBottomLeft = javaContactModel.getContactListItemTextBottomLeft();

        // assert
        Assert.assertEquals("Android Dev", contactListItemTextBottomLeft);
    }

    @Test
    public void getContactListItemTextBottomLeft_should_not_return_job_title() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(false);
        javaContactModel.setJobTitle("Android Dev");
        javaContactModel.setPublicNickName("Nickname");

        // act
        final String contactListItemTextBottomLeft = javaContactModel.getContactListItemTextBottomLeft();

        // assert
        Assert.assertEquals("~Nickname", contactListItemTextBottomLeft);
    }

    @Test
    public void getContactListItemTextBottomLeft_should_return_job_title_trimmed() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setJobTitle("   Android Dev   ");
        javaContactModel.setPublicNickName("Nickname");

        // act
        final String contactListItemTextBottomLeft = javaContactModel.getContactListItemTextBottomLeft();

        // assert
        Assert.assertEquals("Android Dev", contactListItemTextBottomLeft);
    }

    @Test
    public void getContactListItemTextBottomLeft_should_return_nickname_1() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setJobTitle("");
        javaContactModel.setPublicNickName("Nickname");

        // act
        final String contactListItemTextBottomLeft = javaContactModel.getContactListItemTextBottomLeft();

        // assert
        Assert.assertEquals("~Nickname", contactListItemTextBottomLeft);
    }

    @Test
    public void getContactListItemTextBottomLeft_should_return_nickname_2() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setJobTitle("  ");
        javaContactModel.setPublicNickName("Nickname");

        // act
        final String contactListItemTextBottomLeft = javaContactModel.getContactListItemTextBottomLeft();

        // assert
        Assert.assertEquals("~Nickname", contactListItemTextBottomLeft);
    }

    @Test
    public void getContactListItemTextBottomLeft_should_return_nickname_3() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setJobTitle(null);
        javaContactModel.setPublicNickName("Nickname");

        // act
        final String contactListItemTextBottomLeft = javaContactModel.getContactListItemTextBottomLeft();

        // assert
        Assert.assertEquals("~Nickname", contactListItemTextBottomLeft);
    }

    @Test
    public void getContactListItemTextBottomLeft_should_return_nickname_trimmed() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setJobTitle(null);
        javaContactModel.setPublicNickName("  Nickname  ");

        // act
        final String contactListItemTextBottomLeft = javaContactModel.getContactListItemTextBottomLeft();

        // assert
        Assert.assertEquals("~Nickname", contactListItemTextBottomLeft);
    }

    @Test
    public void getContactListItemTextBottomLeft_should_return_empty() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setJobTitle(null);
        javaContactModel.setPublicNickName(null);

        // act
        final String contactListItemTextBottomLeft = javaContactModel.getContactListItemTextBottomLeft();

        // assert
        Assert.assertEquals("", contactListItemTextBottomLeft);
    }

    @Test
    public void getContactListItemTextBottomRight_should_return_department_trimmed() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setDepartment("  Android  ");

        // act
        final String contactListItemTextBottomRight = javaContactModel.getContactListItemTextBottomRight();

        // assert
        Assert.assertEquals("Android", contactListItemTextBottomRight);
    }

    @Test
    public void getContactListItemTextBottomRight_should_not_return_department() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(false);
        javaContactModel.setDepartment("Android");

        // act
        final String contactListItemTextBottomRight = javaContactModel.getContactListItemTextBottomRight();

        // assert
        Assert.assertEquals(identity, contactListItemTextBottomRight);
    }

    @Test
    public void getContactListItemTextBottomRight_should_return_identity_1() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setDepartment(null);

        // act
        final String contactListItemTextBottomRight = javaContactModel.getContactListItemTextBottomRight();

        // assert
        Assert.assertEquals(identity, contactListItemTextBottomRight);
    }

    @Test
    public void getContactListItemTextBottomRight_should_return_identity_2() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setDepartment("");

        // act
        final String contactListItemTextBottomRight = javaContactModel.getContactListItemTextBottomRight();

        // assert
        Assert.assertEquals(identity, contactListItemTextBottomRight);
    }

    @Test
    public void getContactListItemTextBottomRight_should_return_identity_3() {

        // arrange
        final String identity = "IDENTITY";
        final byte[] publicKey = new byte[32];
        final ch.threema.storage.models.ContactModel javaContactModel = ch.threema.storage.models.ContactModel.create(
            identity,
            publicKey
        );
        javaContactModel.setIsWork(true);
        javaContactModel.setDepartment("  ");

        // act
        final String contactListItemTextBottomRight = javaContactModel.getContactListItemTextBottomRight();

        // assert
        Assert.assertEquals(identity, contactListItemTextBottomRight);
    }
}

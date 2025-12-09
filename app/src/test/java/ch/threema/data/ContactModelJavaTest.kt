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

package ch.threema.data

import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.common.now
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModelData.Companion.javaCreate
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.models.ContactModel
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ContactModelJavaTest {
    private val databaseBackendMock = mockk<DatabaseBackend>()
    private val coreServiceManagerMock = mockk<CoreServiceManager>()
    private val contactModelRepository = ContactModelRepository(
        ModelTypeCache(),
        databaseBackendMock,
        coreServiceManagerMock,
    )
    private val multiDeviceManagerMock = mockk<MultiDeviceManager>()
    private val taskManagerMock = mockk<TaskManager>()

    @BeforeTest
    fun init() {
        every { coreServiceManagerMock.multiDeviceManager } returns multiDeviceManagerMock
        every { coreServiceManagerMock.taskManager } returns taskManagerMock
    }

    /**
     * Test the construction using the primary constructor from Java.
     */
    @Test
    fun testConstruction() {
        val createdAt = now()
        val publicKey = ByteArray(32)
        val largeBigInteger = BigInteger("18446744073709551600")
        val identity = "TESTTEST"
        val contact = ch.threema.data.models.ContactModel(
            identity,
            javaCreate(
                identity = identity,
                publicKey = publicKey,
                createdAt = createdAt,
                firstName = "Test",
                lastName = "Contact",
                nickname = null,
                idColor = IdColor(10),
                verificationLevel = VerificationLevel.SERVER_VERIFIED,
                workVerificationLevel = WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
                identityType = IdentityType.NORMAL,
                acquaintanceLevel = ContactModel.AcquaintanceLevel.DIRECT,
                activityState = IdentityState.ACTIVE,
                featureMask = largeBigInteger,
                syncState = ContactSyncState.CUSTOM,
                readReceiptPolicy = ReadReceiptPolicy.DONT_SEND,
                typingIndicatorPolicy = TypingIndicatorPolicy.SEND,
                isArchived = false,
                androidContactLookupInfo = AndroidContactLookupInfo(lookupKey = "asdf", contactId = null),
                localAvatarExpires = null,
                isRestored = false,
                profilePictureBlobId = byteArrayOf(1, 2, 3),
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
            ),
            databaseBackendMock,
            contactModelRepository,
            coreServiceManagerMock,
        )

        val data = contact.data!!
        assertEquals("TESTTEST", data.identity)
        assertEquals(publicKey, data.publicKey)
        assertEquals("Test", data.firstName)
        assertEquals("Contact", data.lastName)
        assertNull(data.nickname)
        assertEquals(10, data.idColor.colorIndex)
        assertEquals(VerificationLevel.SERVER_VERIFIED, data.verificationLevel)
        assertEquals(WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED, data.workVerificationLevel)
        assertEquals(IdentityType.NORMAL, data.identityType)
        assertEquals(ContactModel.AcquaintanceLevel.DIRECT, data.acquaintanceLevel)
        assertEquals(IdentityState.ACTIVE, data.activityState)
        assertEquals(largeBigInteger, data.featureMaskBigInteger())
        val exception = assertFailsWith<IllegalArgumentException> {
            data.featureMaskLong()
        }
        assertEquals("Feature mask does not fit in a signed long", exception.message)
        assertEquals(ContactSyncState.CUSTOM, data.syncState)
        assertEquals(ReadReceiptPolicy.DONT_SEND, data.readReceiptPolicy)
        assertEquals(TypingIndicatorPolicy.SEND, data.typingIndicatorPolicy)
        assertEquals(AndroidContactLookupInfo(lookupKey = "asdf", contactId = null), data.androidContactLookupInfo)
        assertNull(data.localAvatarExpires)
        assertFalse(data.isRestored)
        assertContentEquals(byteArrayOf(1, 2, 3), data.profilePictureBlobId)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_firstname_trimmed_1() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "  Firstname  "
        javaContactModel.lastName = ""
        javaContactModel.setPublicNickName("")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true)

        // assert
        assertEquals("Firstname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_firstname_trimmed_2() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "  Firstname  "
        javaContactModel.lastName = "  "
        javaContactModel.setPublicNickName("")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(false)

        // assert
        assertEquals("Firstname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_lastname_trimmed_1() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "  "
        javaContactModel.lastName = "  Lastname  "
        javaContactModel.setPublicNickName("")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true)

        // assert
        assertEquals("Lastname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_lastname_trimmed_2() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "  "
        javaContactModel.lastName = "  Lastname  "
        javaContactModel.setPublicNickName("")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(false)

        // assert
        assertEquals("Lastname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_firstname_lastname() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "Firstname"
        javaContactModel.lastName = "Lastname"
        javaContactModel.setPublicNickName("")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true)

        // assert
        assertEquals("Firstname Lastname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_lastname_firstname() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "Firstname"
        javaContactModel.lastName = "Lastname"
        javaContactModel.setPublicNickName("")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(false)

        // assert
        assertEquals("Lastname Firstname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_firstname_lastname_trimmed() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "  Firstname  "
        javaContactModel.lastName = "  Lastname  "
        javaContactModel.setPublicNickName("")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true)

        // assert
        assertEquals("Firstname Lastname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_lastname_firstname_trimmed() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "  Firstname  "
        javaContactModel.lastName = "  Lastname  "
        javaContactModel.setPublicNickName("")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(false)

        // assert
        assertEquals("Lastname Firstname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_value_first_lastname_over_nickname() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "Firstname"
        javaContactModel.lastName = "Lastname"
        javaContactModel.setPublicNickName("Nickname")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true)

        // assert
        assertEquals("Firstname Lastname", contactListItemTextTopLeft)
        assertFalse(contactListItemTextTopLeft.contains("Nickname"))
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_nickname() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = ""
        javaContactModel.lastName = ""
        javaContactModel.setPublicNickName("Nickname")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true)

        // assert
        assertEquals("~Nickname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_nickname_when_first_lastname_blank() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "   "
        javaContactModel.lastName = "   "
        javaContactModel.setPublicNickName("Nickname")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true)

        // assert
        assertEquals("~Nickname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_nickname_trimmed() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = ""
        javaContactModel.lastName = ""
        javaContactModel.setPublicNickName("   Nickname   ")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true)

        // assert
        assertEquals("~Nickname", contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextTopLeft_should_return_identity() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.firstName = "  "
        javaContactModel.lastName = "  "
        javaContactModel.setPublicNickName("  ")

        // act
        val contactListItemTextTopLeft = javaContactModel.getContactListItemTextTopLeft(true)

        // assert
        assertEquals(identity, contactListItemTextTopLeft)
    }

    @Test
    fun getContactListItemTextBottomLeft_should_return_job_title() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setJobTitle("Android Dev")
        javaContactModel.setPublicNickName("Nickname")

        // act
        val contactListItemTextBottomLeft = javaContactModel.contactListItemTextBottomLeft

        // assert
        assertEquals("Android Dev", contactListItemTextBottomLeft)
    }

    @Test
    fun getContactListItemTextBottomLeft_should_not_return_job_title() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(false)
        javaContactModel.setJobTitle("Android Dev")
        javaContactModel.setPublicNickName("Nickname")

        // act
        val contactListItemTextBottomLeft = javaContactModel.contactListItemTextBottomLeft

        // assert
        assertEquals("~Nickname", contactListItemTextBottomLeft)
    }

    @Test
    fun getContactListItemTextBottomLeft_should_return_job_title_trimmed() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setJobTitle("   Android Dev   ")
        javaContactModel.setPublicNickName("Nickname")

        // act
        val contactListItemTextBottomLeft = javaContactModel.contactListItemTextBottomLeft

        // assert
        assertEquals("Android Dev", contactListItemTextBottomLeft)
    }

    @Test
    fun getContactListItemTextBottomLeft_should_return_nickname_1() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setJobTitle("")
        javaContactModel.setPublicNickName("Nickname")

        // act
        val contactListItemTextBottomLeft = javaContactModel.contactListItemTextBottomLeft

        // assert
        assertEquals("~Nickname", contactListItemTextBottomLeft)
    }

    @Test
    fun getContactListItemTextBottomLeft_should_return_nickname_2() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setJobTitle("  ")
        javaContactModel.setPublicNickName("Nickname")

        // act
        val contactListItemTextBottomLeft = javaContactModel.contactListItemTextBottomLeft

        // assert
        assertEquals("~Nickname", contactListItemTextBottomLeft)
    }

    @Test
    fun getContactListItemTextBottomLeft_should_return_nickname_3() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setJobTitle(null)
        javaContactModel.setPublicNickName("Nickname")

        // act
        val contactListItemTextBottomLeft = javaContactModel.contactListItemTextBottomLeft

        // assert
        assertEquals("~Nickname", contactListItemTextBottomLeft)
    }

    @Test
    fun getContactListItemTextBottomLeft_should_return_nickname_trimmed() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setJobTitle(null)
        javaContactModel.setPublicNickName("  Nickname  ")

        // act
        val contactListItemTextBottomLeft = javaContactModel.contactListItemTextBottomLeft

        // assert
        assertEquals("~Nickname", contactListItemTextBottomLeft)
    }

    @Test
    fun getContactListItemTextBottomLeft_should_return_empty() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setJobTitle(null)
        javaContactModel.setPublicNickName(null)

        // act
        val contactListItemTextBottomLeft = javaContactModel.contactListItemTextBottomLeft

        // assert
        assertEquals("", contactListItemTextBottomLeft)
    }

    @Test
    fun getContactListItemTextBottomRight_should_return_department_trimmed() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setDepartment("  Android  ")

        // act
        val contactListItemTextBottomRight = javaContactModel.contactListItemTextBottomRight

        // assert
        assertEquals("Android", contactListItemTextBottomRight)
    }

    @Test
    fun getContactListItemTextBottomRight_should_not_return_department() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(false)
        javaContactModel.setDepartment("Android")

        // act
        val contactListItemTextBottomRight = javaContactModel.contactListItemTextBottomRight

        // assert
        assertEquals(identity, contactListItemTextBottomRight)
    }

    @Test
    fun getContactListItemTextBottomRight_should_return_identity_1() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setDepartment(null)

        // act
        val contactListItemTextBottomRight = javaContactModel.contactListItemTextBottomRight

        // assert
        assertEquals(identity, contactListItemTextBottomRight)
    }

    @Test
    fun getContactListItemTextBottomRight_should_return_identity_2() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setDepartment("")

        // act
        val contactListItemTextBottomRight = javaContactModel.contactListItemTextBottomRight

        // assert
        assertEquals(identity, contactListItemTextBottomRight)
    }

    @Test
    fun getContactListItemTextBottomRight_should_return_identity_3() {
        // arrange
        val identity = "IDENTITY"
        val publicKey = ByteArray(32)
        val javaContactModel = ContactModel.create(
            identity,
            publicKey,
        )
        javaContactModel.setIsWork(true)
        javaContactModel.setDepartment("  ")

        // act
        val contactListItemTextBottomRight = javaContactModel.contactListItemTextBottomRight

        // assert
        assertEquals(identity, contactListItemTextBottomRight)
    }
}

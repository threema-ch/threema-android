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

package ch.threema.data

import android.text.format.DateUtils
import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.ListenerManager
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.RepositoryToken
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.ContactModel.State
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.powermock.api.mockito.PowerMockito
import java.util.Date
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

// Used for testing, this is fine™
class TestRepositoryToken : RepositoryToken

/**
 * Track calls to the contact listener.
 */
private class ContactListenerTracker {
    val onNew = mutableListOf<String>()
    val onModified = mutableListOf<String>()
    val onAvatarChanged = mutableListOf<ch.threema.storage.models.ContactModel?>()
    val onRemoved = mutableListOf<String>()

    val listener = object : ContactListener {
        override fun onNew(identity: String) {
            onNew.add(identity)
        }

        override fun onModified(identity: String) {
            onModified.add(identity)
        }

        override fun onAvatarChanged(contactModel: ch.threema.storage.models.ContactModel?) {
            onAvatarChanged.add(contactModel)
        }

        override fun onRemoved(identity: String) {
            onRemoved.add(identity)
        }
    }

    fun subscribe() {
        ListenerManager.contactListeners.add(this.listener)
    }

    fun unsubscribe() {
        ListenerManager.contactListeners.remove(this.listener)
    }
}

class ContactModelTest {
    private val databaseBackendMock = PowerMockito.mock(DatabaseBackend::class.java)

    private lateinit var contactListenerTracker: ContactListenerTracker

    private fun createTestContact(isRestored: Boolean = false): ContactModel {
        val identity = "TESTTEST"
        return ContactModel(
            identity, ContactModelData(
                identity,
                Random.nextBytes(32),
                Date(),
                "Test",
                "Contact",
                null,
                13u,
                VerificationLevel.FULLY_VERIFIED,
                WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
                IdentityType.NORMAL,
                AcquaintanceLevel.DIRECT,
                State.ACTIVE,
                ContactSyncState.INITIAL,
                7uL,
                ReadReceiptPolicy.DONT_SEND,
                TypingIndicatorPolicy.SEND,
                null,
                null,
                isRestored,
                null,
                null,
                null,
            ), databaseBackendMock
        )
    }

    @Before
    fun beforeEach() {
        this.contactListenerTracker = ContactListenerTracker()
        this.contactListenerTracker.subscribe()
    }

    @After
    fun afterEach() {
        this.contactListenerTracker.unsubscribe()
    }

    /**
     * Test the construction using the primary constructor.
     *
     * Data is accessed through the `data` state flow.
     */
    @Test
    fun testConstruction() {
        val publicKey = Random.nextBytes(32)
        val createdAt = Date()
        val localAvatarExpires = Date()
        val identity = "TESTTEST"
        val contact = ContactModel(
            identity, ContactModelData(
                identity,
                publicKey,
                createdAt,
                "Test",
                "Contact",
                null,
                13u,
                VerificationLevel.FULLY_VERIFIED,
                WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
                IdentityType.NORMAL,
                AcquaintanceLevel.DIRECT,
                State.ACTIVE,
                ContactSyncState.INITIAL,
                7uL,
                ReadReceiptPolicy.SEND,
                TypingIndicatorPolicy.DONT_SEND,
                null,
                localAvatarExpires,
                true,
                byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
                null,
                null,
            ), databaseBackendMock
        )

        val value = contact.data.value!!
        assertEquals("TESTTEST", value.identity)
        assertEquals(publicKey, value.publicKey)
        assertEquals(createdAt, value.createdAt)
        assertEquals("Test", value.firstName)
        assertEquals("Contact", value.lastName)
        assertNull(value.nickname)
        assertEquals(13u, value.colorIndex)
        assertEquals(VerificationLevel.FULLY_VERIFIED, value.verificationLevel)
        assertEquals(WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED, value.workVerificationLevel)
        assertEquals(IdentityType.NORMAL, value.identityType)
        assertEquals(AcquaintanceLevel.DIRECT, value.acquaintanceLevel)
        assertEquals(State.ACTIVE, value.activityState)
        assertEquals(ContactSyncState.INITIAL, value.syncState)
        assertEquals(7uL, value.featureMask)
        assertEquals(ReadReceiptPolicy.SEND, value.readReceiptPolicy)
        assertEquals(TypingIndicatorPolicy.DONT_SEND, value.typingIndicatorPolicy)
        assertNull(value.androidContactLookupKey)
        assertEquals(localAvatarExpires, value.localAvatarExpires)
        assertTrue { value.isRestored }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), value.profilePictureBlobId)
    }

    @Test
    fun testSetNickname() {
        val contact = createTestContact()
        assertEquals(null, contact.data.value!!.nickname)
        assertEquals(0, contactListenerTracker.onModified.size)

        // Setting nickname should update data and notify modification listeners
        contact.setNicknameFromSync("nicky")
        assertEquals("nicky", contact.data.value!!.nickname)
        assertEquals(1, contactListenerTracker.onModified.size)

        // Setting nickname again to the same value should not notify listeners
        contact.setNicknameFromSync("nicky")
        assertEquals("nicky", contact.data.value!!.nickname)
        assertEquals(1, contactListenerTracker.onModified.size)

        // Removing nickname should notify listeners
        contact.setNicknameFromSync(null)
        assertEquals(null, contact.data.value!!.nickname)
        assertEquals(2, contactListenerTracker.onModified.size)

        // All listeners should have been notified for our test contact
        assertTrue("Contact listener onModified called for wrong identity") {
            contactListenerTracker.onModified.all { it == contact.identity }
        }
    }

    @Test
    fun testDisplayName() {
        val contact = createTestContact()
        contact.setNicknameFromSync("nicky")

        contact.setNameFromLocal("Test", "Contact")
        assertEquals("Test Contact", contact.data.value!!.getDisplayName())

        contact.setNameFromLocal("", "Lastname")
        assertEquals("Lastname", contact.data.value!!.getDisplayName())

        contact.setNameFromLocal("", "")
        assertEquals("~nicky", contact.data.value!!.getDisplayName())

        contact.setNicknameFromSync(null)
        assertEquals(contact.data.value!!.identity, contact.data.value!!.getDisplayName())

        contact.setNicknameFromSync(contact.data.value!!.identity)
        assertEquals(contact.data.value!!.identity, contact.data.value!!.getDisplayName())
    }

    @Test
    fun testConstructorValidateIdentity() {
        val data = createTestContact().data.value!!.copy(identity = "AAAAAAAA")
        Assert.assertThrows(AssertionError::class.java) {
            ContactModel("BBBBBBBB", data, databaseBackendMock)
        }
    }

    @Test
    fun testDeleteIndirect() {
        val contact = createTestContact()
        assertNotNull(contact.data.value)

        // Delete only model state, not database entry
        contact.delete(TestRepositoryToken(), false)
        assertNull(contact.data.value)

        // No interaction with database backend should take place
        verifyNoInteractions(databaseBackendMock)

        // Listeners should not be called
        assertEquals(0, contactListenerTracker.onRemoved.size)
    }

    @Test
    fun testDeleteDirect() {
        val contact = createTestContact()
        assertNotNull(contact.data.value)

        // Delete only model state, not database entry
        contact.delete(TestRepositoryToken(), true)
        assertNull(contact.data.value)

        // Ensure that the contact was deleted from the database backend
        verify(databaseBackendMock, times(1))
            .deleteContactByIdentity(contact.identity)

        assertEquals(1, contactListenerTracker.onRemoved.size)
        assertEquals(contact.identity, contactListenerTracker.onRemoved[0])
    }

    @Test
    fun testAndroidContactLookupKey() {
        val contact = createTestContact()

        assertEquals(0, contactListenerTracker.onModified.size)

        contact.data.value!!.let {
            assertNull(it.androidContactLookupKey)
            assertFalse { it.isLinkedToAndroidContact() }
        }

        contact.setAndroidLookupKey("foo/bar")
        contact.data.value!!.let {
            assertEquals("foo/bar", it.androidContactLookupKey)
            assertTrue { it.isLinkedToAndroidContact() }
        }
        assertEquals(1, contactListenerTracker.onModified.size)
    }

    @Test
    fun testLocalAvatarExpires() {
        val contact = createTestContact()

        // Initially null
        assertNull(contact.data.value!!.androidContactLookupKey)

        // Set date
        val inOneDay = Date(System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS)
        contact.setLocalAvatarExpires(inOneDay)
        assertEquals(inOneDay.time, contact.data.value!!.localAvatarExpires?.time)
        assertFalse(contact.data.value?.isAvatarExpired() ?: fail("No data"))

        // Reset to null
        contact.setLocalAvatarExpires(null)
        assertNull(contact.data.value!!.androidContactLookupKey)
        assertTrue(contact.data.value?.isAvatarExpired() ?: fail("No data"))

        // Change listener not called
        assertEquals(0, contactListenerTracker.onModified.size)
    }

    @Test
    fun testClearIsRestored() {
        val contact = createTestContact(isRestored = true)

        // Initially true
        assertTrue { contact.data.value!!.isRestored }

        // Clear
        contact.clearIsRestored()
        assertFalse { contact.data.value!!.isRestored }

        // Change listener not called
        assertEquals(0, contactListenerTracker.onModified.size)
    }

    @Test
    fun testSetProfilePictureBlobId() {
        val contact = createTestContact()
        assertEquals(null, contact.data.value!!.profilePictureBlobId)
        assertEquals(0, contactListenerTracker.onModified.size)

        // Setting blob ID should update data and notify modification listeners
        contact.setProfilePictureBlobId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            contact.data.value!!.profilePictureBlobId
        )
        assertEquals(1, contactListenerTracker.onModified.size)

        // Setting blob ID again to the same value should not notify listeners
        contact.setProfilePictureBlobId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            contact.data.value!!.profilePictureBlobId
        )
        assertEquals(1, contactListenerTracker.onModified.size)

        // Blob ID can be set to an empty array or to null
        contact.setProfilePictureBlobId(byteArrayOf())
        assertArrayEquals(byteArrayOf(), contact.data.value!!.profilePictureBlobId)
        assertEquals(2, contactListenerTracker.onModified.size)
        contact.setProfilePictureBlobId(null)
        assertNull(contact.data.value!!.profilePictureBlobId)
        assertEquals(3, contactListenerTracker.onModified.size)

        // All listeners should have been notified for our test contact
        assertTrue("Contact listener onModified called for wrong identity") {
            contactListenerTracker.onModified.all { it == contact.identity }
        }
    }
}

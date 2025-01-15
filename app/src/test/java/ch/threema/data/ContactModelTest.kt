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
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ListenerManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.tasks.ReflectContactSyncUpdateTask
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceStore
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.taskmanager.QueueSendCompleteListener
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import java.util.Date
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Track calls to the contact listener.
 */
private class ContactListenerTracker {
    val onNew = mutableListOf<String>()
    val onModified = mutableListOf<String>()
    val onAvatarChanged = mutableListOf<String>()
    val onRemoved = mutableListOf<String>()

    val listener = object : ContactListener {
        override fun onNew(identity: String) {
            onNew.add(identity)
        }

        override fun onModified(identity: String) {
            onModified.add(identity)
        }

        override fun onAvatarChanged(identity: String) {
            onAvatarChanged.add(identity)
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
    private val multiDeviceManagerMock = PowerMockito.mock(MultiDeviceManager::class.java).also {
        `when`(it.isMultiDeviceActive).thenReturn(true)
    }
    private val nonceStoreMock = PowerMockito.mock(NonceStore::class.java)
    private val nonceFactory = NonceFactory(nonceStoreMock)
    private val taskManager = object : TaskManager {
        val scheduledTasks = mutableListOf<Task<*, TaskCodec>>()

        override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
            scheduledTasks.add(task)
            return CompletableDeferred()
        }

        override fun hasPendingTasks(): Boolean = scheduledTasks.isNotEmpty()

        override fun addQueueSendCompleteListener(listener: QueueSendCompleteListener) {
            // Nothing to do
        }

        override fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener) {
            // Nothing to do
        }
    }
    private val coreServiceManagerMock = PowerMockito.mock(CoreServiceManager::class.java).also {
        `when`(it.taskManager).thenReturn(taskManager)
        `when`(it.multiDeviceManager).thenReturn(multiDeviceManagerMock)
        `when`(it.nonceFactory).thenReturn(nonceFactory)
    }
    private val contactModelRepository = ContactModelRepository(
        ModelTypeCache(), databaseBackendMock, coreServiceManagerMock
    )

    private lateinit var contactListenerTracker: ContactListenerTracker

    private fun createTestContact(isRestored: Boolean = false): ContactModel {
        val identity = "TESTTEST"
        return ContactModel(
            identity,
            ContactModelData(
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
                IdentityState.ACTIVE,
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
            ),
            databaseBackendMock,
            contactModelRepository,
            coreServiceManagerMock,
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
            identity,
            ContactModelData(
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
                IdentityState.ACTIVE,
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
            ),
            databaseBackendMock,
            contactModelRepository,
            coreServiceManagerMock,
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
        assertEquals(IdentityState.ACTIVE, value.activityState)
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
    fun testSetNameFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setNameFromLocal("First", "Last") },
            { c -> "First" == c.data.value!!.firstName && "Last" == c.data.value!!.lastName },
            ReflectContactSyncUpdateTask.ReflectNameUpdate::class.java,
        )
    }

    @Test
    fun testSetNameFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setFirstNameFromSync("First") },
            { c -> "First" == c.data.value!!.firstName },
        )
        contactListenerTracker.onModified.clear()
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setLastNameFromSync("Last") },
            { c -> "Last" == c.data.value!!.lastName },
        )
    }

    @Test
    fun testNicknameFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setNicknameFromSync("NewNickname") },
            { c -> "NewNickname" == c.data.value!!.nickname },
        )
    }

    @Test
    fun testVerificationLevelFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setVerificationLevelFromLocal(VerificationLevel.SERVER_VERIFIED) },
            { c -> VerificationLevel.SERVER_VERIFIED == c.data.value!!.verificationLevel },
            ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate::class.java,
        )
    }

    @Test
    fun testVerificationLevelFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setVerificationLevelFromSync(VerificationLevel.SERVER_VERIFIED) },
            { c -> VerificationLevel.SERVER_VERIFIED == c.data.value!!.verificationLevel },
        )
    }

    @Test
    fun testWorkVerificationLevelFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setWorkVerificationLevelFromLocal(WorkVerificationLevel.NONE) },
            { c -> WorkVerificationLevel.NONE == c.data.value!!.workVerificationLevel },
            ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate::class.java,
        )
    }

    @Test
    fun testWorkVerificationLevelFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setWorkVerificationLevelFromSync(WorkVerificationLevel.NONE) },
            { c -> WorkVerificationLevel.NONE == c.data.value!!.workVerificationLevel },
        )
    }

    @Test
    fun testIdentityTypeFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setIdentityTypeFromLocal(IdentityType.WORK) },
            { c -> IdentityType.WORK == c.data.value!!.identityType },
            ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate::class.java,
        )
    }

    @Test
    fun testIdentityTypeFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setIdentityTypeFromSync(IdentityType.WORK) },
            { c -> IdentityType.WORK == c.data.value!!.identityType },
        )
    }

    @Test
    fun testAcquaintanceLevelFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setAcquaintanceLevelFromLocal(AcquaintanceLevel.GROUP) },
            { c -> AcquaintanceLevel.GROUP == c.data.value!!.acquaintanceLevel },
            ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate::class.java,
            shouldTriggerModifyListener = false,
        )
    }

    @Test
    fun testAcquaintanceLevelFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setAcquaintanceLevelFromSync(AcquaintanceLevel.GROUP) },
            { c -> AcquaintanceLevel.GROUP == c.data.value!!.acquaintanceLevel },
            shouldTriggerModifyListener = false,
        )
    }

    @Test
    fun testActivityStateFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setActivityStateFromLocal(IdentityState.INVALID) },
            { c -> IdentityState.INVALID == c.data.value!!.activityState },
            ReflectContactSyncUpdateTask.ReflectActivityStateUpdate::class.java,
        )
    }

    @Test
    fun testActivityStateFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setActivityStateFromSync(IdentityState.INVALID) },
            { c -> IdentityState.INVALID == c.data.value!!.activityState },
        )
    }

    @Test
    fun testFeatureMaskFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setFeatureMaskFromLocal(12) },
            { c -> 12 == c.data.value!!.featureMask.toInt() },
            ReflectContactSyncUpdateTask.ReflectFeatureMaskUpdate::class.java,
        )
    }

    @Test
    fun testFeatureMaskFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setFeatureMaskFromSync(12u) },
            { c -> 12 == c.data.value!!.featureMask.toInt() },
        )
    }

    @Test
    fun testSyncStateFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setSyncStateFromSync(ContactSyncState.CUSTOM) },
            { c -> ContactSyncState.CUSTOM == c.data.value!!.syncState },
            shouldTriggerModifyListener = false,
        )
    }

    @Test
    fun testReadReceiptPolicyFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setReadReceiptPolicyFromLocal(ReadReceiptPolicy.DEFAULT) },
            { c -> ReadReceiptPolicy.DEFAULT == c.data.value!!.readReceiptPolicy },
            ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate::class.java,
        )
    }

    @Test
    fun testReadReceiptPolicyFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setReadReceiptPolicyFromSync(ReadReceiptPolicy.DEFAULT) },
            { c -> ReadReceiptPolicy.DEFAULT == c.data.value!!.readReceiptPolicy },
        )
    }

    @Test
    fun testTypingIndicatorPolicyFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setTypingIndicatorPolicyFromLocal(TypingIndicatorPolicy.DONT_SEND) },
            { c -> TypingIndicatorPolicy.DONT_SEND == c.data.value!!.typingIndicatorPolicy },
            ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate::class.java,
        )
    }

    @Test
    fun testTypingIndicatorPolicyFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setTypingIndicatorPolicyFromSync(TypingIndicatorPolicy.DONT_SEND) },
            { c -> TypingIndicatorPolicy.DONT_SEND == c.data.value!!.typingIndicatorPolicy },
        )
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
            ContactModel(
                identity = "BBBBBBBB",
                data = data,
                databaseBackend = databaseBackendMock,
                contactModelRepository = contactModelRepository,
                coreServiceManager = coreServiceManagerMock
            )
        }
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

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
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

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
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

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
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

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
    }

    private fun assertChangeFromLocal(
        contactModel: ContactModel,
        performChange: (c: ContactModel) -> Unit,
        checkDataChanged: (c: ContactModel) -> Boolean,
        expectedTaskReflectType: Class<*>,
        shouldTriggerModifyListener: Boolean = true,
    ) {
        // Check that the data is not yet updated, listener count is zero, and no task is scheduled
        assertFalse(checkDataChanged(contactModel))
        assertEquals(0, contactListenerTracker.onModified.size)
        assertTrue(taskManager.scheduledTasks.isEmpty())

        // Perform change
        performChange(contactModel)

        // Assert that the data has been updated, the listeners has been fired and a sync task has
        // been created
        assertTrue(checkDataChanged(contactModel))
        if (shouldTriggerModifyListener) {
            assertEquals(1, contactListenerTracker.onModified.size)
        }
        assertEquals(expectedTaskReflectType, taskManager.scheduledTasks.first()::class.java)
        assertEquals(1, taskManager.scheduledTasks.size)
        taskManager.scheduledTasks.clear()

        // Perform the change another time
        performChange(contactModel)

        // Assert that the data is still correct, but no listener should be fired and no sync task
        // should be scheduled
        assertTrue(checkDataChanged(contactModel))
        if (shouldTriggerModifyListener) {
            assertEquals(1, contactListenerTracker.onModified.size)
        }
        assertTrue(taskManager.scheduledTasks.isEmpty())

        // The listeners should have been notified for out test contact
        assertTrue("Contact listener onModified called for wrong identity") {
            contactListenerTracker.onModified.all { it == contactModel.identity }
        }
    }

    private fun assertChangeFromSync(
        contactModel: ContactModel,
        performChange: (c: ContactModel) -> Unit,
        checkDataChanged: (c: ContactModel) -> Boolean,
        shouldTriggerModifyListener: Boolean = true,
    ) {
        // Check that the data is not yet updated, listener count is zero, and no task is scheduled
        assertFalse(checkDataChanged(contactModel))
        assertEquals(0, contactListenerTracker.onModified.size)
        assertTrue(taskManager.scheduledTasks.isEmpty())

        // Perform change
        performChange(contactModel)

        // Assert that the data has been updated, the listeners has been fired and no sync task has
        // been created
        assertTrue(checkDataChanged(contactModel))
        if (shouldTriggerModifyListener) {
            assertEquals(1, contactListenerTracker.onModified.size)
        }
        assertEquals(0, taskManager.scheduledTasks.size)

        // Perform the change another time
        performChange(contactModel)

        // Assert that the data is still correct, but no listener should be fired and still no sync
        // task should be scheduled
        assertTrue(checkDataChanged(contactModel))
        if (shouldTriggerModifyListener) {
            assertEquals(1, contactListenerTracker.onModified.size)
        }
        assertTrue(taskManager.scheduledTasks.isEmpty())

        // The listeners should have been notified for out test contact
        assertTrue("Contact listener onModified called for wrong identity") {
            contactListenerTracker.onModified.all { it == contactModel.identity }
        }
    }
}

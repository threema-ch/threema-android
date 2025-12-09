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

import ch.threema.app.ThreemaApplication
import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ListenerManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.tasks.ReflectContactSyncUpdateTask
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceStore
import ch.threema.common.now
import ch.threema.common.plus
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.IdColor
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
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.time.Instant
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * Track calls to the contact listener.
 */
private class ContactListenerTracker {
    val onNew = mutableListOf<Identity>()
    val onModified = mutableListOf<Identity>()
    val onAvatarChanged = mutableListOf<Identity>()
    val onRemoved = mutableListOf<Identity>()

    val listener = object : ContactListener {
        override fun onNew(identity: Identity) {
            onNew.add(identity)
        }

        override fun onModified(identity: Identity) {
            onModified.add(identity)
        }

        override fun onAvatarChanged(identity: Identity) {
            onAvatarChanged.add(identity)
        }

        override fun onRemoved(identity: Identity) {
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
    private val databaseBackendMock = mockk<DatabaseBackend>(relaxed = true)
    private val multiDeviceManagerMock = mockk<MultiDeviceManager> {
        every { isMultiDeviceActive } returns true
    }
    private val nonceStoreMock = mockk<NonceStore>()
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
    private val coreServiceManagerMock = mockk<CoreServiceManager>().also {
        every { it.taskManager } returns taskManager
        every { it.multiDeviceManager } returns multiDeviceManagerMock
        every { it.nonceFactory } returns nonceFactory
    }
    private val contactModelRepository = ContactModelRepository(
        ModelTypeCache(),
        databaseBackendMock,
        coreServiceManagerMock,
    )

    private lateinit var contactListenerTracker: ContactListenerTracker

    private fun createTestContact(isRestored: Boolean = false): ContactModel {
        val identity = "TESTTEST"
        return ContactModel(
            identity,
            ContactModelData(
                identity = identity,
                publicKey = Random.nextBytes(32),
                createdAt = now(),
                firstName = "Test",
                lastName = "Contact",
                nickname = null,
                idColor = IdColor(13),
                verificationLevel = VerificationLevel.FULLY_VERIFIED,
                workVerificationLevel = WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
                identityType = IdentityType.NORMAL,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
                activityState = IdentityState.ACTIVE,
                syncState = ContactSyncState.INITIAL,
                featureMask = 7uL,
                readReceiptPolicy = ReadReceiptPolicy.DONT_SEND,
                typingIndicatorPolicy = TypingIndicatorPolicy.SEND,
                isArchived = false,
                androidContactLookupInfo = null,
                localAvatarExpires = null,
                isRestored = isRestored,
                profilePictureBlobId = null,
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
            ),
            databaseBackendMock,
            contactModelRepository,
            coreServiceManagerMock,
        )
    }

    @BeforeTest
    fun beforeEach() {
        this.contactListenerTracker = ContactListenerTracker()
        this.contactListenerTracker.subscribe()

        // TODO(ANDR-4219): We have to mock ServiceManager, as it is sneakily referenced somewhere deep down the stack. This needs to be cleaned up.
        mockkObject(ThreemaApplication)
        every { ThreemaApplication.getServiceManager() } returns null
    }

    @AfterTest
    fun afterEach() {
        this.contactListenerTracker.unsubscribe()
        unmockkObject(ThreemaApplication)
    }

    /**
     * Test the construction using the primary constructor.
     *
     * Data is accessed through the `data` state flow.
     */
    @Test
    fun testConstruction() {
        val now = now()
        val publicKey = Random.nextBytes(32)
        val createdAt = now
        val localAvatarExpires = now
        val identity = "TESTTEST"
        val contact = ContactModel(
            identity,
            ContactModelData(
                identity = identity,
                publicKey = publicKey,
                createdAt = createdAt,
                firstName = "Test",
                lastName = "Contact",
                nickname = null,
                idColor = IdColor(13),
                verificationLevel = VerificationLevel.FULLY_VERIFIED,
                workVerificationLevel = WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
                identityType = IdentityType.NORMAL,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
                activityState = IdentityState.ACTIVE,
                syncState = ContactSyncState.INITIAL,
                featureMask = 7uL,
                readReceiptPolicy = ReadReceiptPolicy.SEND,
                typingIndicatorPolicy = TypingIndicatorPolicy.DONT_SEND,
                isArchived = false,
                androidContactLookupInfo = null,
                localAvatarExpires = localAvatarExpires,
                isRestored = true,
                profilePictureBlobId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
            ),
            databaseBackendMock,
            contactModelRepository,
            coreServiceManagerMock,
        )

        val value = contact.data!!
        assertEquals("TESTTEST", value.identity)
        assertEquals(publicKey, value.publicKey)
        assertEquals(createdAt, value.createdAt)
        assertEquals("Test", value.firstName)
        assertEquals("Contact", value.lastName)
        assertNull(value.nickname)
        assertEquals(IdColor(13), value.idColor)
        assertEquals(VerificationLevel.FULLY_VERIFIED, value.verificationLevel)
        assertEquals(WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED, value.workVerificationLevel)
        assertEquals(IdentityType.NORMAL, value.identityType)
        assertEquals(AcquaintanceLevel.DIRECT, value.acquaintanceLevel)
        assertEquals(IdentityState.ACTIVE, value.activityState)
        assertEquals(ContactSyncState.INITIAL, value.syncState)
        assertEquals(7uL, value.featureMask)
        assertEquals(ReadReceiptPolicy.SEND, value.readReceiptPolicy)
        assertEquals(TypingIndicatorPolicy.DONT_SEND, value.typingIndicatorPolicy)
        assertNull(value.androidContactLookupInfo)
        assertEquals(localAvatarExpires, value.localAvatarExpires)
        assertTrue { value.isRestored }
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), value.profilePictureBlobId)
    }

    @Test
    fun testSetNickname() {
        val contact = createTestContact()
        assertEquals(null, contact.data!!.nickname)
        assertEquals(0, contactListenerTracker.onModified.size)

        // Setting nickname should update data and notify modification listeners
        contact.setNicknameFromSync("nicky")
        assertEquals("nicky", contact.data!!.nickname)
        assertEquals(1, contactListenerTracker.onModified.size)

        // Setting nickname again to the same value should not notify listeners
        contact.setNicknameFromSync("nicky")
        assertEquals("nicky", contact.data!!.nickname)
        assertEquals(1, contactListenerTracker.onModified.size)

        // Removing nickname should notify listeners
        contact.setNicknameFromSync(null)
        assertEquals(null, contact.data!!.nickname)
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
            { c -> "First" == c.data!!.firstName && "Last" == c.data!!.lastName },
            ReflectContactSyncUpdateTask.ReflectNameUpdate::class.java,
        )
    }

    @Test
    fun testSetNameFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setFirstNameFromSync("First") },
            { c -> "First" == c.data!!.firstName },
        )
        contactListenerTracker.onModified.clear()
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setLastNameFromSync("Last") },
            { c -> "Last" == c.data!!.lastName },
        )
    }

    @Test
    fun testNicknameFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setNicknameFromSync("NewNickname") },
            { c -> "NewNickname" == c.data!!.nickname },
        )
    }

    @Test
    fun testVerificationLevelFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setVerificationLevelFromLocal(VerificationLevel.SERVER_VERIFIED) },
            { c -> VerificationLevel.SERVER_VERIFIED == c.data!!.verificationLevel },
            ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate::class.java,
        )
    }

    @Test
    fun testVerificationLevelFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setVerificationLevelFromSync(VerificationLevel.SERVER_VERIFIED) },
            { c -> VerificationLevel.SERVER_VERIFIED == c.data!!.verificationLevel },
        )
    }

    @Test
    fun testWorkVerificationLevelFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setWorkVerificationLevelFromLocal(WorkVerificationLevel.NONE) },
            { c -> WorkVerificationLevel.NONE == c.data!!.workVerificationLevel },
            ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate::class.java,
        )
    }

    @Test
    fun testWorkVerificationLevelFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setWorkVerificationLevelFromSync(WorkVerificationLevel.NONE) },
            { c -> WorkVerificationLevel.NONE == c.data!!.workVerificationLevel },
        )
    }

    @Test
    fun testIdentityTypeFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setIdentityTypeFromLocal(IdentityType.WORK) },
            { c -> IdentityType.WORK == c.data!!.identityType },
            ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate::class.java,
        )
    }

    @Test
    fun testIdentityTypeFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setIdentityTypeFromSync(IdentityType.WORK) },
            { c -> IdentityType.WORK == c.data!!.identityType },
        )
    }

    @Test
    fun testAcquaintanceLevelFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setAcquaintanceLevelFromLocal(AcquaintanceLevel.GROUP) },
            { c -> AcquaintanceLevel.GROUP == c.data!!.acquaintanceLevel },
            ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate::class.java,
            shouldTriggerModifyListener = false,
        )
    }

    @Test
    fun testAcquaintanceLevelFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setAcquaintanceLevelFromSync(AcquaintanceLevel.GROUP) },
            { c -> AcquaintanceLevel.GROUP == c.data!!.acquaintanceLevel },
            shouldTriggerModifyListener = false,
        )
    }

    @Test
    fun testActivityStateFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setActivityStateFromLocal(IdentityState.INVALID) },
            { c -> IdentityState.INVALID == c.data!!.activityState },
            ReflectContactSyncUpdateTask.ReflectActivityStateUpdate::class.java,
        )
    }

    @Test
    fun testActivityStateFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setActivityStateFromSync(IdentityState.INVALID) },
            { c -> IdentityState.INVALID == c.data!!.activityState },
        )
    }

    @Test
    fun testFeatureMaskFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setFeatureMaskFromLocal(12) },
            { c -> 12 == c.data!!.featureMask.toInt() },
            ReflectContactSyncUpdateTask.ReflectFeatureMaskUpdate::class.java,
        )
    }

    @Test
    fun testFeatureMaskFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setFeatureMaskFromSync(12u) },
            { c -> 12 == c.data!!.featureMask.toInt() },
        )
    }

    @Test
    fun testSyncStateFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setSyncStateFromSync(ContactSyncState.CUSTOM) },
            { c -> ContactSyncState.CUSTOM == c.data!!.syncState },
            shouldTriggerModifyListener = false,
        )
    }

    @Test
    fun testReadReceiptPolicyFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setReadReceiptPolicyFromLocal(ReadReceiptPolicy.DEFAULT) },
            { c -> ReadReceiptPolicy.DEFAULT == c.data!!.readReceiptPolicy },
            ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate::class.java,
        )
    }

    @Test
    fun testReadReceiptPolicyFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setReadReceiptPolicyFromSync(ReadReceiptPolicy.DEFAULT) },
            { c -> ReadReceiptPolicy.DEFAULT == c.data!!.readReceiptPolicy },
        )
    }

    @Test
    fun testTypingIndicatorPolicyFromLocal() {
        assertChangeFromLocal(
            createTestContact(),
            { c -> c.setTypingIndicatorPolicyFromLocal(TypingIndicatorPolicy.DONT_SEND) },
            { c -> TypingIndicatorPolicy.DONT_SEND == c.data!!.typingIndicatorPolicy },
            ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate::class.java,
        )
    }

    @Test
    fun testTypingIndicatorPolicyFromSync() {
        assertChangeFromSync(
            createTestContact(),
            { c -> c.setTypingIndicatorPolicyFromSync(TypingIndicatorPolicy.DONT_SEND) },
            { c -> TypingIndicatorPolicy.DONT_SEND == c.data!!.typingIndicatorPolicy },
        )
    }

    @Test
    fun testDisplayName() {
        val contact = createTestContact()
        contact.setNicknameFromSync("nicky")

        contact.setNameFromLocal("Test", "Contact")
        assertEquals("Test Contact", contact.data!!.getDisplayName())

        contact.setNameFromLocal("", "Lastname")
        assertEquals("Lastname", contact.data!!.getDisplayName())

        contact.setNameFromLocal("", "")
        assertEquals("~nicky", contact.data!!.getDisplayName())

        contact.setNicknameFromSync(null)
        assertEquals(contact.data!!.identity, contact.data!!.getDisplayName())

        contact.setNicknameFromSync(contact.data!!.identity)
        assertEquals(contact.data!!.identity, contact.data!!.getDisplayName())
    }

    @Test
    fun testConstructorValidateIdentity() {
        val data = createTestContact().data!!.copy(identity = "AAAAAAAA")
        assertFailsWith<AssertionError> {
            ContactModel(
                identity = "BBBBBBBB",
                data = data,
                databaseBackend = databaseBackendMock,
                contactModelRepository = contactModelRepository,
                coreServiceManager = coreServiceManagerMock,
            )
        }
    }

    @Test
    fun testAndroidContactLookupKey() {
        val contact = createTestContact()

        assertEquals(0, contactListenerTracker.onModified.size)

        contact.data!!.let {
            assertNull(it.androidContactLookupInfo)
            assertFalse { it.isLinkedToAndroidContact() }
        }

        val androidContactLookupInfo = AndroidContactLookupInfo(
            lookupKey = "lookMeUp",
            contactId = 42,
        )
        contact.setAndroidContactLookupKey(androidContactLookupInfo)
        contact.data!!.let {
            assertEquals(androidContactLookupInfo, it.androidContactLookupInfo)
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
        assertNull(contact.data!!.androidContactLookupInfo)

        // Set date
        val inOneDay = Instant.now() + 1.days
        contact.setLocalAvatarExpires(inOneDay)
        assertEquals(inOneDay, contact.data!!.localAvatarExpires?.toInstant())
        assertFalse(contact.data?.isAvatarExpired() ?: fail("No data"))

        // Reset to null
        contact.setLocalAvatarExpires(null)
        assertNull(contact.data!!.androidContactLookupInfo)
        assertTrue(contact.data?.isAvatarExpired() ?: fail("No data"))

        // Change listener not called
        assertEquals(0, contactListenerTracker.onModified.size)

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
    }

    @Test
    fun testClearIsRestored() {
        val contact = createTestContact(isRestored = true)

        // Initially true
        assertTrue { contact.data!!.isRestored }

        // Clear
        contact.clearIsRestored()
        assertFalse { contact.data!!.isRestored }

        // Change listener not called
        assertEquals(0, contactListenerTracker.onModified.size)

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
    }

    @Test
    fun testSetProfilePictureBlobId() {
        val contact = createTestContact()
        assertEquals(null, contact.data!!.profilePictureBlobId)
        assertEquals(0, contactListenerTracker.onModified.size)

        // Setting blob ID should update data and notify modification listeners
        contact.setProfilePictureBlobId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            contact.data!!.profilePictureBlobId,
        )
        assertEquals(1, contactListenerTracker.onModified.size)

        // Setting blob ID again to the same value should not notify listeners
        contact.setProfilePictureBlobId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            contact.data!!.profilePictureBlobId,
        )
        assertEquals(1, contactListenerTracker.onModified.size)

        // Blob ID can be set to an empty array or to null
        contact.setProfilePictureBlobId(byteArrayOf())
        assertContentEquals(byteArrayOf(), contact.data!!.profilePictureBlobId)
        assertEquals(2, contactListenerTracker.onModified.size)
        contact.setProfilePictureBlobId(null)
        assertNull(contact.data!!.profilePictureBlobId)
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

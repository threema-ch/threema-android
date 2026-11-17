package ch.threema.data

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.tasks.ReflectContactSyncUpdateTask
import ch.threema.common.emptyByteArray
import ch.threema.common.plus
import ch.threema.common.stateFlowOf
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.taskmanager.LeaderState
import ch.threema.domain.taskmanager.QueueSendCompleteState
import ch.threema.domain.taskmanager.ReflectionQueueDryState
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.Identity
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.utcDate
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
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
import kotlinx.coroutines.flow.StateFlow
import testdata.TestData

class ContactModelTest {
    private val databaseBackendMock = mockk<DatabaseBackend>(relaxed = true)
    private val multiDeviceManagerMock = mockk<MultiDeviceManager> {
        every { isMultiDeviceActive } returns true
    }
    private val taskManager = object : TaskManager {
        val scheduledTasks = mutableListOf<Task<*, TaskCodec>>()

        override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
            scheduledTasks.add(task)
            return CompletableDeferred()
        }

        override fun hasPendingTasks(): Boolean = scheduledTasks.isNotEmpty()

        override fun watchQueueSendComplete(): StateFlow<QueueSendCompleteState> = stateFlowOf(QueueSendCompleteState.PENDING)

        override fun watchReflectionQueueDry(): StateFlow<ReflectionQueueDryState> = stateFlowOf(ReflectionQueueDryState.PENDING)

        override fun watchLeaderState(): StateFlow<LeaderState> = stateFlowOf(LeaderState.NOT_LEADER)
    }
    private val identityProvider = TestIdentityProvider(TestData.Identities.ME)

    /**
     * Test the construction using the primary constructor.
     *
     * Data is accessed through the `data` state flow.
     */
    @Test
    fun testConstruction() {
        val now = utcDate(2025, 10, 25, 15, 30, 10)
        val contact = ContactModel(
            identity = TestData.Identities.OTHER_1.value,
            data = ContactModelData(
                identity = TestData.Identities.OTHER_1.value,
                publicKey = TestData.testPublicKey,
                createdAt = now,
                lastUpdateAt = null,
                firstName = "Firstname",
                lastName = "Lastname",
                nickname = "Nick",
                idColor = IdColor(13),
                verificationLevel = VerificationLevel.FULLY_VERIFIED,
                workVerificationLevel = WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
                identityType = IdentityType.REGULAR,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
                activityState = IdentityState.ACTIVE,
                syncState = ContactSyncState.INITIAL,
                featureMask = 7uL,
                readReceiptPolicy = ReadReceiptPolicy.SEND,
                typingIndicatorPolicy = TypingIndicatorPolicy.DONT_SEND,
                conversationVisibility = ConversationVisibility.NORMAL,
                conversationCategory = ConversationCategory.DEFAULT,
                androidContactLookupInfo = null,
                localAvatarExpires = now,
                isRestored = true,
                profilePictureBlobId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
                availabilityStatus = AvailabilityStatus.None,
                workLastFullSyncAt = null,
            ),
            databaseBackend = databaseBackendMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManager,
            identityProvider = identityProvider,
            globalEventBuses = mockk(relaxed = true),
        )

        val contactModelData = contact.data!!
        assertEquals(TestData.Identities.OTHER_1.value, contactModelData.identity)
        assertEquals(TestData.testPublicKey, contactModelData.publicKey)
        assertEquals(now, contactModelData.createdAt)
        assertEquals("Firstname", contactModelData.firstName)
        assertEquals("Lastname", contactModelData.lastName)
        assertEquals("Nick", contactModelData.nickname)
        assertEquals(IdColor(13), contactModelData.idColor)
        assertEquals(VerificationLevel.FULLY_VERIFIED, contactModelData.verificationLevel)
        assertEquals(WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED, contactModelData.workVerificationLevel)
        assertEquals(IdentityType.REGULAR, contactModelData.identityType)
        assertEquals(AcquaintanceLevel.DIRECT, contactModelData.acquaintanceLevel)
        assertEquals(IdentityState.ACTIVE, contactModelData.activityState)
        assertEquals(ContactSyncState.INITIAL, contactModelData.syncState)
        assertEquals(7uL, contactModelData.featureMask)
        assertEquals(ReadReceiptPolicy.SEND, contactModelData.readReceiptPolicy)
        assertEquals(TypingIndicatorPolicy.DONT_SEND, contactModelData.typingIndicatorPolicy)
        assertNull(contactModelData.androidContactLookupInfo)
        assertEquals(now, contactModelData.localAvatarExpires)
        assertTrue(contactModelData.isRestored)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), contactModelData.profilePictureBlobId)
        assertEquals(ConversationVisibility.NORMAL, contactModelData.conversationVisibility)
    }

    @Test
    fun testSetNickname() {
        val contact = TestData.createContactModel(
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManager,
        )
        assertEquals(null, contact.data!!.nickname)

        // Setting nickname should update data
        contact.setNicknameFromSync("nicky")
        assertEquals("nicky", contact.data!!.nickname)

        // Removing nickname should notify listeners
        contact.setNicknameFromSync(null)
        assertEquals(null, contact.data!!.nickname)
    }

    @Test
    fun testSetAvailabilityStatusFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel ->
                contactModel.setAvailabilityStatusFromSync(
                    AvailabilityStatus.Unavailable(
                        description = "On vacation",
                    ),
                )
            },
            checkDataChanged = { contactModel ->
                AvailabilityStatus.Unavailable(
                    description = "On vacation",
                ) == contactModel.data!!.availabilityStatus
            },
        )
    }

    @Test
    fun testSetNameFromLocal() {
        assertChangeFromLocal(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setNameFromLocal("First", "Last") },
            checkDataChanged = { contactModel -> "First" == contactModel.data!!.firstName && "Last" == contactModel.data!!.lastName },
            expectedTaskReflectType = ReflectContactSyncUpdateTask.ReflectNameUpdate::class.java,
        )
    }

    @Test
    fun testSetNameFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setFirstNameFromSync("First") },
            checkDataChanged = { contactModel -> "First" == contactModel.data!!.firstName },
        )

        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setLastNameFromSync("Last") },
            checkDataChanged = { contactModel -> "Last" == contactModel.data!!.lastName },
        )
    }

    @Test
    fun testNicknameFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(identity = Identity("TESTTEST")),
            performChange = { contactModel -> contactModel.setNicknameFromSync("NewNickname") },
            checkDataChanged = { contactModel -> "NewNickname" == contactModel.data!!.nickname },
        )
    }

    @Test
    fun testVerificationLevelFromLocal() {
        assertChangeFromLocal(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setVerificationLevelFromLocal(VerificationLevel.SERVER_VERIFIED) },
            checkDataChanged = { contactModel -> VerificationLevel.SERVER_VERIFIED == contactModel.data!!.verificationLevel },
            expectedTaskReflectType = ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate::class.java,
        )
    }

    @Test
    fun testVerificationLevelFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setVerificationLevelFromSync(VerificationLevel.SERVER_VERIFIED) },
            checkDataChanged = { contactModel -> VerificationLevel.SERVER_VERIFIED == contactModel.data!!.verificationLevel },
        )
    }

    @Test
    fun testWorkVerificationLevelFromLocal() {
        assertChangeFromLocal(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setWorkVerificationLevelFromLocal(WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED) },
            checkDataChanged = { contactModel -> WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED == contactModel.data!!.workVerificationLevel },
            expectedTaskReflectType = ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate::class.java,
        )
    }

    @Test
    fun testWorkVerificationLevelFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setWorkVerificationLevelFromSync(WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED) },
            checkDataChanged = { contactModel -> WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED == contactModel.data!!.workVerificationLevel },
        )
    }

    @Test
    fun testIdentityTypeFromLocal() {
        assertChangeFromLocal(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setIdentityTypeFromLocal(IdentityType.WORK) },
            checkDataChanged = { contactModel -> IdentityType.WORK == contactModel.data!!.identityType },
            expectedTaskReflectType = ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate::class.java,
        )
    }

    @Test
    fun testIdentityTypeFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setIdentityTypeFromSync(IdentityType.WORK) },
            checkDataChanged = { contactModel -> IdentityType.WORK == contactModel.data!!.identityType },
        )
    }

    @Test
    fun testAcquaintanceLevelFromLocal() {
        assertChangeFromLocal(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setAcquaintanceLevelFromLocal(AcquaintanceLevel.GROUP_OR_DELETED) },
            checkDataChanged = { contactModel -> AcquaintanceLevel.GROUP_OR_DELETED == contactModel.data!!.acquaintanceLevel },
            expectedTaskReflectType = ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate::class.java,
        )
    }

    @Test
    fun testAcquaintanceLevelFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setAcquaintanceLevelFromSync(AcquaintanceLevel.GROUP_OR_DELETED) },
            checkDataChanged = { contactModel -> AcquaintanceLevel.GROUP_OR_DELETED == contactModel.data!!.acquaintanceLevel },
        )
    }

    @Test
    fun testActivityStateFromLocal() {
        assertChangeFromLocal(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setActivityStateFromLocal(IdentityState.INVALID) },
            checkDataChanged = { contactModel -> IdentityState.INVALID == contactModel.data!!.activityState },
            expectedTaskReflectType = ReflectContactSyncUpdateTask.ReflectActivityStateUpdate::class.java,
        )
    }

    @Test
    fun testActivityStateFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setActivityStateFromSync(IdentityState.INVALID) },
            checkDataChanged = { contactModel -> IdentityState.INVALID == contactModel.data!!.activityState },
        )
    }

    @Test
    fun testFeatureMaskFromLocal() {
        assertChangeFromLocal(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setFeatureMaskFromLocal(12) },
            checkDataChanged = { contactModel -> 12 == contactModel.data!!.featureMask.toInt() },
            expectedTaskReflectType = ReflectContactSyncUpdateTask.ReflectFeatureMaskUpdate::class.java,
        )
    }

    @Test
    fun testFeatureMaskFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setFeatureMaskFromSync(12u) },
            checkDataChanged = { contactModel -> 12 == contactModel.data!!.featureMask.toInt() },
        )
    }

    @Test
    fun testSyncStateFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setSyncStateFromSync(ContactSyncState.CUSTOM) },
            checkDataChanged = { contactModel -> ContactSyncState.CUSTOM == contactModel.data!!.syncState },
        )
    }

    @Test
    fun testReadReceiptPolicyFromLocal() {
        assertChangeFromLocal(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setReadReceiptPolicyFromLocal(ReadReceiptPolicy.DONT_SEND) },
            checkDataChanged = { contactModel -> ReadReceiptPolicy.DONT_SEND == contactModel.data!!.readReceiptPolicy },
            expectedTaskReflectType = ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate::class.java,
        )
    }

    @Test
    fun testReadReceiptPolicyFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setReadReceiptPolicyFromSync(ReadReceiptPolicy.DONT_SEND) },
            checkDataChanged = { contactModel -> ReadReceiptPolicy.DONT_SEND == contactModel.data!!.readReceiptPolicy },
        )
    }

    @Test
    fun testTypingIndicatorPolicyFromLocal() {
        assertChangeFromLocal(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setTypingIndicatorPolicyFromLocal(TypingIndicatorPolicy.DONT_SEND) },
            checkDataChanged = { contactModel -> TypingIndicatorPolicy.DONT_SEND == contactModel.data!!.typingIndicatorPolicy },
            expectedTaskReflectType = ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate::class.java,
        )
    }

    @Test
    fun testTypingIndicatorPolicyFromSync() {
        assertChangeFromSync(
            contactModel = TestData.createContactModel(
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
            ),
            performChange = { contactModel -> contactModel.setTypingIndicatorPolicyFromSync(TypingIndicatorPolicy.DONT_SEND) },
            checkDataChanged = { contactModel -> TypingIndicatorPolicy.DONT_SEND == contactModel.data!!.typingIndicatorPolicy },
        )
    }

    @Test
    fun testConstructorValidateIdentity() {
        val data = TestData.createContactModel(
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManager,
        ).data!!.copy(identity = "AAAAAAAA")
        assertFailsWith<IllegalArgumentException> {
            ContactModel(
                identity = "BBBBBBBB",
                data = data,
                databaseBackend = databaseBackendMock,
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
                identityProvider = identityProvider,
                globalEventBuses = mockk(relaxed = true),
            )
        }
    }

    @Test
    fun testAndroidContactLookupKey() {
        val contact = TestData.createContactModel(
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManager,
        )

        contact.data!!.let {
            assertNull(it.androidContactLookupInfo)
            assertFalse(it.isLinkedToAndroidContact())
        }

        val androidContactLookupInfo = AndroidContactLookupInfo(
            lookupKey = "lookMeUp",
            contactId = 42,
        )
        contact.setAndroidContactLookupKey(androidContactLookupInfo)
        contact.data!!.let {
            assertEquals(androidContactLookupInfo, it.androidContactLookupInfo)
            assertTrue(it.isLinkedToAndroidContact())
        }

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
    }

    @Test
    fun testLocalAvatarExpires() {
        val contact = TestData.createContactModel(
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManager,
        )

        // Initially null
        assertNull(contact.data!!.androidContactLookupInfo)

        // Set date
        val inOneDay = Instant.now() + 1.days
        contact.setLocalAvatarExpires(inOneDay)
        assertEquals(inOneDay, contact.data!!.localAvatarExpires)
        assertFalse(contact.data?.isAvatarExpired() ?: fail("No data"))

        // Reset to null
        contact.setLocalAvatarExpires(null)
        assertNull(contact.data!!.androidContactLookupInfo)
        assertTrue(contact.data?.isAvatarExpired() ?: fail("No data"))

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
    }

    @Test
    fun testClearIsRestored() {
        val contact = TestData.createContactModel(
            isRestored = true,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManager,
        )

        // Initially true
        assertTrue(contact.data!!.isRestored)

        // Clear
        contact.clearIsRestored()
        assertFalse(contact.data!!.isRestored)

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
    }

    @Test
    fun testSetProfilePictureBlobId() {
        val contact = TestData.createContactModel(
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManager,
        )
        assertEquals(null, contact.data!!.profilePictureBlobId)

        // Setting blob ID should update data and notify modification listeners
        contact.setProfilePictureBlobId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            contact.data!!.profilePictureBlobId,
        )

        // Setting blob ID again to the same value should not notify listeners
        contact.setProfilePictureBlobId(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            contact.data!!.profilePictureBlobId,
        )

        // Blob ID can be set to an empty array or to null
        contact.setProfilePictureBlobId(emptyByteArray())
        assertContentEquals(emptyByteArray(), contact.data!!.profilePictureBlobId)
        contact.setProfilePictureBlobId(null)
        assertNull(contact.data!!.profilePictureBlobId)

        // Assert that no tasks have been scheduled
        assertTrue(taskManager.scheduledTasks.isEmpty())
    }

    private fun assertChangeFromLocal(
        contactModel: ContactModel,
        performChange: (contactModel: ContactModel) -> Unit,
        checkDataChanged: (contactModel: ContactModel) -> Boolean,
        expectedTaskReflectType: Class<*>,
    ) {
        // Check that the data is not yet updated, listener count is zero, and no task is scheduled
        assertFalse(checkDataChanged(contactModel))
        assertTrue(taskManager.scheduledTasks.isEmpty())

        // Perform change
        performChange(contactModel)

        // Assert that the data has been updated, the listeners has been fired and a sync task has
        // been created
        assertTrue(checkDataChanged(contactModel))
        assertEquals(expectedTaskReflectType, taskManager.scheduledTasks.first()::class.java)
        assertEquals(1, taskManager.scheduledTasks.size)
        taskManager.scheduledTasks.clear()

        // Perform the change another time
        performChange(contactModel)

        // Assert that the data is still correct, but no listener should be fired and no sync task
        // should be scheduled
        assertTrue(checkDataChanged(contactModel))
        assertTrue(taskManager.scheduledTasks.isEmpty())
    }

    private fun assertChangeFromSync(
        contactModel: ContactModel,
        performChange: (contactModel: ContactModel) -> Unit,
        checkDataChanged: (contactModel: ContactModel) -> Boolean,
    ) {
        // Check that the data is not yet updated, listener count is zero, and no task is scheduled
        assertFalse(checkDataChanged(contactModel))
        assertTrue(taskManager.scheduledTasks.isEmpty())

        // Perform change
        performChange(contactModel)

        // Assert that the data has been updated, the listeners has been fired and no sync task has
        // been created
        assertTrue(checkDataChanged(contactModel))
        assertEquals(0, taskManager.scheduledTasks.size)

        // Perform the change another time
        performChange(contactModel)

        // Assert that the data is still correct, but no listener should be fired and still no sync
        // task should be scheduled
        assertTrue(checkDataChanged(contactModel))
        assertTrue(taskManager.scheduledTasks.isEmpty())
    }
}

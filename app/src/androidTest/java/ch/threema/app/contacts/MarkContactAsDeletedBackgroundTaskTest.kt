package ch.threema.app.contacts

import androidx.annotation.WorkerThread
import ch.threema.app.DangerousTest
import ch.threema.app.TestMultiDevicePropertyProvider
import ch.threema.app.TestNonceStore
import ch.threema.app.asynctasks.AndroidContactLinkPolicy
import ch.threema.app.asynctasks.ContactSyncPolicy
import ch.threema.app.asynctasks.DeleteContactServices
import ch.threema.app.asynctasks.MarkContactAsDeletedBackgroundTask
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.multidevice.PersistedMultiDeviceProperties
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.ReflectContactSyncUpdateTask
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.base.crypto.NonceFactory
import ch.threema.common.DispatcherProvider
import ch.threema.common.stateFlowOf
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.ModelRepositories
import ch.threema.domain.helpers.TransactionAckTaskCodec
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.InboundD2mMessage.DevicesInfo
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.LeaderState
import ch.threema.domain.taskmanager.QueueSendCompleteState
import ch.threema.domain.taskmanager.ReflectionQueueDryState
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.Identity
import ch.threema.storage.factories.ContactModelFactory
import ch.threema.test.TestData.PUBLIC_KEY
import ch.threema.test.TestDatabaseProvider
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.MUST_NOT_BE_CALLED
import ch.threema.testhelpers.TestTimeProvider
import io.mockk.mockk
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

@DangerousTest
class MarkContactAsDeletedBackgroundTaskTest {
    private val backgroundExecutor = BackgroundExecutor()
    private lateinit var testTaskCodec: TransactionAckTaskCodec
    private val testTaskManager = object : TaskManager {
        val taskQueue: MutableList<Task<*, TaskCodec>> = mutableListOf()

        override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
            taskQueue.add(task)
            return CompletableDeferred()
        }

        override fun hasPendingTasks(): Boolean {
            return taskQueue.isNotEmpty()
        }

        override fun watchQueueSendComplete(): StateFlow<QueueSendCompleteState> = stateFlowOf(QueueSendCompleteState.PENDING)

        override fun watchReflectionQueueDry(): StateFlow<ReflectionQueueDryState> = stateFlowOf(ReflectionQueueDryState.PENDING)

        override fun watchLeaderState(): StateFlow<LeaderState> = stateFlowOf(LeaderState.NOT_LEADER)
    }
    private lateinit var databaseProvider: TestDatabaseProvider
    private val multiDeviceManager = object : MultiDeviceManager {
        var multiDeviceEnabled = false

        override val isMdDisabledOrSupportsFs: Boolean
            get() = !multiDeviceEnabled
        override val isMultiDeviceActive: Boolean
            get() = multiDeviceEnabled
        override val propertiesProvider = TestMultiDevicePropertyProvider
        override val socketCloseListener: D2mSocketCloseListener = D2mSocketCloseListener { }

        @WorkerThread
        override fun removeMultiDeviceLocally(serviceManager: ServiceManager) {
            MUST_NOT_BE_CALLED()
        }

        override suspend fun setDeviceLabel(deviceLabel: String) {
            MUST_NOT_BE_CALLED()
        }

        override suspend fun linkDevice(
            serviceManager: ServiceManager,
            deviceJoinOfferUri: String,
            taskCreator: TaskCreator,
        ): Flow<DeviceLinkingStatus> {
            MUST_NOT_BE_CALLED()
        }

        override suspend fun loadLinkedDevices(taskCreator: TaskCreator): Result<Map<DeviceId, DevicesInfo.AugmentedDeviceInfo>> {
            MUST_NOT_BE_CALLED()
        }

        override suspend fun setProperties(persistedProperties: PersistedMultiDeviceProperties?) {
            MUST_NOT_BE_CALLED()
        }

        override fun reconnect() {
            MUST_NOT_BE_CALLED()
        }

        override suspend fun disableForwardSecurity(
            handle: ActiveTaskCodec,
            contactService: ContactService,
            userService: UserService,
            fsMessageProcessor: ForwardSecurityMessageProcessor,
            taskCreator: TaskCreator,
        ) {
            MUST_NOT_BE_CALLED()
        }

        override fun enableForwardSecurity(serviceManager: ServiceManager) {
            MUST_NOT_BE_CALLED()
        }
    }

    private lateinit var contactModelRepository: ContactModelRepository
    private lateinit var deleteContactServices: DeleteContactServices
    private val testContactModelData = ContactModelData(
        identity = "12345678",
        publicKey = PUBLIC_KEY,
        createdAt = Instant.now(),
        lastUpdateAt = null,
        firstName = "1234",
        lastName = "5678",
        nickname = null,
        verificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        identityType = IdentityType.REGULAR,
        acquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState = IdentityState.ACTIVE,
        syncState = ContactSyncState.INITIAL,
        featureMask = 0u,
        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        conversationVisibility = ConversationVisibility.NORMAL,
        conversationCategory = ConversationCategory.DEFAULT,
        androidContactLookupInfo = null,
        localAvatarExpires = null,
        isRestored = false,
        profilePictureBlobId = null,
        jobTitle = null,
        department = null,
        notificationTriggerPolicyOverride = null,
        availabilityStatus = AvailabilityStatus.None,
        workLastFullSyncAt = null,
    )

    @BeforeTest
    fun before() {
        databaseProvider = TestDatabaseProvider()
        val serviceManager = ServiceManager.require()
        testTaskCodec = TransactionAckTaskCodec()
        val myIdentity = "00000000"
        val identityProviderMock = TestIdentityProvider(Identity(myIdentity))
        deleteContactServices = DeleteContactServices(
            serviceManager.userService,
            serviceManager.contactService,
            serviceManager.conversationService,
            serviceManager.ringtoneService,
            serviceManager.profilePicRecipientsService,
            serviceManager.wallpaperService,
            serviceManager.fileService,
            serviceManager.excludedSyncIdentitiesService,
            serviceManager.dhSessionStore,
            serviceManager.notificationService,
            ContactModelFactory(databaseProvider),
        )
        contactModelRepository = ModelRepositories(
            databaseProvider = databaseProvider,
            identityProvider = identityProviderMock,
            multiDeviceManager = multiDeviceManager,
            taskManager = testTaskManager,
            nonceFactory = NonceFactory(TestNonceStore()),
            globalEventBuses = mockk(relaxed = true),
            globalEventFlows = mockk(relaxed = true),
            dispatcherProvider = DispatcherProvider.default,
            timeProvider = TestTimeProvider(),
        ).contacts

        contactModelRepository.persistNewContact(testContactModelData)
    }

    @Test
    fun testAcquaintanceLevelChange() = runTest {
        val contactModel = contactModelRepository.getByIdentity(testContactModelData.identity)
        // Assert that the contact exists as "direct" contact
        assertNotNull(contactModel)
        assertEquals(AcquaintanceLevel.DIRECT, contactModel.data?.acquaintanceLevel)

        // Remove the contact
        backgroundExecutor.executeDeferred(
            MarkContactAsDeletedBackgroundTask(
                setOf(testContactModelData.identity),
                contactModelRepository,
                deleteContactServices,
                ContactSyncPolicy.INCLUDE,
                AndroidContactLinkPolicy.REMOVE_LINK,
            ),
        ).await()

        // Assert that the contact's acquaintance level is "group" now
        assertEquals(AcquaintanceLevel.GROUP_OR_DELETED, contactModel.data?.acquaintanceLevel)
    }

    @Test
    fun testNoReflection() = runTest {
        // Disable multi device
        multiDeviceManager.multiDeviceEnabled = false

        val contactModel = contactModelRepository.getByIdentity(testContactModelData.identity)
        // Assert that the contact exists as "direct" contact
        assertNotNull(contactModel)
        assertEquals(AcquaintanceLevel.DIRECT, contactModel.data?.acquaintanceLevel)

        // Remove the contact
        backgroundExecutor.executeDeferred(
            MarkContactAsDeletedBackgroundTask(
                setOf(testContactModelData.identity),
                contactModelRepository,
                deleteContactServices,
                ContactSyncPolicy.INCLUDE,
                AndroidContactLinkPolicy.REMOVE_LINK,
            ),
        ).await()

        // Assert that the there was no task scheduled
        assertTrue(testTaskManager.taskQueue.isEmpty())
    }

    @Test
    fun testReflection() = runTest {
        // Enable multi device
        multiDeviceManager.multiDeviceEnabled = true

        val contactModel = contactModelRepository.getByIdentity(testContactModelData.identity)
        // Assert that the contact exists as "direct" contact
        assertNotNull(contactModel)
        assertEquals(AcquaintanceLevel.DIRECT, contactModel.data?.acquaintanceLevel)

        // Mark the contact as deleted
        backgroundExecutor.executeDeferred(
            MarkContactAsDeletedBackgroundTask(
                setOf(testContactModelData.identity),
                contactModelRepository,
                deleteContactServices,
                ContactSyncPolicy.INCLUDE,
                AndroidContactLinkPolicy.REMOVE_LINK,
            ),
        ).await()

        // Assert that a reflection task has been scheduled
        val task = testTaskManager.taskQueue.removeFirstOrNull()
        assertIs<ReflectContactSyncUpdateTask>(task)

        // Assert that no other tasks have been scheduled
        assertTrue(testTaskManager.taskQueue.isEmpty())
    }
}

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

package ch.threema.app.contacts

import androidx.annotation.WorkerThread
import ch.threema.app.DangerousTest
import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestMultiDevicePropertyProvider
import ch.threema.app.ThreemaApplication
import ch.threema.app.asynctasks.AndroidContactLinkPolicy
import ch.threema.app.asynctasks.ContactSyncPolicy
import ch.threema.app.asynctasks.DeleteContactServices
import ch.threema.app.asynctasks.MarkContactAsDeletedBackgroundTask
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.multidevice.PersistedMultiDeviceProperties
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.ReflectContactSyncUpdateTask
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.utils.AppVersionProvider
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.base.crypto.NaCl
import ch.threema.data.TestDatabaseService
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.ModelRepositories
import ch.threema.domain.helpers.TransactionAckTaskCodec
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
import ch.threema.domain.taskmanager.QueueSendCompleteListener
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.testhelpers.MUST_NOT_BE_CALLED
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
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

        override fun addQueueSendCompleteListener(listener: QueueSendCompleteListener) {
            // Nothing to do
        }

        override fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener) {
            // Nothing to do
        }
    }
    private lateinit var databaseService: TestDatabaseService
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

    private lateinit var coreServiceManager: CoreServiceManager
    private lateinit var contactModelRepository: ContactModelRepository
    private lateinit var deleteContactServices: DeleteContactServices
    private val testContactModelData = ContactModelData(
        identity = "12345678",
        publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES),
        createdAt = Date(),
        firstName = "1234",
        lastName = "5678",
        nickname = null,
        verificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        identityType = IdentityType.NORMAL,
        acquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState = IdentityState.ACTIVE,
        syncState = ContactSyncState.INITIAL,
        featureMask = 0u,
        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        isArchived = false,
        androidContactLookupKey = null,
        localAvatarExpires = null,
        isRestored = false,
        profilePictureBlobId = null,
        jobTitle = null,
        department = null,
        notificationTriggerPolicyOverride = null,
    )

    @BeforeTest
    fun before() {
        databaseService = TestDatabaseService()
        val serviceManager = ThreemaApplication.requireServiceManager()
        testTaskCodec = TransactionAckTaskCodec()
        coreServiceManager = TestCoreServiceManager(
            version = AppVersionProvider.appVersion,
            databaseService = databaseService,
            preferenceStore = serviceManager.preferenceStore,
            encryptedPreferenceStore = serviceManager.encryptedPreferenceStore,
            multiDeviceManager = multiDeviceManager,
            taskManager = testTaskManager,
        )
        deleteContactServices = DeleteContactServices(
            serviceManager.userService,
            serviceManager.contactService,
            serviceManager.conversationService,
            serviceManager.ringtoneService,
            serviceManager.conversationCategoryService,
            serviceManager.profilePicRecipientsService,
            serviceManager.wallpaperService,
            serviceManager.fileService,
            serviceManager.excludedSyncIdentitiesService,
            serviceManager.dhSessionStore,
            serviceManager.notificationService,
            serviceManager.databaseService,
        )
        contactModelRepository = ModelRepositories(coreServiceManager).contacts

        // Add a contact "from sync". This has no side effects and does not reflect the contact.
        contactModelRepository.createFromSync(testContactModelData)
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
        assertEquals(AcquaintanceLevel.GROUP, contactModel.data?.acquaintanceLevel)
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

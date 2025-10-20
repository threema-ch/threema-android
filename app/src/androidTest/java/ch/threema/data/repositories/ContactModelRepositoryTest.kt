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

package ch.threema.data.repositories

import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.utils.AppVersionProvider
import ch.threema.base.crypto.NaCl
import ch.threema.data.TestDatabaseService
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModelData
import ch.threema.domain.helpers.TransactionAckTaskCodec
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.testhelpers.nonSecureRandomArray
import ch.threema.testhelpers.randomIdentity
import java.util.Date
import junit.framework.TestCase.assertNotNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(value = Parameterized::class)
class ContactModelRepositoryTest(private val contactModelData: ContactModelData) {
    // Services where MD is disabled
    private lateinit var databaseService: TestDatabaseService
    private lateinit var coreServiceManager: TestCoreServiceManager
    private lateinit var contactModelRepository: ContactModelRepository

    // Services where MD is enabled
    private lateinit var databaseServiceMd: TestDatabaseService
    private lateinit var taskCodecMd: TransactionAckTaskCodec
    private lateinit var coreServiceManagerMd: TestCoreServiceManager
    private lateinit var contactModelRepositoryMd: ContactModelRepository

    private enum class TestTriggerSource {
        FROM_LOCAL,
        FROM_REMOTE,
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun initialValuesSet() = setOf(
            getInitialContactModelData(),
            getInitialContactModelData(publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES) { it.toByte() }),
            getInitialContactModelData(createdAt = Date(42)),
            getInitialContactModelData(identityType = IdentityType.WORK),
            getInitialContactModelData(acquaintanceLevel = AcquaintanceLevel.GROUP),
            getInitialContactModelData(activityState = IdentityState.INACTIVE),
            getInitialContactModelData(featureMask = 64.toULong()),
        )

        private fun getInitialContactModelData(
            identity: Identity = "ABCDEFGH",
            publicKey: ByteArray = ByteArray(NaCl.PUBLIC_KEY_BYTES),
            createdAt: Date = Date(),
            firstName: String = "",
            lastName: String = "",
            nickname: String? = null,
            verificationLevel: VerificationLevel = VerificationLevel.UNVERIFIED,
            workVerificationLevel: WorkVerificationLevel = WorkVerificationLevel.NONE,
            identityType: IdentityType = IdentityType.NORMAL,
            acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
            activityState: IdentityState = IdentityState.ACTIVE,
            syncState: ContactSyncState = ContactSyncState.INITIAL,
            featureMask: ULong = 0u,
            readReceiptPolicy: ReadReceiptPolicy = ReadReceiptPolicy.DEFAULT,
            typingIndicatorPolicy: TypingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
            androidContactLookupKey: String? = null,
            localAvatarExpires: Date? = null,
            isRestored: Boolean = false,
            profilePictureBlobId: ByteArray? = null,
            jobTitle: String? = null,
            department: String? = null,
        ) = ContactModelData(
            identity = identity,
            publicKey = publicKey,
            createdAt = createdAt,
            firstName = firstName,
            lastName = lastName,
            nickname = nickname,
            idColor = IdColor.ofIdentity(identity),
            verificationLevel = verificationLevel,
            workVerificationLevel = workVerificationLevel,
            identityType = identityType,
            acquaintanceLevel = acquaintanceLevel,
            activityState = activityState,
            syncState = syncState,
            featureMask = featureMask,
            readReceiptPolicy = readReceiptPolicy,
            typingIndicatorPolicy = typingIndicatorPolicy,
            isArchived = false,
            androidContactLookupKey = androidContactLookupKey,
            localAvatarExpires = localAvatarExpires,
            isRestored = isRestored,
            profilePictureBlobId = profilePictureBlobId,
            jobTitle = jobTitle,
            department = department,
            notificationTriggerPolicyOverride = null,
        )
    }

    @BeforeTest
    fun before() {
        val serviceManager = ThreemaApplication.requireServiceManager()
        TestHelpers.setIdentity(
            serviceManager,
            TestHelpers.TEST_CONTACT,
        )

        // Instantiate services where MD is disabled
        this.databaseService = TestDatabaseService()
        this.coreServiceManager = TestCoreServiceManager(
            version = AppVersionProvider.appVersion,
            databaseService = databaseService,
            preferenceStore = serviceManager.preferenceStore,
            encryptedPreferenceStore = serviceManager.encryptedPreferenceStore,
            taskManager = TestTaskManager(UnusedTaskCodec()),
        )
        this.contactModelRepository = ModelRepositories(coreServiceManager).contacts

        // Instantiate services where MD is enabled
        this.databaseServiceMd = TestDatabaseService()
        this.taskCodecMd = TransactionAckTaskCodec()
        this.coreServiceManagerMd = TestCoreServiceManager(
            version = AppVersionProvider.appVersion,
            databaseService = databaseServiceMd,
            preferenceStore = serviceManager.preferenceStore,
            encryptedPreferenceStore = serviceManager.encryptedPreferenceStore,
            multiDeviceManager = TestMultiDeviceManager(
                isMultiDeviceActive = true,
                isMdDisabledOrSupportsFs = false,
            ),
            taskManager = TestTaskManager(taskCodecMd),
        )
        this.contactModelRepositoryMd = ModelRepositories(coreServiceManagerMd).contacts
    }

    /**
     * Test creation of a new contact from local.
     */
    @Test
    fun createFromLocal() {
        testCreateFromLocalOrRemote(contactModelData, TestTriggerSource.FROM_LOCAL)
    }

    /**
     * Test creation of a new contact from remote.
     */
    @Test
    fun createFromRemote() {
        testCreateFromLocalOrRemote(contactModelData, TestTriggerSource.FROM_REMOTE)
    }

    /**
     * Test creation of a new contact from sync.
     */
    @Test
    fun createFromSync() {
        testCreateFromSync(contactModelData)
    }

    /**
     * Test creation of a new contact from local twice. The first time a new contact should be
     * created. The second time a [ContactStoreException] should be thrown.
     */
    @Test
    fun createFromLocalTwice() {
        testCreateFromLocalOrRemoteTwice(contactModelData, TestTriggerSource.FROM_LOCAL)
    }

    /**
     * Test creation of a new contact from remote twice. The first time a new contact should be
     * created. The second time a [ContactStoreException] should be thrown.
     */
    @Test
    fun createFromRemoteTwice() {
        testCreateFromLocalOrRemoteTwice(contactModelData, TestTriggerSource.FROM_REMOTE)
    }

    /**
     * Test creation of a new contact from sync twice. The first time a new contact should be
     * created. The second time a [ContactStoreException] should be thrown.
     */
    @Test
    fun createFromSyncTwice() {
        testCreateFromSyncTwice(contactModelData)
    }

    @Test
    fun getByIdentityNotFound() {
        val model = contactModelRepository.getByIdentity("ABCDEFGH")
        assertNull(model)
        val modelMd = contactModelRepositoryMd.getByIdentity("ABCDEFGH")
        assertNull(modelMd)
    }

    @Test
    fun getByIdentityExisting() {
        val identity = randomIdentity()
        val publicKey = nonSecureRandomArray(32)

        // Create contact using "old model"
        databaseService.contactModelFactory.createOrUpdate(ContactModel.create(identity, publicKey))
        databaseServiceMd.contactModelFactory.createOrUpdate(ContactModel.create(identity, publicKey))

        // Fetch contact using "new model"
        val model = contactModelRepository.getByIdentity(identity)!!
        val modelMd = contactModelRepositoryMd.getByIdentity(identity)!!
        assertEquals(model.identity, modelMd.identity)
        assertContentEquals(model.data, modelMd.data)
        assertTrue { model.identity == identity }
        assertTrue { model.data?.identity == identity }
        assertContentEquals(publicKey, model.data?.publicKey)
    }

    private fun testCreateFromLocalOrRemote(
        contactModelData: ContactModelData,
        triggerSource: TestTriggerSource,
    ) {
        assertNull(contactModelRepository.getByIdentity(contactModelData.identity))
        assertNull(contactModelRepositoryMd.getByIdentity(contactModelData.identity))

        val (newModel, newModelMd) = runBlocking {
            when (triggerSource) {
                TestTriggerSource.FROM_LOCAL -> {
                    contactModelRepository.createFromLocal(contactModelData) to
                        contactModelRepositoryMd.createFromLocal(contactModelData)
                }

                TestTriggerSource.FROM_REMOTE -> {
                    contactModelRepository.createFromRemote(
                        contactModelData = contactModelData,
                        handle = UnusedTaskCodec(),
                    ) to
                        contactModelRepositoryMd.createFromRemote(
                            contactModelData = contactModelData,
                            handle = taskCodecMd,
                        )
                }
            }
        }

        // Assert that a transaction has been executed in the MD context
        assertEquals(1, taskCodecMd.transactionBeginCount)
        assertEquals(1, taskCodecMd.transactionCommitCount)

        val queriedModel = contactModelRepository.getByIdentity(contactModelData.identity)
        assertEquals(newModel, queriedModel)

        val queriedModelMd = contactModelRepositoryMd.getByIdentity(contactModelData.identity)
        assertEquals(newModelMd, queriedModelMd)

        assertContentEquals(contactModelData, newModel.data)
        assertContentEquals(contactModelData, newModelMd.data)

        // Reset transaction count in case this test is run several times
        taskCodecMd.transactionBeginCount = 0
        taskCodecMd.transactionCommitCount = 0
    }

    /**
     * Insert the given [contactModelData] twice (from local or remote; depending on the given
     * [triggerSource]): The first time this should create a new contact, the second time it should
     * throw a [ContactStoreException].
     */
    private fun testCreateFromLocalOrRemoteTwice(
        contactModelData: ContactModelData,
        triggerSource: TestTriggerSource,
    ) {
        assertNull(contactModelRepository.getByIdentity(contactModelData.identity))
        assertNull(contactModelRepositoryMd.getByIdentity(contactModelData.identity))

        val (runCreation, runCreationMd) = when (triggerSource) {
            TestTriggerSource.FROM_LOCAL -> {
                suspend {
                    contactModelRepository.createFromLocal(contactModelData)
                } to
                    suspend {
                        contactModelRepositoryMd.createFromLocal(contactModelData)
                    }
            }

            TestTriggerSource.FROM_REMOTE -> {
                suspend {
                    contactModelRepository.createFromRemote(
                        contactModelData = contactModelData,
                        handle = UnusedTaskCodec(),
                    )
                } to
                    suspend {
                        contactModelRepositoryMd.createFromRemote(
                            contactModelData = contactModelData,
                            handle = taskCodecMd,
                        )
                    }
            }
        }

        // Insert it for the first time
        val (newModel, newModelMd) = runBlocking {
            runCreation() to runCreationMd()
        }

        // Assert that a transaction has been executed in the MD context
        assertEquals(1, taskCodecMd.transactionBeginCount)
        assertEquals(1, taskCodecMd.transactionCommitCount)

        val queriedModel = contactModelRepository.getByIdentity(contactModelData.identity)
        assertEquals(newModel, queriedModel)

        val queriedModelMd = contactModelRepositoryMd.getByIdentity(contactModelData.identity)
        assertEquals(newModelMd, queriedModelMd)

        assertContentEquals(contactModelData, newModel.data)
        assertContentEquals(contactModelData, newModelMd.data)

        // Insert for the second time and assert that an exception is thrown
        assertFailsWith<ContactStoreException> { runBlocking { runCreation() } }
        assertFailsWith<ContactReflectException> { runBlocking { runCreationMd() } }

        // Assert that there is still only one transaction and therefore the transaction has not
        // been executed again (due to precondition failure)
        assertEquals(1, taskCodecMd.transactionBeginCount)
        assertEquals(1, taskCodecMd.transactionCommitCount)

        // Reset transaction count in case this test is run several times
        taskCodecMd.transactionBeginCount = 0
        taskCodecMd.transactionCommitCount = 0
    }

    private fun testCreateFromSync(contactModelData: ContactModelData) {
        // Assert that the contact does not exist yet
        assertNull(contactModelRepositoryMd.getByIdentity(contactModelData.identity))

        // Create the contact
        val contactModel = contactModelRepositoryMd.createFromSync(contactModelData)

        // Assert that no transactions were created and no messages were sent
        assertEquals(0, taskCodecMd.transactionBeginCount)
        assertEquals(0, taskCodecMd.transactionCommitCount)
        assertTrue(taskCodecMd.outboundMessages.isEmpty())

        // Assert that the contact data has been inserted correctly
        val addedData = contactModel.data!!
        assertContentEquals(contactModelData, addedData)

        // Reset transaction count in case this test is run several times
        taskCodecMd.transactionBeginCount = 0
        taskCodecMd.transactionCommitCount = 0
    }

    /**
     * Insert the given [contactModelData] twice from sync: The first time this should create a new
     * contact, the second time it should throw a [ContactStoreException].
     */
    private fun testCreateFromSyncTwice(contactModelData: ContactModelData) {
        // Assert that the contact does not exist yet
        assertNull(contactModelRepositoryMd.getByIdentity(contactModelData.identity))

        // Create the contact
        val contactModel = contactModelRepositoryMd.createFromSync(contactModelData)

        // Assert that no transactions were created and no messages were sent
        assertEquals(0, taskCodecMd.transactionBeginCount)
        assertEquals(0, taskCodecMd.transactionCommitCount)
        assertTrue(taskCodecMd.outboundMessages.isEmpty())

        // Assert that the contact data has been inserted correctly
        val addedData = contactModel.data!!
        assertContentEquals(contactModelData, addedData)

        // Assert that the contact data cannot be inserted again (as it already exists)
        assertFailsWith<ContactStoreException> {
            contactModelRepositoryMd.createFromSync(contactModelData)
        }

        // Reset transaction count in case this test is run several times
        taskCodecMd.transactionBeginCount = 0
        taskCodecMd.transactionCommitCount = 0
    }

    @Test
    fun updateNickname() {
        // Create contact using "old model"
        val identity = randomIdentity()
        databaseService.contactModelFactory.createOrUpdate(
            ContactModel.create(
                identity,
                nonSecureRandomArray(32),
            ),
        )

        // Fetch model
        val model: ch.threema.data.models.ContactModel? =
            contactModelRepository.getByIdentity(identity)
        assertNotNull(model)
        assertEquals(null, model!!.data?.nickname)
        model.setNicknameFromSync("testnick")
        assertEquals("testnick", model.data?.nickname)
    }

    private fun assertContentEquals(expected: ContactModelData?, actual: ContactModelData?) {
        if (expected == null && actual == null) {
            return
        }

        if (expected == null) {
            fail("Actual data expected to be null")
        }

        if (actual == null) {
            fail("Actual data expected to be non null")
        }

        assertEquals(expected.identity, actual.identity)
        assertContentEquals(expected.publicKey, actual.publicKey)
        assertEquals(expected.createdAt, actual.createdAt)
        assertEquals(expected.firstName, actual.firstName)
        assertEquals(expected.lastName, actual.lastName)
        assertEquals(expected.nickname, actual.nickname)
        assertEquals(expected.idColor, actual.idColor)
        assertEquals(expected.verificationLevel, actual.verificationLevel)
        assertEquals(expected.workVerificationLevel, actual.workVerificationLevel)
        assertEquals(expected.identityType, actual.identityType)
        assertEquals(expected.acquaintanceLevel, actual.acquaintanceLevel)
        assertEquals(expected.activityState, actual.activityState)
        assertEquals(expected.syncState, actual.syncState)
        assertEquals(expected.featureMask, actual.featureMask)
        assertEquals(expected.readReceiptPolicy, actual.readReceiptPolicy)
        assertEquals(expected.typingIndicatorPolicy, actual.typingIndicatorPolicy)
        assertEquals(
            expected.notificationTriggerPolicyOverride,
            actual.notificationTriggerPolicyOverride,
        )
        assertEquals(expected.androidContactLookupKey, actual.androidContactLookupKey)

        // TODO(ANDR-2998): Assert that notification sound policy override are set correctly

        // Just in case there are new fields added that are not explicitly compared here
        assertEquals(expected, actual)
    }
}

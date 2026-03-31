package ch.threema.data.repositories

import ch.threema.KoinTestRule
import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.mockUser
import ch.threema.base.crypto.NaCl
import ch.threema.data.datatypes.AndroidContactLookupInfo
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
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.storage.DatabaseService
import ch.threema.storage.TestDatabaseProvider
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.testhelpers.nonSecureRandomArray
import ch.threema.testhelpers.randomIdentity
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.koin.dsl.module

@RunWith(value = Parameterized::class)
class ContactModelRepositoryTest(private val contactModelData: ContactModelData) {
    // Services where MD is disabled
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var databaseService: DatabaseService
    private lateinit var coreServiceManager: TestCoreServiceManager
    private lateinit var contactModelRepository: ContactModelRepository

    // Services where MD is enabled
    private lateinit var databaseProviderMd: TestDatabaseProvider
    private lateinit var databaseServiceMd: DatabaseService
    private lateinit var taskCodecMd: TransactionAckTaskCodec
    private lateinit var coreServiceManagerMd: TestCoreServiceManager
    private lateinit var contactModelRepositoryMd: ContactModelRepository

    private enum class TestTriggerSource {
        FROM_LOCAL,
        FROM_REMOTE,
    }

    private var isMultiDeviceEnabled = false

    private val instrumentedTestModule = module {
        factory<MultiDeviceManager> {
            if (isMultiDeviceEnabled) {
                coreServiceManagerMd.multiDeviceManager
            } else {
                coreServiceManager.multiDeviceManager
            }
        }
    }

    @get:Rule
    val koinTestRule = KoinTestRule(
        modules = listOf(instrumentedTestModule),
    )

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
            identity: IdentityString = "ABCDEFGH",
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
            androidContactLookupInfo: AndroidContactLookupInfo? = null,
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
            androidContactLookupInfo = androidContactLookupInfo,
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
        val preferenceStore: PreferenceStore = mockk {
            mockUser(TestHelpers.TEST_CONTACT)
        }
        val encryptedPreferenceStore: EncryptedPreferenceStore = mockk {
            mockUser(TestHelpers.TEST_CONTACT)
        }
        val identityProviderMock: IdentityProvider = mockk {
            every { getIdentity() } returns Identity(TestHelpers.TEST_CONTACT.identity)
            every { getIdentityString() } returns TestHelpers.TEST_CONTACT.identity
        }
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns Identity(TestHelpers.TEST_CONTACT.identity)
            every { getIdentityString() } returns TestHelpers.TEST_CONTACT.identity
        }

        // Instantiate services where MD is disabled
        this.databaseProvider = TestDatabaseProvider()
        this.coreServiceManager = TestCoreServiceManager(
            databaseProvider = databaseProvider,
            identityProvider = identityProviderMock,
            preferenceStore = preferenceStore,
            encryptedPreferenceStore = encryptedPreferenceStore,
            taskManager = TestTaskManager(UnusedTaskCodec()),
            identityStore = identityStoreMock,
        )
        this.databaseService = coreServiceManager.databaseService
        this.contactModelRepository = ModelRepositories(coreServiceManager, identityProviderMock).contacts

        // Instantiate services where MD is enabled
        this.databaseProviderMd = TestDatabaseProvider()
        this.taskCodecMd = TransactionAckTaskCodec()
        this.coreServiceManagerMd = TestCoreServiceManager(
            databaseProvider = databaseProviderMd,
            identityProvider = identityProviderMock,
            preferenceStore = preferenceStore,
            encryptedPreferenceStore = encryptedPreferenceStore,
            multiDeviceManager = TestMultiDeviceManager(
                isMultiDeviceActive = true,
                isMdDisabledOrSupportsFs = false,
            ),
            taskManager = TestTaskManager(taskCodecMd),
            identityStore = identityStoreMock,
        )
        this.databaseServiceMd = coreServiceManagerMd.databaseService
        this.contactModelRepositoryMd = ModelRepositories(coreServiceManagerMd, identityProviderMock).contacts
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

    @Test
    fun userCannotBeAddedAsContact() {
        assertFailsWith<InvalidContactException> {
            contactModelRepository.createFromSync(
                contactModelData = contactModelData.copy(identity = TestHelpers.TEST_CONTACT.identity),
            )
        }
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
                    declareNonMdDependencies()
                    val newModel = contactModelRepository.createFromLocal(contactModelData)

                    declareMdDependencies()
                    val newModelMd = contactModelRepositoryMd.createFromLocal(contactModelData)

                    newModel to newModelMd
                }

                TestTriggerSource.FROM_REMOTE -> {
                    declareNonMdDependencies()
                    val newContactModel = contactModelRepository.createFromRemote(
                        contactModelData = contactModelData,
                        handle = UnusedTaskCodec(),
                    )

                    declareMdDependencies()
                    val newContactModelMd =
                        contactModelRepositoryMd.createFromRemote(
                            contactModelData = contactModelData,
                            handle = taskCodecMd,
                        )

                    newContactModel to newContactModelMd
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
                val runCreation = suspend {
                    declareNonMdDependencies()
                    contactModelRepository.createFromLocal(contactModelData)
                }
                val runCreationMd =
                    suspend {
                        declareMdDependencies()
                        contactModelRepositoryMd.createFromLocal(contactModelData)
                    }

                runCreation to runCreationMd
            }

            TestTriggerSource.FROM_REMOTE -> {
                val runCreation = suspend {
                    declareNonMdDependencies()
                    contactModelRepository.createFromRemote(
                        contactModelData = contactModelData,
                        handle = UnusedTaskCodec(),
                    )
                }
                val runCreationMd = suspend {
                    declareMdDependencies()
                    contactModelRepositoryMd.createFromRemote(
                        contactModelData = contactModelData,
                        handle = taskCodecMd,
                    )
                }

                runCreation to runCreationMd
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

    private fun declareNonMdDependencies() {
        isMultiDeviceEnabled = false
    }

    private fun declareMdDependencies() {
        isMultiDeviceEnabled = true
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
        assertEquals(expected.androidContactLookupInfo, actual.androidContactLookupInfo)

        // TODO(ANDR-2998): Assert that notification sound policy override are set correctly

        // Just in case there are new fields added that are not explicitly compared here
        assertEquals(expected, actual)
    }
}

package ch.threema.app.contacts

import android.os.Looper
import ch.threema.app.TestCoreServiceManager
import ch.threema.app.asynctasks.AddContactRestrictionPolicy
import ch.threema.app.asynctasks.AddOrUpdateContactBackgroundTask
import ch.threema.app.asynctasks.AlreadyVerified
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask
import ch.threema.app.asynctasks.ContactCreated
import ch.threema.app.asynctasks.ContactExists
import ch.threema.app.asynctasks.ContactModified
import ch.threema.app.asynctasks.ContactResult
import ch.threema.app.asynctasks.InvalidThreemaId
import ch.threema.app.asynctasks.RemotePublicKeyMismatch
import ch.threema.app.asynctasks.UserIdentity
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.base.crypto.NaCl
import ch.threema.common.Http
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.ModelRepositories
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.APIConnector.FetchIdentityResult
import ch.threema.domain.protocol.api.APIConnector.HttpConnectionException
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.storage.TestDatabaseProvider
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AddOrUpdateContactBackgroundTaskTest : KoinComponent {
    private val appRestrictions: AppRestrictions by inject()

    private val backgroundExecutor = BackgroundExecutor()
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var coreServiceManager: CoreServiceManager
    private lateinit var contactModelRepository: ContactModelRepository

    private val myIdentity = "00000000"

    @BeforeTest
    fun before() {
        databaseProvider = TestDatabaseProvider()
        val identityProviderMock = mockk<IdentityProvider> {
            every { getIdentity() } returns Identity(myIdentity)
            every { getIdentityString() } returns myIdentity
        }
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns Identity(myIdentity)
            every { getIdentityString() } returns myIdentity
        }
        coreServiceManager = TestCoreServiceManager(
            databaseProvider = databaseProvider,
            identityProvider = identityProviderMock,
            identityStore = identityStoreMock,
        )
        contactModelRepository = ModelRepositories(coreServiceManager, identityProviderMock).contacts
    }

    @Test
    fun testAddSuccessful() {
        val newIdentity = "01234567"

        testAddingContact(
            { identity ->
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                    it.featureMask = 12
                    it.type = 0
                    it.state = IdentityState.ACTIVE.value
                }
            },
            {
                assertTrue(it is ContactCreated)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data!!
                assertEquals(newIdentity, data.identity)
                assertContentEquals(ByteArray(NaCl.PUBLIC_KEY_BYTES), data.publicKey)
                assertEquals(12u, data.featureMask)
                assertEquals(IdentityType.NORMAL, data.identityType)
                assertEquals(IdentityState.ACTIVE, data.activityState)
                assertEquals(VerificationLevel.UNVERIFIED, data.verificationLevel)
                assertEquals(AcquaintanceLevel.DIRECT, data.acquaintanceLevel)
            },
        )
    }

    @Test
    fun testAddGroupContactSuccessful() {
        val newIdentity = "01234567"

        testAddingContact(
            fetchIdentity = { identity ->
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                    it.featureMask = 12
                    it.type = 0
                    it.state = IdentityState.ACTIVE.value
                }
            },
            acquaintanceLevel = AcquaintanceLevel.GROUP,
            runOnFinished = {
                assertTrue(it is ContactCreated)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data!!
                assertEquals(newIdentity, data.identity)
                assertContentEquals(ByteArray(NaCl.PUBLIC_KEY_BYTES), data.publicKey)
                assertEquals(12u, data.featureMask)
                assertEquals(IdentityType.NORMAL, data.identityType)
                assertEquals(IdentityState.ACTIVE, data.activityState)
                assertEquals(VerificationLevel.UNVERIFIED, data.verificationLevel)
                assertEquals(AcquaintanceLevel.GROUP, data.acquaintanceLevel)
            },
        )
    }

    @Test
    fun testAddSuccessfulVerified() {
        val newIdentity = "01234567"

        testAddingContact(
            { identity ->
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                    it.featureMask = 127
                    it.type = 1
                    it.state = IdentityState.INACTIVE.value
                }
            },
            {
                assertTrue(it is ContactCreated)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data!!
                assertEquals(newIdentity, data.identity)
                assertContentEquals(ByteArray(NaCl.PUBLIC_KEY_BYTES), data.publicKey)
                assertEquals(127u, data.featureMask)
                assertEquals(IdentityType.WORK, data.identityType)
                assertEquals(IdentityState.INACTIVE, data.activityState)
                assertEquals(VerificationLevel.FULLY_VERIFIED, data.verificationLevel)
                assertEquals(AcquaintanceLevel.DIRECT, data.acquaintanceLevel)
            },
            publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES),
        )
    }

    @Test
    fun testAddMyIdentity() {
        testAddingContact(
            { identity ->
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                    it.featureMask = 127
                    it.type = 1
                    it.state = IdentityState.INACTIVE.value
                }
            },
            {
                assertTrue(it is UserIdentity)
            },
            newIdentity = myIdentity,
            myIdentity = myIdentity,
        )
    }

    @Test
    fun testAddPublicKeyMismatch() {
        testAddingContact(
            { identity ->
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                    it.featureMask = 12
                    it.type = 0
                    it.state = IdentityState.ACTIVE.value
                }
            },
            {
                assertTrue(it is RemotePublicKeyMismatch)
            },
            publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES).also { it.fill(1) },
        )
    }

    @Test
    fun testAddInvalidId() {
        testAddingContact(
            {
                throw HttpConnectionException(Http.StatusCode.NOT_FOUND, Exception())
            },
            {
                assertTrue(it is InvalidThreemaId)
            },
        )
    }

    @Test
    fun testAddExistingContact() {
        val apiConnectorResult: (identity: IdentityString) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                it.featureMask = 12
                it.type = 0
                it.state = IdentityState.ACTIVE.value
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactCreated)
            },
        )

        // The second time adding the contact should fail
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactExists)
            },
        )
    }

    @Test
    fun testVerifyTwice() {
        val publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES).apply { fill(2) }

        val apiConnectorResult: (identity: IdentityString) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = publicKey
                it.featureMask = 12
                it.type = 0
                it.state = IdentityState.ACTIVE.value
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactCreated)
            },
            publicKey = publicKey,
        )

        // The second time adding the contact should fail
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is AlreadyVerified)
            },
            publicKey = publicKey,
        )
    }

    @Test
    fun testUpgradeGroupContact() {
        val newIdentity = "01234567"

        val apiConnectorResult: (identity: IdentityString) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                it.featureMask = 12
                it.type = 0
                it.state = IdentityState.ACTIVE.value
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactCreated)
            },
            newIdentity = newIdentity,
        )

        val contactModel = contactModelRepository.getByIdentity(newIdentity)!!

        // Downgrade the contact to a group contact
        contactModel.setAcquaintanceLevelFromLocal(AcquaintanceLevel.GROUP)

        // Assert that the acquaintance level change worked
        assertEquals(AcquaintanceLevel.GROUP, contactModel.data!!.acquaintanceLevel)

        // When adding the contact again, it should be converted back to a direct contact
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactModified)
                assertTrue(it.acquaintanceLevelChanged)
                assertFalse(it.verificationLevelChanged)
                assertEquals(AcquaintanceLevel.DIRECT, contactModel.data!!.acquaintanceLevel)
            },
            newIdentity = newIdentity,
        )
    }

    @Test
    fun testVerificationLevelUpgrade() {
        val newIdentity = "01234567"

        val apiConnectorResult: (identity: IdentityString) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                it.featureMask = 12
                it.type = 0
                it.state = IdentityState.ACTIVE.value
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactCreated)
            },
            newIdentity = newIdentity,
        )

        val contactModel = contactModelRepository.getByIdentity(newIdentity)!!

        // Assert that the verification level is unverified
        assertEquals(VerificationLevel.UNVERIFIED, contactModel.data!!.verificationLevel)

        // When adding the contact again, it should be fully verified
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactModified)
                assertTrue(it.verificationLevelChanged)
                assertFalse(it.acquaintanceLevelChanged)
                assertEquals(
                    VerificationLevel.FULLY_VERIFIED,
                    contactModel.data!!.verificationLevel,
                )
            },
            newIdentity = newIdentity,
            publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES),
        )
    }

    @Test
    fun testAddAndVerifyGroupContact() {
        val newIdentity = "01234567"

        val apiConnectorResult: (identity: IdentityString) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                it.featureMask = 12
                it.type = 0
                it.state = IdentityState.ACTIVE.value
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactCreated)
            },
            newIdentity = newIdentity,
        )

        val contactModel = contactModelRepository.getByIdentity(newIdentity)!!

        // Assert that the verification level is unverified
        assertEquals(VerificationLevel.UNVERIFIED, contactModel.data!!.verificationLevel)

        // Downgrade the contact to acquaintance level group
        contactModel.setAcquaintanceLevelFromLocal(AcquaintanceLevel.GROUP)
        assertEquals(AcquaintanceLevel.GROUP, contactModel.data!!.acquaintanceLevel)

        // When adding the contact again, it should be converted back to a direct contact
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactModified)
                assertTrue(it.acquaintanceLevelChanged)
                assertTrue(it.verificationLevelChanged)
                assertEquals(AcquaintanceLevel.DIRECT, contactModel.data!!.acquaintanceLevel)
                assertEquals(
                    VerificationLevel.FULLY_VERIFIED,
                    contactModel.data!!.verificationLevel,
                )
            },
            newIdentity = newIdentity,
            publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES),
        )
    }

    @Test
    fun testThreadUsage() {
        val identity = "01234567"
        val myIdentity = "00000000"

        testAddingContact(
            {
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
                    it.featureMask = 12
                    it.type = 0
                    it.state = IdentityState.ACTIVE.value
                }
            },
            {},
            newIdentity = identity,
            myIdentity = myIdentity,
            publicKey = null,
        )

        val unusedAPIConnector = getTestApiConnector {
            throw AssertionError("This must not be executed for this test")
        }

        val testThreadId = Thread.currentThread().id

        val addTask = object : AddOrUpdateContactBackgroundTask<Boolean>(
            identity = identity,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
            myIdentity = myIdentity,
            apiConnector = unusedAPIConnector,
            contactModelRepository = contactModelRepository,
            addContactRestrictionPolicy = AddContactRestrictionPolicy.CHECK,
            appRestrictions = appRestrictions,
            expectedPublicKey = null,
        ) {
            override fun onBefore() {
                assertEquals(testThreadId, Thread.currentThread().id)
            }

            override fun onContactResult(result: ContactResult): Boolean {
                assertTrue(result is ContactExists)
                assertNotEquals(testThreadId, Thread.currentThread().id)
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                return true
            }

            override fun onFinished(result: Boolean) {
                assertTrue(result)
                assertEquals(Looper.getMainLooper(), Looper.myLooper())
            }
        }

        runBlocking {
            assertTrue(backgroundExecutor.executeDeferred(addTask).await())
        }
    }

    private fun testAddingContact(
        fetchIdentity: (identity: IdentityString) -> FetchIdentityResult,
        runOnFinished: (result: ContactResult) -> Unit,
        newIdentity: IdentityString = "01234567",
        acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
        myIdentity: IdentityString = "00000000",
        publicKey: ByteArray? = null,
    ) {
        val apiConnector = getTestApiConnector {
            if (it != newIdentity) {
                fail("Wrong identity is fetched: $it")
            }

            fetchIdentity(it)
        }

        val contactAdded =
            backgroundExecutor.executeDeferred(
                object : BasicAddOrUpdateContactBackgroundTask(
                    identity = newIdentity,
                    acquaintanceLevel = acquaintanceLevel,
                    myIdentity = myIdentity,
                    apiConnector = apiConnector,
                    contactModelRepository = contactModelRepository,
                    addContactRestrictionPolicy = AddContactRestrictionPolicy.CHECK,
                    appRestrictions = appRestrictions,
                    expectedPublicKey = publicKey,
                ) {
                    override fun onFinished(result: ContactResult) {
                        runOnFinished(result)
                    }
                },
            )

        // Assert that the test is not stopped before running the background task completely
        runBlocking {
            contactAdded.await()
        }
    }

    private fun getTestApiConnector(onIdentityFetchCalled: (identity: IdentityString) -> FetchIdentityResult): APIConnector {
        return object : APIConnector(false, null, false, OkHttpClient(), Version(), null, null) {
            override fun fetchIdentity(identity: IdentityString) = onIdentityFetchCalled(identity)
        }
    }
}

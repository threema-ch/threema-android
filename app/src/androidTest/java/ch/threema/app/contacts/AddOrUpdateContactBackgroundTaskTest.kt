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

package ch.threema.app.contacts

import android.os.Looper
import ch.threema.app.TestCoreServiceManager
import ch.threema.app.ThreemaApplication
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
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.data.TestDatabaseService
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.ModelRepositories
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.protocol.SSLSocketFactoryFactory
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.APIConnector.FetchIdentityResult
import ch.threema.domain.protocol.api.APIConnector.HttpConnectionException
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import com.neilalexander.jnacl.NaCl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import java.net.HttpURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class AddOrUpdateContactBackgroundTaskTest {

    private val backgroundExecutor = BackgroundExecutor()
    private lateinit var databaseService: TestDatabaseService
    private lateinit var coreServiceManager: CoreServiceManager
    private lateinit var contactModelRepository: ContactModelRepository

    @Before
    fun before() {
        databaseService = TestDatabaseService()
        val serviceManager = ThreemaApplication.requireServiceManager()
        coreServiceManager = TestCoreServiceManager(
            version = ThreemaApplication.getAppVersion(),
            databaseService = databaseService,
            preferenceStore = serviceManager.preferenceStore,
        )
        contactModelRepository = ModelRepositories(coreServiceManager).contacts
    }

    @Test
    fun testAddSuccessful() {
        val newIdentity = "01234567"

        testAddingContact(
            { identity ->
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
                    it.featureMask = 12
                    it.type = 0
                    it.state = IdentityState.ACTIVE.value
                }
            },
            {
                assertTrue(it is ContactCreated)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data.value!!
                assertEquals(newIdentity, data.identity)
                assertArrayEquals(ByteArray(NaCl.PUBLICKEYBYTES), data.publicKey)
                assertEquals(12u, data.featureMask)
                assertEquals(IdentityType.NORMAL, data.identityType)
                assertEquals(IdentityState.ACTIVE, data.activityState)
                assertEquals(VerificationLevel.UNVERIFIED, data.verificationLevel)
                assertEquals(AcquaintanceLevel.DIRECT, data.acquaintanceLevel)
            }
        )
    }

    @Test
    fun testAddGroupContactSuccessful() {
        val newIdentity = "01234567"

        testAddingContact(
            fetchIdentity = { identity ->
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
                    it.featureMask = 12
                    it.type = 0
                    it.state = IdentityState.ACTIVE.value
                }
            },
            acquaintanceLevel = AcquaintanceLevel.GROUP,
            runOnFinished = {
                assertTrue(it is ContactCreated)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data.value!!
                assertEquals(newIdentity, data.identity)
                assertArrayEquals(ByteArray(NaCl.PUBLICKEYBYTES), data.publicKey)
                assertEquals(12u, data.featureMask)
                assertEquals(IdentityType.NORMAL, data.identityType)
                assertEquals(IdentityState.ACTIVE, data.activityState)
                assertEquals(VerificationLevel.UNVERIFIED, data.verificationLevel)
                assertEquals(AcquaintanceLevel.GROUP, data.acquaintanceLevel)
            }
        )
    }

    @Test
    fun testAddSuccessfulVerified() {
        val newIdentity = "01234567"

        testAddingContact(
            { identity ->
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
                    it.featureMask = 127
                    it.type = 1
                    it.state = IdentityState.INACTIVE.value
                }
            },
            {
                assertTrue(it is ContactCreated)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data.value!!
                assertEquals(newIdentity, data.identity)
                assertArrayEquals(ByteArray(NaCl.PUBLICKEYBYTES), data.publicKey)
                assertEquals(127u, data.featureMask)
                assertEquals(IdentityType.WORK, data.identityType)
                assertEquals(IdentityState.INACTIVE, data.activityState)
                assertEquals(VerificationLevel.FULLY_VERIFIED, data.verificationLevel)
                assertEquals(AcquaintanceLevel.DIRECT, data.acquaintanceLevel)
            },
            publicKey = ByteArray(NaCl.PUBLICKEYBYTES),
        )
    }

    @Test
    fun testAddMyIdentity() {
        val myIdentity = "00000000"
        testAddingContact(
            { identity ->
                FetchIdentityResult().also {
                    it.identity = identity
                    it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
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
                    it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
                    it.featureMask = 12
                    it.type = 0
                    it.state = IdentityState.ACTIVE.value
                }
            },
            {
                assertTrue(it is RemotePublicKeyMismatch)
            },
            publicKey = ByteArray(NaCl.PUBLICKEYBYTES).also { it.fill(1) }
        )
    }

    @Test
    fun testAddInvalidId() {
        testAddingContact(
            {
                throw HttpConnectionException(HttpURLConnection.HTTP_NOT_FOUND, Exception())
            },
            {
                assertTrue(it is InvalidThreemaId)
            }
        )
    }

    @Test
    fun testAddExistingContact() {
        val apiConnectorResult: (identity: String) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
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
            }
        )

        // The second time adding the contact should fail
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactExists)
            }
        )
    }

    @Test
    fun testVerifyTwice() {
        val publicKey = ByteArray(NaCl.PUBLICKEYBYTES).apply { fill(2) }

        val apiConnectorResult: (identity: String) -> FetchIdentityResult = { identity ->
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
            publicKey = publicKey
        )
    }

    @Test
    fun testUpgradeGroupContact() {
        val newIdentity = "01234567"

        val apiConnectorResult: (identity: String) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
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
            newIdentity = newIdentity
        )

        val contactModel = contactModelRepository.getByIdentity(newIdentity)!!

        // Downgrade the contact to a group contact
        contactModel.setAcquaintanceLevelFromLocal(AcquaintanceLevel.GROUP)

        // Assert that the acquaintance level change worked
        assertEquals(AcquaintanceLevel.GROUP, contactModel.data.value!!.acquaintanceLevel)

        // When adding the contact again, it should be converted back to a direct contact
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactModified)
                assertTrue(it.acquaintanceLevelChanged)
                assertFalse(it.verificationLevelChanged)
                assertEquals(AcquaintanceLevel.DIRECT, contactModel.data.value!!.acquaintanceLevel)
            },
            newIdentity = newIdentity
        )
    }

    @Test
    fun testVerificationLevelUpgrade() {
        val newIdentity = "01234567"

        val apiConnectorResult: (identity: String) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
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
            newIdentity = newIdentity
        )

        val contactModel = contactModelRepository.getByIdentity(newIdentity)!!

        // Assert that the verification level is unverified
        assertEquals(VerificationLevel.UNVERIFIED, contactModel.data.value!!.verificationLevel)

        // When adding the contact again, it should be fully verified
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactModified)
                assertTrue(it.verificationLevelChanged)
                assertFalse(it.acquaintanceLevelChanged)
                assertEquals(
                    VerificationLevel.FULLY_VERIFIED,
                    contactModel.data.value!!.verificationLevel
                )
            },
            newIdentity = newIdentity,
            publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
        )
    }

    @Test
    fun testAddAndVerifyGroupContact() {
        val newIdentity = "01234567"

        val apiConnectorResult: (identity: String) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
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
            newIdentity = newIdentity
        )

        val contactModel = contactModelRepository.getByIdentity(newIdentity)!!

        // Assert that the verification level is unverified
        assertEquals(VerificationLevel.UNVERIFIED, contactModel.data.value!!.verificationLevel)

        // Downgrade the contact to acquaintance level group
        contactModel.setAcquaintanceLevelFromLocal(AcquaintanceLevel.GROUP)
        assertEquals(AcquaintanceLevel.GROUP, contactModel.data.value!!.acquaintanceLevel)

        // When adding the contact again, it should be converted back to a direct contact
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactModified)
                assertTrue(it.acquaintanceLevelChanged)
                assertTrue(it.verificationLevelChanged)
                assertEquals(AcquaintanceLevel.DIRECT, contactModel.data.value!!.acquaintanceLevel)
                assertEquals(VerificationLevel.FULLY_VERIFIED, contactModel.data.value!!.verificationLevel)
            },
            newIdentity = newIdentity,
            publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
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
                    it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
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
            AcquaintanceLevel.DIRECT,
            myIdentity = myIdentity,
            unusedAPIConnector,
            contactModelRepository,
            AddContactRestrictionPolicy.CHECK,
            ThreemaApplication.getAppContext(),
            null,
        ) {
            override fun onBefore() {
                assertEquals(testThreadId, Thread.currentThread().id)
            }

            override fun onContactAdded(result: ContactResult): Boolean {
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
        fetchIdentity: (identity: String) -> FetchIdentityResult,
        runOnFinished: (result: ContactResult) -> Unit,
        newIdentity: String = "01234567",
        acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
        myIdentity: String = "00000000",
        publicKey: ByteArray? = null,
    ) {
        val apiConnector = getTestApiConnector {
            if (it != newIdentity) {
                fail("Wrong identity is fetched: $it")
            }

            fetchIdentity(it)
        }

        val contactAdded =
            backgroundExecutor.executeDeferred(object : BasicAddOrUpdateContactBackgroundTask(
                newIdentity,
                acquaintanceLevel,
                myIdentity,
                apiConnector,
                contactModelRepository,
                AddContactRestrictionPolicy.CHECK,
                ThreemaApplication.getAppContext(),
                publicKey,
            ) {
                override fun onFinished(result: ContactResult) {
                    runOnFinished(result)
                }
            })

        // Assert that the test is not stopped before running the background task completely
        runBlocking {
            contactAdded.await()
        }
    }

    private fun getTestApiConnector(onIdentityFetchCalled: (identity: String) -> FetchIdentityResult): APIConnector {
        val sslSocketFactoryFactory = SSLSocketFactoryFactory { host: String? ->
            ConfigUtils.getSSLSocketFactory(host)
        }

        return object : APIConnector(false, null, false, sslSocketFactoryFactory) {
            override fun fetchIdentity(identity: String) = onIdentityFetchCalled(identity)
        }
    }

}

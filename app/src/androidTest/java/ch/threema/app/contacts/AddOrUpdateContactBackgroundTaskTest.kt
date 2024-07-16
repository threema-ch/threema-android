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

import ch.threema.app.ThreemaApplication
import ch.threema.app.asynctasks.AddContactRestrictionPolicy
import ch.threema.app.asynctasks.AddOrUpdateContactBackgroundTask
import ch.threema.app.asynctasks.AlreadyVerified
import ch.threema.app.asynctasks.ContactExists
import ch.threema.app.asynctasks.Failed
import ch.threema.app.asynctasks.ContactAddResult
import ch.threema.app.asynctasks.ContactModified
import ch.threema.app.asynctasks.Success
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
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import com.neilalexander.jnacl.NaCl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import java.net.HttpURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.fail

class AddOrUpdateContactBackgroundTaskTest {

    private val backgroundExecutor = BackgroundExecutor()
    private lateinit var databaseService: TestDatabaseService
    private lateinit var contactModelRepository: ContactModelRepository

    @Before
    fun before() {
        databaseService = TestDatabaseService()
        contactModelRepository = ModelRepositories(databaseService).contacts
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
                    it.state = IdentityState.ACTIVE
                }
            },
            {
                assertTrue(it is Success)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data.value!!
                assertEquals(newIdentity, data.identity)
                assertArrayEquals(ByteArray(NaCl.PUBLICKEYBYTES), data.publicKey)
                assertEquals(12u, data.featureMask)
                assertEquals(IdentityType.NORMAL, data.identityType)
                assertEquals(ContactModel.State.ACTIVE, data.activityState)
                assertEquals(VerificationLevel.UNVERIFIED, data.verificationLevel)
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
                    it.state = IdentityState.INACTIVE
                }
            },
            {
                assertTrue(it is Success)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data.value!!
                assertEquals(newIdentity, data.identity)
                assertArrayEquals(ByteArray(NaCl.PUBLICKEYBYTES), data.publicKey)
                assertEquals(127u, data.featureMask)
                assertEquals(IdentityType.WORK, data.identityType)
                assertEquals(ContactModel.State.INACTIVE, data.activityState)
                assertEquals(VerificationLevel.FULLY_VERIFIED, data.verificationLevel)
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
                    it.state = IdentityState.INACTIVE
                }
            },
            {
                assertTrue(it is Failed)
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
                    it.state = IdentityState.ACTIVE
                }
            },
            {
                assertTrue(it is Failed)
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
                assertTrue(it is Failed)
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
                it.state = IdentityState.ACTIVE
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is Success)
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
                it.state = IdentityState.ACTIVE
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is Success)
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
    fun testAddGroupContact() {
        val newIdentity = "01234567"

        val apiConnectorResult: (identity: String) -> FetchIdentityResult = { identity ->
            FetchIdentityResult().also {
                it.identity = identity
                it.publicKey = ByteArray(NaCl.PUBLICKEYBYTES)
                it.featureMask = 12
                it.type = 0
                it.state = IdentityState.ACTIVE
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is Success)
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
                it.state = IdentityState.ACTIVE
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is Success)
            },
            newIdentity = newIdentity
        )

        val contactModel = contactModelRepository.getByIdentity(newIdentity)!!

        // Assert that the verification level is unverified
        assertEquals(VerificationLevel.UNVERIFIED, contactModel.data.value!!.verificationLevel)

        // When adding the contact again, it should be converted back to a direct contact
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is ContactModified)
                assertTrue(it.verificationLevelChanged)
                assertFalse(it.acquaintanceLevelChanged)
                assertEquals(VerificationLevel.FULLY_VERIFIED, contactModel.data.value!!.verificationLevel)
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
                it.state = IdentityState.ACTIVE
            }
        }

        // The first time adding the contact should succeed
        testAddingContact(
            apiConnectorResult,
            {
                assertTrue(it is Success)
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

    private fun testAddingContact(
        fetchIdentity: (identity: String) -> FetchIdentityResult,
        runOnFinished: (result: ContactAddResult) -> Unit,
        newIdentity: String = "01234567",
        myIdentity: String = "00000000",
        publicKey: ByteArray? = null,
    ) {
        val apiConnector = getTestApiConnector {
            if (it != newIdentity) {
                fail("Wrong identity is fetched: $it")
            }

            fetchIdentity(it)
        }

        val contactAdded = backgroundExecutor.executeDeferred(object : AddOrUpdateContactBackgroundTask(
            newIdentity,
            myIdentity,
            apiConnector,
            contactModelRepository,
            AddContactRestrictionPolicy.CHECK,
            ThreemaApplication.getAppContext(),
            publicKey,
        ) {
            override fun onFinished(result: ContactAddResult) {
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

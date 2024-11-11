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

package ch.threema.data.repositories

import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.threema.data.TestDatabaseService
import ch.threema.data.models.ModelDeletedException
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.ContactModel.State
import ch.threema.testhelpers.nonSecureRandomArray
import ch.threema.testhelpers.randomIdentity
import com.neilalexander.jnacl.NaCl
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.runner.RunWith
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ContactModelRepositoryTest {
    private lateinit var databaseService: TestDatabaseService
    private lateinit var contactModelRepository: ContactModelRepository

    private enum class TestTriggerSource {
        FROM_LOCAL,
        FROM_REMOTE,
    }

    private val initialValuesSet = setOf(
        InitialValues(),
        InitialValues(publicKey = ByteArray(NaCl.PUBLICKEYBYTES) { it.toByte() }),
        InitialValues(date = Date(42)),
        InitialValues(identityType = IdentityType.WORK),
        InitialValues(acquaintanceLevel = AcquaintanceLevel.GROUP),
        InitialValues(activityState = State.INACTIVE),
        InitialValues(featureMask = 64.toULong()),
    )

    @Before
    fun before() {
        this.databaseService = TestDatabaseService()
        this.contactModelRepository = ModelRepositories(databaseService).contacts
    }

    @Test
    fun createFromLocal() {
        initialValuesSet.forEach { testCreateFromLocalOrRemote(it, TestTriggerSource.FROM_LOCAL) }
    }

    @Test
    fun createFromRemote() {
        initialValuesSet.forEach { testCreateFromLocalOrRemote(it, TestTriggerSource.FROM_REMOTE) }
    }

    @Test
    fun createFromLocalTwice() {
        initialValuesSet.forEach {
            testCreateFromLocalOrRemoteTwice(it, TestTriggerSource.FROM_LOCAL)
        }
    }

    @Test
    fun createFromRemoteTwice() {
        initialValuesSet.forEach {
            testCreateFromLocalOrRemoteTwice(it, TestTriggerSource.FROM_REMOTE)
        }
    }

    @Test
    fun createFromSync() {
        // TODO(ANDR-2835): Create contact from sync
    }

    @Test
    fun getByIdentityNotFound() {
        val model = contactModelRepository.getByIdentity("ABCDEFGH")
        assertNull(model)
    }

    @Test
    fun getByIdentityExisting() {
        val identity = randomIdentity()
        val publicKey = nonSecureRandomArray(32)

        // Create contact using "old model"
        databaseService.contactModelFactory.createOrUpdate(ContactModel(identity, publicKey))

        // Fetch contact using "new model"
        val model = contactModelRepository.getByIdentity(identity)
        assertNotNull(model!!)
        assertTrue { model.identity == identity }
        assertTrue { model.data.value?.identity == identity }
        assertContentEquals(publicKey, model.data.value?.publicKey)
    }

    @Test
    fun deleteByIdentityNonExisting() {
        // If model does not exist, no exception is thrown
        contactModelRepository.deleteByIdentity("ABCDEFGH")
        contactModelRepository.deleteByIdentity("ABCDEFGH")
    }

    @Test
    fun deleteByIdentityExistingNotCached() {
        // Create contact using "old model"
        val identity = randomIdentity()
        databaseService.contactModelFactory.createOrUpdate(ContactModel(identity, nonSecureRandomArray(32)))

        // Delete through repository
        contactModelRepository.deleteByIdentity(identity)

        // Ensure that contact is gone
        val model = contactModelRepository.getByIdentity(identity)
        assertNull(model)
    }

    @Test
    fun deleteByIdentityExistingCached() {
        // Create contact using "old model"
        val identity = randomIdentity()
        databaseService.contactModelFactory.createOrUpdate(ContactModel(identity, nonSecureRandomArray(32)))

        // Fetch model to ensure it's cached
        val modelBeforeDeletion = contactModelRepository.getByIdentity(identity)
        assertNotNull(modelBeforeDeletion)

        // Delete through repository
        contactModelRepository.deleteByIdentity(identity)

        // Ensure that contact is gone
        val modelAfterDeletion = contactModelRepository.getByIdentity(identity)
        assertNull(modelAfterDeletion)
    }

    @Test
    fun deleteExisting() {
        // Create contact using "old model"
        val identity = randomIdentity()
        databaseService.contactModelFactory.createOrUpdate(ContactModel(identity, nonSecureRandomArray(32)))

        // Fetch model
        val model = contactModelRepository.getByIdentity(identity)
        assertNotNull(model!!)

        // Data is present, mutating model is possible
        assertNotNull(model.data.value)
        model.setNicknameFromSync("testnick")

        // Delete through repository
        contactModelRepository.delete(model)

        // Data is gone, mutating model throws exception
        assertNull(model.data.value)
        assertFailsWith(ModelDeletedException::class) {
            model.setNicknameFromSync("testnick")
        }

        // Ensure that contact is not cached anymore
        val modelAfterDeletion = contactModelRepository.getByIdentity(identity)
        assertNull(modelAfterDeletion)
    }

    private fun testCreateFromLocalOrRemote(
        initialValues: InitialValues,
        triggerSource: TestTriggerSource,
    ) {
        assertNull(contactModelRepository.getByIdentity(initialValues.identity))

        val newModel = runBlocking {
            when (triggerSource) {
                TestTriggerSource.FROM_LOCAL -> contactModelRepository.createFromLocal(
                    initialValues.identity,
                    initialValues.publicKey,
                    initialValues.date,
                    initialValues.identityType,
                    initialValues.acquaintanceLevel,
                    initialValues.activityState,
                    initialValues.featureMask,
                )

                TestTriggerSource.FROM_REMOTE -> contactModelRepository.createFromRemote(
                    initialValues.identity,
                    initialValues.publicKey,
                    initialValues.date,
                    initialValues.identityType,
                    initialValues.acquaintanceLevel,
                    initialValues.activityState,
                    initialValues.featureMask,
                )

            }
        }

        // TODO(ANDR-3003): Test that transaction has been executed

        val queriedModel = contactModelRepository.getByIdentity(initialValues.identity)
        assertEquals(newModel, queriedModel)

        assertDefaultValues(newModel, initialValues)

        contactModelRepository.deleteByIdentity(initialValues.identity)

        assertNull(contactModelRepository.getByIdentity(initialValues.identity))
    }

    private fun testCreateFromLocalOrRemoteTwice(
        initialValues: InitialValues,
        triggerSource: TestTriggerSource,
    ) {
        assertNull(contactModelRepository.getByIdentity(initialValues.identity))

        val runCreation = when (triggerSource) {
            TestTriggerSource.FROM_LOCAL -> suspend {
                contactModelRepository.createFromLocal(
                    initialValues.identity,
                    initialValues.publicKey,
                    initialValues.date,
                    initialValues.identityType,
                    initialValues.acquaintanceLevel,
                    initialValues.activityState,
                    initialValues.featureMask,
                )
            }

            TestTriggerSource.FROM_REMOTE -> suspend {
                contactModelRepository.createFromRemote(
                    initialValues.identity,
                    initialValues.publicKey,
                    initialValues.date,
                    initialValues.identityType,
                    initialValues.acquaintanceLevel,
                    initialValues.activityState,
                    initialValues.featureMask,
                )
            }

        }

        // Insert it for the first time
        val newModel = runBlocking {
            runCreation()
        }

        // TODO(ANDR-3003): Test that transaction has been executed

        val queriedModel = contactModelRepository.getByIdentity(initialValues.identity)
        assertEquals(newModel, queriedModel)

        assertDefaultValues(newModel, initialValues)

        // Insert for the second time and assert that an exception is thrown
        assertThrows(ContactCreateException::class.java) { runBlocking { runCreation() } }

        contactModelRepository.deleteByIdentity(initialValues.identity)

        assertNull(contactModelRepository.getByIdentity(initialValues.identity))
    }

    private data class InitialValues(
        val identity: String = "ABCDEFGH",
        val publicKey: ByteArray = ByteArray(NaCl.PUBLICKEYBYTES),
        val date: Date = Date(),
        val identityType: IdentityType = IdentityType.NORMAL,
        val acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
        val activityState: State = State.ACTIVE,
        val featureMask: ULong = 4.toULong(),
    )

    private fun assertDefaultValues(
        contactModel: ch.threema.data.models.ContactModel,
        initialValues: InitialValues,
    ) {
        assertEquals(initialValues.identity, contactModel.identity)
        val data = contactModel.data.value!!

        // Assert that the given properties match
        assertArrayEquals(initialValues.publicKey, data.publicKey)
        assertEquals(initialValues.date.time, data.createdAt.time)
        assertEquals(initialValues.identityType, data.identityType)
        assertEquals(initialValues.acquaintanceLevel, data.acquaintanceLevel)
        assertEquals(initialValues.activityState, data.activityState)
        assertEquals(initialValues.featureMask, data.featureMask)

        // Assert that the rest is set to the default values
        assertEquals("", data.firstName)
        assertEquals("", data.lastName)
        assertNull(data.nickname)
        assertEquals(VerificationLevel.UNVERIFIED, data.verificationLevel)
        assertEquals(WorkVerificationLevel.NONE, data.workVerificationLevel)
        assertEquals(ContactSyncState.INITIAL, data.syncState)
        assertEquals(ReadReceiptPolicy.DEFAULT, data.readReceiptPolicy)
        assertEquals(TypingIndicatorPolicy.DEFAULT, data.typingIndicatorPolicy)
        assertNull(data.androidContactLookupKey)
        assertNull(data.localAvatarExpires)
        assertFalse(data.isRestored)
        assertNull(data.profilePictureBlobId)
        assertEquals(
            ContactModel(data.identity, data.publicKey).idColorIndex.toUByte(),
            data.colorIndex
        )
    }
}

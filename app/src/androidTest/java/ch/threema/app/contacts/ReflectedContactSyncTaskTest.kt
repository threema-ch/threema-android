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

import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.processors.reflectedd2dsync.ReflectedContactSyncTask
import ch.threema.app.utils.AppVersionProvider
import ch.threema.base.crypto.NaCl
import ch.threema.data.TestDatabaseService
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModel
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
import ch.threema.protobuf.d2d.ContactSyncKt.create
import ch.threema.protobuf.d2d.ContactSyncKt.update
import ch.threema.protobuf.d2d.contactSync
import ch.threema.protobuf.d2d.sync.ContactKt.notificationSoundPolicyOverride
import ch.threema.protobuf.d2d.sync.ContactKt.readReceiptPolicyOverride
import ch.threema.protobuf.d2d.sync.ContactKt.typingIndicatorPolicyOverride
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.ActivityState
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.ReadReceiptPolicyOverride
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.TypingIndicatorPolicyOverride
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.protobuf.unit
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import com.google.protobuf.kotlin.toByteString
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReflectedContactSyncTaskTest {
    private lateinit var databaseService: TestDatabaseService
    private lateinit var taskCodec: TransactionAckTaskCodec
    private lateinit var coreServiceManager: TestCoreServiceManager
    private lateinit var contactModelRepository: ContactModelRepository

    private val initialContactModelData = ContactModelData(
        identity = "01234567",
        publicKey = ByteArray(32),
        createdAt = Date(),
        firstName = "",
        lastName = "",
        nickname = "Nick",
        idColor = IdColor.ofIdentity("01234567"),
        verificationLevel = VerificationLevel.UNVERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        identityType = IdentityType.NORMAL,
        acquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState = IdentityState.ACTIVE,
        syncState = ContactSyncState.INITIAL,
        featureMask = 511u,
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
        taskCodec = TransactionAckTaskCodec()
        val serviceManager = ThreemaApplication.requireServiceManager()
        coreServiceManager = TestCoreServiceManager(
            version = AppVersionProvider.appVersion,
            databaseService = databaseService,
            preferenceStore = serviceManager.preferenceStore,
            encryptedPreferenceStore = serviceManager.encryptedPreferenceStore,
            multiDeviceManager = TestMultiDeviceManager(
                isMultiDeviceActive = true,
                isMdDisabledOrSupportsFs = false,
            ),
            taskManager = TestTaskManager(taskCodec),
        )
        contactModelRepository = ModelRepositories(coreServiceManager).contacts
    }

    @Test
    fun testNewReflectedContact() {
        val contact = contact {
            identity = "01234567"
            publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES) { it.toByte() }.toByteString()
            createdAt = Date().time
            firstName = "0123"
            // No last name provided
            nickname = "nick"
            verificationLevel = MdD2DSync.Contact.VerificationLevel.FULLY_VERIFIED
            workVerificationLevel = MdD2DSync.Contact.WorkVerificationLevel.NONE
            identityType = MdD2DSync.Contact.IdentityType.WORK
            acquaintanceLevel = MdD2DSync.Contact.AcquaintanceLevel.DIRECT
            activityState = ActivityState.INACTIVE
            featureMask = 123
            syncState = MdD2DSync.Contact.SyncState.IMPORTED
            readReceiptPolicyOverride = readReceiptPolicyOverride {
                default = unit {}
            }
            typingIndicatorPolicyOverride = typingIndicatorPolicyOverride {
                policy = MdD2DSync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
            }
            notificationTriggerPolicyOverride =
                MdD2DSync.Contact.NotificationTriggerPolicyOverride.getDefaultInstance()
            notificationSoundPolicyOverride = notificationSoundPolicyOverride {
                policy = MdD2DSync.NotificationSoundPolicy.MUTED
            }
            conversationCategory = MdD2DSync.ConversationCategory.DEFAULT
            conversationVisibility = MdD2DSync.ConversationVisibility.NORMAL
        }

        testReflectedContactCreate(contact) { contactModel ->
            val data = contactModel.data!!
            assertEquals(contact.identity, data.identity)
            assertContentEquals(ByteArray(NaCl.PUBLIC_KEY_BYTES) { it.toByte() }, data.publicKey)
            assertEquals(contact.createdAt, data.createdAt.time)
            assertEquals(contact.firstName, data.firstName)
            assertEquals("", data.lastName)
            assertEquals(contact.nickname, data.nickname)
            assertEquals(contact.verificationLevel.convert(), data.verificationLevel)
            assertEquals(contact.workVerificationLevel.convert(), data.workVerificationLevel)
            assertEquals(contact.identityType.convert(), data.identityType)
            assertEquals(contact.acquaintanceLevel.convert(), data.acquaintanceLevel)
            assertEquals(contact.activityState.convert(), data.activityState)
            assertEquals(contact.featureMask, data.featureMask.toLong())
            assertEquals(contact.syncState.convert(), data.syncState)
            assertEquals(contact.readReceiptPolicyOverride.convert(), data.readReceiptPolicy)
            assertEquals(
                contact.typingIndicatorPolicyOverride.convert(),
                data.typingIndicatorPolicy,
            )
        }
    }

    @Test
    fun testReflectedNicknameChange() {
        val newNickname = "new nickname"
        testReflectedContactUpdate(
            contact {
                identity = "01234567"
                nickname = newNickname
            },
        ) { contactModel ->
            assertEquals(newNickname, contactModel.data?.nickname)
        }
    }

    private fun testReflectedContactCreate(
        contact: MdD2DSync.Contact,
        assertContactCreated: (contactModel: ContactModel) -> Unit,
    ) {
        assertNull(contactModelRepository.getByIdentity(contact.identity))

        ReflectedContactSyncTask(
            contact.toContactSyncCreate(),
            contactModelRepository,
            ThreemaApplication.requireServiceManager(),
        ).run()

        // Assert that no transaction have been executed
        assertZeroTransactionCount()

        // Assert that no messages have been sent
        assertNoMessagesSent()

        // Assert that the create has been applied
        val contactModel = contactModelRepository.getByIdentity(contact.identity)!!
        assertContactCreated(contactModel)
    }

    private fun testReflectedContactUpdate(
        contact: MdD2DSync.Contact,
        assertUpdateApplied: (contactModel: ContactModel) -> Unit,
    ) {
        createContact(initialContactModelData.copy(identity = contact.identity))

        ReflectedContactSyncTask(
            contact.toContactSyncUpdate(),
            contactModelRepository,
            ThreemaApplication.requireServiceManager(),
        ).run()

        // Assert that no transaction have been executed
        assertZeroTransactionCount()

        // Assert that no messages have been sent
        assertNoMessagesSent()

        // Assert that update has been applied
        val contactModel = contactModelRepository.getByIdentity(contact.identity)!!
        assertUpdateApplied(contactModel)
    }

    private fun createContact(contactModelData: ContactModelData) {
        runBlocking {
            contactModelRepository.createFromLocal(contactModelData)
        }
        assertAndClearOneTransactionCount()
    }

    private fun assertAndClearOneTransactionCount() {
        assertEquals(1, taskCodec.transactionBeginCount)
        assertEquals(1, taskCodec.transactionCommitCount)
        // Assert that there are 3 outbound messages (transaction begin, reflect, and commit)
        assertEquals(3, taskCodec.outboundMessages.size)

        taskCodec.transactionBeginCount = 0
        taskCodec.transactionCommitCount = 0
        taskCodec.outboundMessages.clear()
    }

    private fun assertZeroTransactionCount() {
        assertEquals(0, taskCodec.transactionBeginCount)
        assertEquals(0, taskCodec.transactionCommitCount)
    }

    private fun assertNoMessagesSent() {
        assertTrue { taskCodec.outboundMessages.isEmpty() }
    }

    private fun MdD2DSync.Contact.toContactSyncCreate() = contactSync {
        create = create {
            contact = this@toContactSyncCreate
        }
    }

    private fun MdD2DSync.Contact.toContactSyncUpdate() = contactSync {
        update = update {
            contact = this@toContactSyncUpdate
        }
    }

    private fun MdD2DSync.Contact.VerificationLevel.convert(): VerificationLevel = when (this) {
        MdD2DSync.Contact.VerificationLevel.FULLY_VERIFIED -> VerificationLevel.FULLY_VERIFIED
        MdD2DSync.Contact.VerificationLevel.SERVER_VERIFIED -> VerificationLevel.SERVER_VERIFIED
        MdD2DSync.Contact.VerificationLevel.UNVERIFIED -> VerificationLevel.UNVERIFIED
        MdD2DSync.Contact.VerificationLevel.UNRECOGNIZED -> fail("Verification level is unrecognized")
    }

    private fun MdD2DSync.Contact.WorkVerificationLevel.convert(): WorkVerificationLevel =
        when (this) {
            MdD2DSync.Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
            MdD2DSync.Contact.WorkVerificationLevel.NONE -> WorkVerificationLevel.NONE
            MdD2DSync.Contact.WorkVerificationLevel.UNRECOGNIZED -> fail("Work verification level is unrecognized")
        }

    private fun MdD2DSync.Contact.IdentityType.convert(): IdentityType = when (this) {
        MdD2DSync.Contact.IdentityType.REGULAR -> IdentityType.NORMAL
        MdD2DSync.Contact.IdentityType.WORK -> IdentityType.WORK
        MdD2DSync.Contact.IdentityType.UNRECOGNIZED -> fail("Identity type is unrecognized")
    }

    private fun MdD2DSync.Contact.AcquaintanceLevel.convert(): AcquaintanceLevel =
        when (this) {
            MdD2DSync.Contact.AcquaintanceLevel.DIRECT -> AcquaintanceLevel.DIRECT
            MdD2DSync.Contact.AcquaintanceLevel.GROUP_OR_DELETED -> AcquaintanceLevel.GROUP
            MdD2DSync.Contact.AcquaintanceLevel.UNRECOGNIZED -> fail("Acquaintance level is unrecognized")
        }

    private fun ActivityState.convert(): IdentityState = when (this) {
        ActivityState.ACTIVE -> IdentityState.ACTIVE
        ActivityState.INACTIVE -> IdentityState.INACTIVE
        ActivityState.INVALID -> IdentityState.INVALID
        ActivityState.UNRECOGNIZED -> fail("Activity state is unrecognized")
    }

    private fun MdD2DSync.Contact.SyncState.convert(): ContactSyncState = when (this) {
        MdD2DSync.Contact.SyncState.INITIAL -> ContactSyncState.INITIAL
        MdD2DSync.Contact.SyncState.IMPORTED -> ContactSyncState.IMPORTED
        MdD2DSync.Contact.SyncState.CUSTOM -> ContactSyncState.CUSTOM
        MdD2DSync.Contact.SyncState.UNRECOGNIZED -> fail("Sync state is unrecognized")
    }

    private fun ReadReceiptPolicyOverride.convert(): ReadReceiptPolicy = when (overrideCase) {
        ReadReceiptPolicyOverride.OverrideCase.DEFAULT -> ReadReceiptPolicy.DEFAULT
        ReadReceiptPolicyOverride.OverrideCase.POLICY -> when (policy) {
            MdD2DSync.ReadReceiptPolicy.SEND_READ_RECEIPT -> ReadReceiptPolicy.SEND
            MdD2DSync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT -> ReadReceiptPolicy.DONT_SEND
            MdD2DSync.ReadReceiptPolicy.UNRECOGNIZED -> fail("Read receipt policy is unrecognized")
            null -> fail("Read receipt policy is null")
        }

        ReadReceiptPolicyOverride.OverrideCase.OVERRIDE_NOT_SET -> fail("Read receipt policy override not set")
        null -> fail("Read receipt policy override is null")
    }

    private fun TypingIndicatorPolicyOverride.convert(): TypingIndicatorPolicy =
        when (overrideCase) {
            TypingIndicatorPolicyOverride.OverrideCase.DEFAULT -> TypingIndicatorPolicy.DEFAULT
            TypingIndicatorPolicyOverride.OverrideCase.POLICY -> when (policy) {
                MdD2DSync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.SEND
                MdD2DSync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.DONT_SEND
                MdD2DSync.TypingIndicatorPolicy.UNRECOGNIZED -> fail("Typing indicator policy is unrecognized")
                null -> fail("Typing indicator policy is null")
            }

            TypingIndicatorPolicyOverride.OverrideCase.OVERRIDE_NOT_SET -> fail("Typing indicator policy override not set")
            null -> fail("Typing indicator policy override is null")
        }
}

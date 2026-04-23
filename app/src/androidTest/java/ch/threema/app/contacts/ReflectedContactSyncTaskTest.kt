package ch.threema.app.contacts

import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.threema.KoinTestRule
import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.processors.reflectedd2dsync.ReflectedContactSyncTask
import ch.threema.app.stores.IdentityProvider
import ch.threema.base.crypto.NaCl
import ch.threema.data.datatypes.AvailabilityStatus
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
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.ContactSyncKt.create
import ch.threema.protobuf.d2d.ContactSyncKt.update
import ch.threema.protobuf.d2d.contactSync
import ch.threema.protobuf.d2d.sync.Contact
import ch.threema.protobuf.d2d.sync.ContactKt.deprecatedNotificationSoundPolicyOverride
import ch.threema.protobuf.d2d.sync.ContactKt.readReceiptPolicyOverride
import ch.threema.protobuf.d2d.sync.ContactKt.typingIndicatorPolicyOverride
import ch.threema.protobuf.d2d.sync.ConversationCategory
import ch.threema.protobuf.d2d.sync.ConversationVisibility
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.storage.TestDatabaseProvider
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import com.google.protobuf.kotlin.toByteString
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class ReflectedContactSyncTaskTest {
    private lateinit var databaseProvider: TestDatabaseProvider
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

    private val instrumentedTestModule = module {
        factory<MultiDeviceManager> { coreServiceManager.multiDeviceManager }
    }

    @get:Rule
    val koinTestRule = KoinTestRule(
        modules = listOf(instrumentedTestModule),
    )

    @BeforeTest
    fun before() {
        databaseProvider = TestDatabaseProvider()
        taskCodec = TransactionAckTaskCodec()
        val myIdentity = "00000000"
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
            multiDeviceManager = TestMultiDeviceManager(
                isMultiDeviceActive = true,
                isMdDisabledOrSupportsFs = false,
            ),
            taskManager = TestTaskManager(taskCodec),
            identityStore = identityStoreMock,
        )
        contactModelRepository = ModelRepositories(
            coreServiceManager = coreServiceManager,
            identityProvider = identityProviderMock,
        ).contacts
    }

    @Test
    fun testNewReflectedContact() = runTest {
        val contact = contact {
            identity = "01234567"
            publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES) { it.toByte() }.toByteString()
            createdAt = Date().time
            firstName = "0123"
            // No last name provided
            nickname = "nick"
            verificationLevel = Contact.VerificationLevel.FULLY_VERIFIED
            workVerificationLevel = Contact.WorkVerificationLevel.NONE
            identityType = Contact.IdentityType.WORK
            acquaintanceLevel = Contact.AcquaintanceLevel.DIRECT
            activityState = Contact.ActivityState.INACTIVE
            featureMask = 123
            syncState = Contact.SyncState.IMPORTED
            readReceiptPolicyOverride = readReceiptPolicyOverride {
                default = unit {}
            }
            typingIndicatorPolicyOverride = typingIndicatorPolicyOverride {
                policy = ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
            }
            notificationTriggerPolicyOverride = Contact.NotificationTriggerPolicyOverride.getDefaultInstance()
            deprecatedNotificationSoundPolicyOverride = deprecatedNotificationSoundPolicyOverride {
                default = unit {}
            }
            conversationCategory = ConversationCategory.DEFAULT
            conversationVisibility = ConversationVisibility.NORMAL
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
    fun testReflectedNicknameChange() = runTest {
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

    private suspend fun testReflectedContactCreate(
        contact: Contact,
        assertContactCreated: (contactModel: ContactModel) -> Unit,
    ) {
        assertNull(contactModelRepository.getByIdentity(contact.identity))

        ReflectedContactSyncTask(
            contactSync = contact.toContactSyncCreate(),
            contactModelRepository = contactModelRepository,
            serviceManager = ThreemaApplication.requireServiceManager(),
        ).run()

        // Assert that no transaction have been executed
        assertZeroTransactionCount()

        // Assert that no messages have been sent
        assertNoMessagesSent()

        // Assert that the create has been applied
        val contactModel = contactModelRepository.getByIdentity(contact.identity)!!
        assertContactCreated(contactModel)
    }

    private suspend fun testReflectedContactUpdate(
        contact: Contact,
        assertUpdateApplied: (contactModel: ContactModel) -> Unit,
    ) {
        createContact(initialContactModelData.copy(identity = contact.identity))

        ReflectedContactSyncTask(
            contactSync = contact.toContactSyncUpdate(),
            contactModelRepository = contactModelRepository,
            serviceManager = ThreemaApplication.requireServiceManager(),
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

    private fun Contact.toContactSyncCreate() = contactSync {
        create = create {
            contact = this@toContactSyncCreate
        }
    }

    private fun Contact.toContactSyncUpdate() = contactSync {
        update = update {
            contact = this@toContactSyncUpdate
        }
    }

    private fun Contact.VerificationLevel.convert(): VerificationLevel = when (this) {
        Contact.VerificationLevel.FULLY_VERIFIED -> VerificationLevel.FULLY_VERIFIED
        Contact.VerificationLevel.SERVER_VERIFIED -> VerificationLevel.SERVER_VERIFIED
        Contact.VerificationLevel.UNVERIFIED -> VerificationLevel.UNVERIFIED
        Contact.VerificationLevel.UNRECOGNIZED -> fail("Verification level is unrecognized")
    }

    private fun Contact.WorkVerificationLevel.convert(): WorkVerificationLevel =
        when (this) {
            Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
            Contact.WorkVerificationLevel.NONE -> WorkVerificationLevel.NONE
            Contact.WorkVerificationLevel.UNRECOGNIZED -> fail("Work verification level is unrecognized")
        }

    private fun Contact.IdentityType.convert(): IdentityType = when (this) {
        Contact.IdentityType.REGULAR -> IdentityType.NORMAL
        Contact.IdentityType.WORK -> IdentityType.WORK
        Contact.IdentityType.UNRECOGNIZED -> fail("Identity type is unrecognized")
    }

    private fun Contact.AcquaintanceLevel.convert(): AcquaintanceLevel =
        when (this) {
            Contact.AcquaintanceLevel.DIRECT -> AcquaintanceLevel.DIRECT
            Contact.AcquaintanceLevel.GROUP_OR_DELETED -> AcquaintanceLevel.GROUP
            Contact.AcquaintanceLevel.UNRECOGNIZED -> fail("Acquaintance level is unrecognized")
        }

    private fun Contact.ActivityState.convert(): IdentityState = when (this) {
        Contact.ActivityState.ACTIVE -> IdentityState.ACTIVE
        Contact.ActivityState.INACTIVE -> IdentityState.INACTIVE
        Contact.ActivityState.INVALID -> IdentityState.INVALID
        Contact.ActivityState.UNRECOGNIZED -> fail("Activity state is unrecognized")
    }

    private fun Contact.SyncState.convert(): ContactSyncState = when (this) {
        Contact.SyncState.INITIAL -> ContactSyncState.INITIAL
        Contact.SyncState.IMPORTED -> ContactSyncState.IMPORTED
        Contact.SyncState.CUSTOM -> ContactSyncState.CUSTOM
        Contact.SyncState.UNRECOGNIZED -> fail("Sync state is unrecognized")
    }

    private fun Contact.ReadReceiptPolicyOverride.convert(): ReadReceiptPolicy = when (overrideCase) {
        Contact.ReadReceiptPolicyOverride.OverrideCase.DEFAULT -> ReadReceiptPolicy.DEFAULT
        Contact.ReadReceiptPolicyOverride.OverrideCase.POLICY -> when (policy) {
            ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.SEND_READ_RECEIPT -> ReadReceiptPolicy.SEND
            ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT -> ReadReceiptPolicy.DONT_SEND
            ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.UNRECOGNIZED -> fail("Read receipt policy is unrecognized")
            null -> fail("Read receipt policy is null")
        }

        Contact.ReadReceiptPolicyOverride.OverrideCase.OVERRIDE_NOT_SET -> fail("Read receipt policy override not set")
        null -> fail("Read receipt policy override is null")
    }

    private fun Contact.TypingIndicatorPolicyOverride.convert(): TypingIndicatorPolicy =
        when (overrideCase) {
            Contact.TypingIndicatorPolicyOverride.OverrideCase.DEFAULT -> TypingIndicatorPolicy.DEFAULT
            Contact.TypingIndicatorPolicyOverride.OverrideCase.POLICY -> when (policy) {
                ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.SEND
                ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.DONT_SEND
                ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.UNRECOGNIZED -> fail("Typing indicator policy is unrecognized")
                null -> fail("Typing indicator policy is null")
            }

            Contact.TypingIndicatorPolicyOverride.OverrideCase.OVERRIDE_NOT_SET -> fail("Typing indicator policy override not set")
            null -> fail("Typing indicator policy override is null")
        }
}

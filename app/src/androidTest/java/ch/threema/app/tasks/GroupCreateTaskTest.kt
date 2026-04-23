package ch.threema.app.tasks

import ch.threema.KoinTestRule
import ch.threema.app.DangerousTest
import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.di.modules.sessionScopedModule
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.protocolsteps.ExpectedProfilePictureChange
import ch.threema.app.protocolsteps.PredefinedMessageIds
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.clearDatabaseAndCaches
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupCreateException
import ch.threema.domain.helpers.TransactionAckTaskCodec
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.UserState
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.storage.models.ContactModel
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.koin.dsl.module

@DangerousTest
class GroupCreateTaskTest {
    private val myContact: TestContact = TestHelpers.TEST_CONTACT

    private val initialContactModelData = ContactModelData(
        identity = "12345678",
        publicKey = ByteArray(32),
        createdAt = Date(),
        firstName = "",
        lastName = "",
        verificationLevel = VerificationLevel.SERVER_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        nickname = null,
        identityType = IdentityType.NORMAL,
        acquaintanceLevel = ContactModel.AcquaintanceLevel.DIRECT,
        activityState = IdentityState.ACTIVE,
        syncState = ContactSyncState.INITIAL,
        featureMask = 255u,
        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        isArchived = false,
        profilePictureBlobId = null,
        androidContactLookupInfo = null,
        localAvatarExpires = null,
        isRestored = false,
        jobTitle = null,
        department = null,
        notificationTriggerPolicyOverride = null,
        availabilityStatus = AvailabilityStatus.None,
        workLastFullSyncAt = null,
    )

    private val serviceManager by lazy { ThreemaApplication.requireServiceManager() }

    private var isMultiDeviceEnabled = false

    private val testMultiDeviceManagerMdEnabled by lazy {
        TestMultiDeviceManager(
            isMdDisabledOrSupportsFs = false,
            isMultiDeviceActive = true,
        )
    }

    private val testMultiDeviceManagerMdDisabled by lazy {
        TestMultiDeviceManager(
            isMdDisabledOrSupportsFs = true,
            isMultiDeviceActive = false,
        )
    }

    private val instrumentedTestModule = module {
        factory<MultiDeviceManager> {
            if (isMultiDeviceEnabled) {
                testMultiDeviceManagerMdEnabled
            } else {
                testMultiDeviceManagerMdDisabled
            }
        }
    }

    @get:Rule
    val koinTestRule = KoinTestRule(
        modules = listOf(sessionScopedModule, instrumentedTestModule),
    )

    @BeforeTest
    fun setup() {
        clearDatabaseAndCaches(serviceManager)

        assert(myContact.identity == TestHelpers.ensureIdentity(serviceManager))

        // Note that we use from sync to prevent any reflection. This is only acceptable in tests.
        serviceManager.modelRepositories.contacts.createFromSync(initialContactModelData)

        // Note that we use from sync to prevent any reflection. This is only acceptable in tests.
        try {
            val now = Date()
            serviceManager.modelRepositories.groups.createFromSync(
                GroupModelData(
                    groupIdentity = GroupIdentity(myContact.identity, 42),
                    name = "My Group",
                    createdAt = now,
                    synchronizedAt = null,
                    lastUpdate = now,
                    isArchived = false,
                    userState = UserState.MEMBER,
                    otherMembers = setOf(initialContactModelData.identity),
                    groupDescription = null,
                    groupDescriptionChangedAt = null,
                    notificationTriggerPolicyOverride = null,
                ),
            )
        } catch (_: GroupCreateException) {
            // Ignore
        }
    }

    @Test
    fun testSimpleGroupMd() = runTest {
        enableMultiDevice()

        val predefinedMessageIds = PredefinedMessageIds.random()
        val groupCreateTask = GroupCreateTask(
            name = "My Group",
            expectedProfilePictureChange = ExpectedProfilePictureChange.Remove,
            members = setOf(initialContactModelData.identity),
            groupIdentity = GroupIdentity(myContact.identity, 42),
            predefinedMessageIds = predefinedMessageIds,
        )

        val handle = TransactionAckTaskCodec()
        groupCreateTask.invoke(handle)
        assertEquals(1, handle.transactionBeginCount)
        assertEquals(1, handle.transactionCommitCount)

        handle.outboundMessages.apply {
            // The transaction start
            assertIs<OutboundD2mMessage.BeginTransaction>(get(0))

            // The reflected csp messages
            assertIs<OutboundD2mMessage.Reflect>(get(1))
            assertIs<OutboundD2mMessage.Reflect>(get(2))
            assertIs<OutboundD2mMessage.Reflect>(get(3))

            // The sent csp messages
            assertIs<CspMessage>(get(4))
            assertIs<CspMessage>(get(5))
            assertIs<CspMessage>(get(6))

            // The transaction end
            assertIs<OutboundD2mMessage.CommitTransaction>(get(7))

            // Assert that there are no more messages
            assertEquals(8, size)
        }
    }

    @Test
    fun testSimpleGroupNonMd() = runTest {
        disableMultiDevice()

        val predefinedMessageIds = PredefinedMessageIds.random()
        val groupCreateTask = GroupCreateTask(
            name = "My Group",
            expectedProfilePictureChange = ExpectedProfilePictureChange.Remove,
            members = setOf("12345678"),
            groupIdentity = GroupIdentity(myContact.identity, 42),
            predefinedMessageIds = predefinedMessageIds,
        )

        val handle = TransactionAckTaskCodec()
        groupCreateTask.invoke(handle)
        assertEquals(0, handle.transactionBeginCount)
        assertEquals(0, handle.transactionCommitCount)

        handle.outboundMessages.apply {
            assertEquals(6, size)
            // Empty message and group setup message
            assertIs<CspMessage>(get(0))
            assertIs<CspMessage>(get(1))

            // Empty message and group name message
            assertIs<CspMessage>(get(2))
            assertIs<CspMessage>(get(3))

            // Empty message and group delete profile picture message
            assertIs<CspMessage>(get(4))
            assertIs<CspMessage>(get(5))
        }
    }

    private fun enableMultiDevice() {
        isMultiDeviceEnabled = true
        serviceManager.forwardSecurityMessageProcessor.setForwardSecurityEnabled(false)
    }

    private fun disableMultiDevice() {
        isMultiDeviceEnabled = false
        serviceManager.forwardSecurityMessageProcessor.setForwardSecurityEnabled(true)
    }
}

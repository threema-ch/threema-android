package ch.threema.app.protocol

import android.os.Build
import ch.threema.app.DangerousTest
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.protocolsteps.BlockState
import ch.threema.app.protocolsteps.IdentityBlockedSteps
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.GroupService
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.stores.ContactStore
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.group.GroupModelOld
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before

@DangerousTest
class IdentityBlockedStepsTest {
    private val knownContact = TestContact("12345678")
    private val unknownContact = TestContact("TESTTEST")
    private val specialContact = TestContact("*3MAPUSH")
    private val explicitlyBlockedContact = TestContact("23456789")
    private val inGroup = TestContact("ABCDEFGH")
    private val inNoGroup = TestContact("********")
    private val inLeftGroup = TestContact("--------")

    private lateinit var contactModelRepositoryMock: ContactModelRepository
    private lateinit var contactStoreMock: ContactStore
    private lateinit var groupServiceMock: GroupService
    private lateinit var blockedIdentitiesServiceMock: BlockedIdentitiesService
    private lateinit var noBlockUnknownSynchronizedSettingsServiceMock: SynchronizedSettingsService
    private lateinit var blockUnknownSynchronizedSettingsServiceMock: SynchronizedSettingsService

    @Before
    fun setup() {
        // Because mockk does not support mocking objects below android P, we skip this test in this case.
        assumeTrue(
            Build.VERSION.SDK_INT > Build.VERSION_CODES.P,
        )

        contactModelRepositoryMock = getContactModelRepositoryMock()
        contactStoreMock = getContactStoreMock()
        groupServiceMock = getGroupServiceMock()
        blockedIdentitiesServiceMock = mockk {
            every { isBlocked(any()) } answers {
                firstArg<String>() == explicitlyBlockedContact.identity
            }
        }
        noBlockUnknownSynchronizedSettingsServiceMock = mockk {
            every { isBlockUnknown() } returns false
        }
        blockUnknownSynchronizedSettingsServiceMock = mockk {
            every { isBlockUnknown() } returns true
        }
    }

    @Test
    fun testExplicitlyBlockedContact() {
        assertEquals(
            BlockState.EXPLICITLY_BLOCKED,
            runIdentityBlockedSteps(explicitlyBlockedContact.identity, noBlockUnknownSynchronizedSettingsServiceMock),
        )
        assertEquals(
            BlockState.EXPLICITLY_BLOCKED,
            runIdentityBlockedSteps(explicitlyBlockedContact.identity, blockUnknownSynchronizedSettingsServiceMock),
        )
    }

    @Test
    fun testImplicitlyBlockedContact() {
        assertEquals(
            BlockState.IMPLICITLY_BLOCKED,
            runIdentityBlockedSteps(unknownContact.identity, blockUnknownSynchronizedSettingsServiceMock),
        )
    }

    @Test
    fun testImplicitlyBlockedSpecialContact() {
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(specialContact.identity, blockUnknownSynchronizedSettingsServiceMock),
        )
    }

    @Test
    fun testGroupContactWithGroup() {
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(inGroup.identity, blockUnknownSynchronizedSettingsServiceMock),
        )
    }

    @Test
    fun testGroupContactWithoutGroup() {
        assertEquals(
            BlockState.IMPLICITLY_BLOCKED,
            runIdentityBlockedSteps(inNoGroup.identity, blockUnknownSynchronizedSettingsServiceMock),
        )
    }

    @Test
    fun testGroupContactWithLeftGroup() {
        assertEquals(
            BlockState.IMPLICITLY_BLOCKED,
            runIdentityBlockedSteps(inLeftGroup.identity, blockUnknownSynchronizedSettingsServiceMock),
        )
    }

    @Test
    fun testKnownContact() {
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(knownContact.identity, blockUnknownSynchronizedSettingsServiceMock),
        )
    }

    @Test
    fun testWithoutBlockingUnknown() {
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(knownContact.identity, noBlockUnknownSynchronizedSettingsServiceMock),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(unknownContact.identity, noBlockUnknownSynchronizedSettingsServiceMock),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(specialContact.identity, noBlockUnknownSynchronizedSettingsServiceMock),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(inGroup.identity, noBlockUnknownSynchronizedSettingsServiceMock),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(inNoGroup.identity, noBlockUnknownSynchronizedSettingsServiceMock),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(inLeftGroup.identity, noBlockUnknownSynchronizedSettingsServiceMock),
        )
    }

    private fun runIdentityBlockedSteps(identity: String, synchronizedSettingsService: SynchronizedSettingsService) =
        IdentityBlockedSteps(
            contactModelRepository = contactModelRepositoryMock,
            contactStore = contactStoreMock,
            groupService = groupServiceMock,
            blockedIdentitiesService = blockedIdentitiesServiceMock,
            synchronizedSettingsService = synchronizedSettingsService,
        ).run(identity = identity)

    private fun getContactModelRepositoryMock(): ContactModelRepository = mockk {
        every { getByIdentity(knownContact.identity) } returns getContactModelMock(knownContact, AcquaintanceLevel.DIRECT)

        every { getByIdentity(unknownContact.identity) } returns null

        every { getByIdentity(inGroup.identity) } returns getContactModelMock(inGroup, AcquaintanceLevel.GROUP)

        every { getByIdentity(inNoGroup.identity) } returns getContactModelMock(inNoGroup, AcquaintanceLevel.GROUP)

        every { getByIdentity(inLeftGroup.identity) } returns getContactModelMock(inLeftGroup, AcquaintanceLevel.GROUP)
    }

    private fun getContactStoreMock(): ContactStore = mockk {
        every { isSpecialContact(any()) } answers {
            firstArg<String>() == specialContact.identity
        }
    }

    private fun getGroupServiceMock(): GroupService = mockk {
        val groupModelMock: GroupModelOld = mockk()
        val leftGroupModelMock: GroupModelOld = mockk()

        every { getGroupsByIdentity(any()) } answers {
            when (firstArg<String>()) {
                inGroup.identity -> listOf(groupModelMock)
                inLeftGroup.identity -> listOf(leftGroupModelMock)
                else -> emptyList()
            }
        }

        every { isGroupMember(groupModelMock) } returns true
        every { isGroupMember(leftGroupModelMock) } returns false
    }

    private fun getContactModelMock(contact: TestContact, acquaintanceLevel: AcquaintanceLevel): ContactModel = mockk {
        val contactModelData = ContactModelData(
            identity = contact.identity,
            publicKey = contact.publicKey,
            createdAt = Date(),
            firstName = "",
            lastName = "",
            nickname = null,
            idColor = mockk(),
            verificationLevel = mockk(),
            workVerificationLevel = mockk(),
            identityType = mockk(),
            acquaintanceLevel = acquaintanceLevel,
            activityState = mockk(),
            syncState = mockk(),
            featureMask = 0u,
            readReceiptPolicy = mockk(),
            typingIndicatorPolicy = mockk(),
            isArchived = false,
            androidContactLookupInfo = null,
            localAvatarExpires = null,
            isRestored = false,
            profilePictureBlobId = null,
            jobTitle = null,
            department = null,
            notificationTriggerPolicyOverride = mockk(),
            availabilityStatus = mockk(),
            workLastFullSyncAt = null,
        )

        every { data } returns contactModelData
    }
}

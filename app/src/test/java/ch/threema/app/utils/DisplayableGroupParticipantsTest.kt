package ch.threema.app.utils

import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.models.GroupIdentity
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.IdentityState
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import testdata.TestData

class DisplayableGroupParticipantsTest {
    private val meMyselfAndI = "Me"
    private val userServiceMock: UserService = mockk {
        every { identity } returns TestData.Identities.ME.value
        every { displayName } returns meMyselfAndI
    }
    private val contactModelRepositoryMock: ContactModelRepository = mockk {
        every { getByIdentity(TestData.Identities.OTHER_1.value) } returns TestData.createContactModel(
            identity = TestData.Identities.OTHER_1,
            firstname = "First1",
            lastname = "Last1",
        )
        every { getByIdentity(TestData.Identities.OTHER_2.value) } returns TestData.createContactModel(
            identity = TestData.Identities.OTHER_2,
            firstname = "",
            lastname = "",
            nickname = "Nickname2",
        )
    }
    private val coreServiceManagerMock: CoreServiceManager = mockk(relaxed = true) {
        every { identityStore } returns mockk {
            every { getIdentityString() } returns userServiceMock.identity!!
        }
    }

    @Test
    fun `displayable participants can be created from empty group`() {
        val preferenceServiceMock: PreferenceService = mockk {
            every { getContactNameFormat() } returns ContactNameFormat.FIRSTNAME_LASTNAME
        }

        val groupModel = TestData.createGroupModel(
            groupIdentity = GroupIdentity(
                creatorIdentity = TestData.Identities.ME.value,
                groupId = 42,
            ),
            otherMembers = emptySet(),
            coreServiceManager = coreServiceManagerMock,
        )

        val displayableGroupParticipants = DisplayableGroupParticipants.getDisplayableGroupParticipantsOfGroup(
            groupModel = groupModel,
            contactModelRepository = contactModelRepositoryMock,
            userService = userServiceMock,
            preferenceService = preferenceServiceMock,
        )

        assertNotNull(displayableGroupParticipants)
        assertTrue { displayableGroupParticipants.membersWithoutCreator.isEmpty() }
        val displayableGroupCreator = displayableGroupParticipants.creator.displayableContactOrUser
        assertIs<DisplayableContactOrUser.User>(displayableGroupCreator)
        with(displayableGroupCreator) {
            assertEquals(TestData.Identities.ME.value, identity)
            assertEquals(meMyselfAndI, displayName)
            assertEquals(IdentityState.ACTIVE, identityState)
        }
    }

    @Test
    fun `displayable participants can be created from foreign group`() {
        val preferenceServiceMock: PreferenceService = mockk {
            every { getContactNameFormat() } returns ContactNameFormat.FIRSTNAME_LASTNAME
        }

        val groupModel = TestData.createGroupModel(
            groupIdentity = GroupIdentity(
                creatorIdentity = TestData.Identities.OTHER_1.value,
                groupId = 42,
            ),
            otherMembers = setOf(TestData.Identities.OTHER_2.value),
            coreServiceManager = coreServiceManagerMock,
        )

        val displayableGroupParticipants = DisplayableGroupParticipants.getDisplayableGroupParticipantsOfGroup(
            groupModel = groupModel,
            contactModelRepository = contactModelRepositoryMock,
            userService = userServiceMock,
            preferenceService = preferenceServiceMock,
        )

        assertNotNull(displayableGroupParticipants)
        val displayableGroupCreator = displayableGroupParticipants.creator.displayableContactOrUser
        assertIs<DisplayableContactOrUser.Contact>(displayableGroupCreator)
        with(displayableGroupCreator) {
            assertEquals(TestData.Identities.OTHER_1.value, identity)
            assertEquals("First1 Last1", displayName)
            assertEquals(IdentityState.ACTIVE, identityState)
        }

        assertEquals(2, displayableGroupParticipants.membersWithoutCreator.size)
        val otherMember = displayableGroupParticipants.membersWithoutCreator.find { displayableGroupParticipantMember ->
            displayableGroupParticipantMember.displayableContactOrUser.identity == TestData.Identities.OTHER_2.value
        }?.displayableContactOrUser
        assertNotNull(otherMember)
        assertIs<DisplayableContactOrUser.Contact>(otherMember)
        with(otherMember) {
            assertEquals(TestData.Identities.OTHER_2.value, identity)
            assertEquals("~Nickname2", displayName)
            assertEquals(IdentityState.ACTIVE, identityState)
        }
        val me = displayableGroupParticipants.membersWithoutCreator.find { displayableGroupParticipantMember ->
            displayableGroupParticipantMember.displayableContactOrUser.identity == TestData.Identities.ME.value
        }?.displayableContactOrUser
        assertNotNull(me)
        assertIs<DisplayableContactOrUser.User>(me)
        with(me) {
            assertEquals(TestData.Identities.ME.value, me.identity)
            assertEquals(meMyselfAndI, displayName)
            assertEquals(IdentityState.ACTIVE, identityState)
        }
    }

    @Test
    fun `displayable participants can be created from own group`() {
        val preferenceServiceMock: PreferenceService = mockk {
            every { getContactNameFormat() } returns ContactNameFormat.FIRSTNAME_LASTNAME
        }

        val groupModel = TestData.createGroupModel(
            groupIdentity = GroupIdentity(
                creatorIdentity = TestData.Identities.ME.value,
                groupId = 42,
            ),
            otherMembers = setOf(TestData.Identities.OTHER_2.value),
            coreServiceManager = coreServiceManagerMock,
        )

        val displayableGroupParticipants = DisplayableGroupParticipants.getDisplayableGroupParticipantsOfGroup(
            groupModel = groupModel,
            contactModelRepository = contactModelRepositoryMock,
            userService = userServiceMock,
            preferenceService = preferenceServiceMock,
        )

        assertNotNull(displayableGroupParticipants)

        val displayableGroupCreator = displayableGroupParticipants.creator.displayableContactOrUser
        assertIs<DisplayableContactOrUser.User>(displayableGroupCreator)
        with(displayableGroupCreator) {
            assertEquals(TestData.Identities.ME.value, identity)
            assertEquals(meMyselfAndI, displayName)
            assertEquals(IdentityState.ACTIVE, identityState)
        }

        assertEquals(1, displayableGroupParticipants.membersWithoutCreator.size)
        val displayableGroupMember = displayableGroupParticipants.membersWithoutCreator.first().displayableContactOrUser
        assertIs<DisplayableContactOrUser.Contact>(displayableGroupMember)
        with(displayableGroupMember) {
            assertEquals(TestData.Identities.OTHER_2.value, identity)
            assertEquals("~Nickname2", displayName)
            assertEquals(IdentityState.ACTIVE, identityState)
        }
    }
}

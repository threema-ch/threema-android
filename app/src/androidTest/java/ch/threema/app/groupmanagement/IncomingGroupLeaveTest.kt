package ch.threema.app.groupmanagement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ch.threema.app.DangerousTest
import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.types.IdentityString
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/**
 * Tests that incoming group leave messages are handled correctly.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupLeaveTest : GroupControlTest<GroupLeaveMessage>() {
    /**
     * Test that contact A leaving my group works as expected.
     */
    @Test
    fun testValidLeaveInMyGroup() = runTest {
        assertSuccessfulLeave(myGroup, contactA, true)
    }

    /**
     * Test that contact B leaving groupAB works as expected.
     */
    @Test
    fun testValidLeave() = runTest {
        assertSuccessfulLeave(groupAB, contactB)
    }

    /**
     * Test that a leave message of an unknown group (where I am the owner) is discarded (and does
     * not change anything).
     */
    @Test
    fun testLeaveOfMyNonExistingGroup() = runTest {
        assertUnsuccessfulLeave(myUnknownGroup, contactA, emptySet())
    }

    /**
     * Test that a leave message of an unknown group (where I am not the owner) is discarded and has
     * no effect.
     */
    @Test
    fun testLeaveOfNonExistingGroup() = runTest {
        assertUnsuccessfulLeave(groupAUnknown, contactB, emptySet(), true)
    }

    /**
     * Test that a leave message of a left group (where I am not a member anymore) is discarded (and
     * does not change anything).
     */
    @Test
    fun testLeaveOfLeftGroup() = runTest {
        assertUnsuccessfulLeave(groupALeft, contactB, null, true)
    }

    /**
     * Test that a leave message of a left group (where I am the owner) is discarded (and nothing is
     * changed).
     */
    @Test
    fun testLeaveOfMyLeftGroup() = runTest {
        assertUnsuccessfulLeave(myLeftGroup, contactA)
    }

    /**
     * Test that a leave message of a sender that is not part of the group is discarded and has no
     * effect.
     */
    @Test
    fun testLeaveOfNonMember() = runTest {
        assertUnsuccessfulLeave(
            group = groupA,
            contact = contactB,
        )
    }

    @AfterTest
    fun removeAllGroupListeners() {
        GroupLeaveTracker.stopAllListeners()
    }

    override fun createMessageForGroup() = GroupLeaveMessage()

    override fun testCommonGroupReceiveStepUnknownGroupUserCreator() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStepUnknownGroupUserNotCreator() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStepLeftGroupUserCreator() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStepLeftGroupUserNotCreator() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserCreator() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserNotCreator() {
        // The common group receive steps are not executed for group leave messages
    }

    private suspend fun assertSuccessfulLeave(
        group: TestGroup,
        contact: TestContact,
        expectStateChange: Boolean = false,
    ) {
        serviceManager.groupService.resetCache(group.groupModel.id)
        val groupIdentity = GroupIdentity(group.groupCreator.identity, group.apiGroupId.toLong())
        val previousMemberCount = serviceManager.groupService.countMembers(group.groupModel)

        assertEquals(
            group.members.map { it.identity } - myContact.identity,
            serviceManager.modelRepositories.groups.getByGroupIdentity(groupIdentity)?.data?.otherMembers?.toList(),
        )

        val leaveTracker = GroupLeaveTracker(group, contact.identity, expectStateChange)
            .apply { start() }

        // Process the group rename message
        processMessage(createEncryptedGroupLeaveMessage(group, contact), contact.identityStore)

        leaveTracker.assertMemberLeft()

        leaveTracker.stop()

        serviceManager.groupService.resetCache(group.groupModel.id)

        assertEquals(
            previousMemberCount - 1,
            serviceManager.groupService.countMembers(group.groupModel),
        )
        assertEquals(
            group.members.map { it.identity } - myContact.identity - contact.identity,
            serviceManager.modelRepositories.groups.getByGroupIdentity(groupIdentity)?.data?.otherMembers?.toList(),
        )

        // Assert that no message has been sent as a response to a group leave
        assertEquals(0, sentMessagesInsideTask.size)
    }

    private suspend fun assertUnsuccessfulLeave(
        group: TestGroup,
        contact: TestContact,
        expectedMembers: Set<String>? = null,
        shouldSendSyncRequest: Boolean = false,
    ) {
        val expectedMemberList = expectedMembers ?: (setOf(group.groupCreator.identity) + group.members.map { it.identity })

        serviceManager.groupService.resetCache(group.groupModel.id)

        assertGroupIdentities(expectedMemberList, group)
        assertMemberCount(expectedMemberList.size, group)

        val leaveTracker = GroupLeaveTracker(group, contact.identity).apply { start() }

        // Process the group rename message
        processMessage(createEncryptedGroupLeaveMessage(group, contact), contact.identityStore)

        leaveTracker.assertNoMemberLeft()

        leaveTracker.stop()

        serviceManager.groupService.resetCache(group.groupModel.id)

        assertGroupIdentities(expectedMemberList, group)
        assertMemberCount(expectedMemberList.size, group)

        if (shouldSendSyncRequest) {
            // Should send sync request to the group creator
            assertEquals(1, sentMessagesInsideTask.size)
            val sentMessage = sentMessagesInsideTask.first() as GroupSyncRequestMessage
            assertEquals(myContact.identity, sentMessage.fromIdentity)
            assertEquals(group.groupCreator.identity, sentMessage.toIdentity)
            assertEquals(group.apiGroupId, sentMessage.apiGroupId)
            assertEquals(group.groupCreator.identity, sentMessage.groupCreator)
        } else {
            // Assert that no message has been sent as a response to the leave message
            assertEquals(0, sentMessagesInsideTask.size)
        }
    }

    private fun createEncryptedGroupLeaveMessage(group: TestGroup, contact: TestContact) =
        createMessageForGroup().apply {
            groupCreator = group.groupCreator.identity
            apiGroupId = group.apiGroupId
            fromIdentity = contact.identity
            toIdentity = myContact.identity
        }

    private fun assertGroupIdentities(expectedMemberList: Set<String>, group: TestGroup) {
        if (serviceManager.groupService.getByApiGroupIdAndCreator(
                group.apiGroupId,
                group.groupCreator.identity,
            ) != null
        ) {
            // We check the expected members if the group is available in the database. If there is
            // no such group, we do not need to perform this check as we would not be able to
            // retrieve a group model.
            assertEquals(
                expectedMemberList,
                serviceManager.groupService.getGroupMemberIdentities(group.groupModel).toSet(),
            )
        }
    }

    private fun assertMemberCount(expectedMemberCount: Int, group: TestGroup) {
        if (serviceManager.groupService.getByApiGroupIdAndCreator(
                group.apiGroupId,
                group.groupCreator.identity,
            ) != null
        ) {
            // We only check the expected members if the group is available in the database.
            // Otherwise, the check does not make sense as we would not be able to retrieve a group
            // model.
            assertEquals(
                expectedMemberCount,
                serviceManager.groupService.countMembers(group.groupModel),
            )
        }
    }

    private class GroupLeaveTracker(
        private val group: TestGroup?,
        private val leavingIdentity: IdentityString?,
        private val expectStateChange: Boolean = false,
    ) {
        private var memberHasLeft = false

        private val groupListener = object : GroupListener {
            override fun onCreate(groupIdentity: GroupIdentity) = fail()

            override fun onRename(groupIdentity: GroupIdentity) = fail()

            override fun onUpdatePhoto(groupIdentity: GroupIdentity) = fail()

            override fun onRemove(groupDbId: Long) = fail()

            override fun onNewMember(
                groupIdentity: GroupIdentity,
                identityNew: String,
            ) = fail()

            override fun onMemberLeave(
                groupIdentity: GroupIdentity,
                identityLeft: String,
            ) {
                assertFalse(memberHasLeft)
                group?.let {
                    assertEquals(it.apiGroupId.toLong(), groupIdentity.groupId)
                    assertEquals(it.groupCreator.identity, groupIdentity.creatorIdentity)
                    assertEquals(leavingIdentity, identityLeft)
                }
                memberHasLeft = true
            }

            override fun onMemberKicked(
                groupIdentity: GroupIdentity,
                identityKicked: String,
            ) = fail()

            override fun onUpdate(groupIdentity: GroupIdentity) = fail()

            override fun onLeave(groupIdentity: GroupIdentity) = fail()

            override fun onGroupStateChanged(
                groupIdentity: GroupIdentity,
                oldState: Int,
                newState: Int,
            ) {
                if (!expectStateChange) {
                    fail()
                }
            }
        }

        companion object {
            private val groupListeners: MutableList<GroupListener> = mutableListOf()

            fun stopAllListeners() {
                for (groupListener in groupListeners) {
                    ListenerManager.groupListeners.remove(groupListener)
                }
                groupListeners.clear()
            }
        }

        fun start() {
            ListenerManager.groupListeners.add(groupListener)
            groupListeners.add(groupListener)
        }

        fun assertMemberLeft() {
            assertTrue(memberHasLeft)
        }

        fun assertNoMemberLeft() {
            assertFalse(memberHasLeft)
        }

        fun stop() {
            ListenerManager.groupListeners.remove(groupListener)
            groupListeners.remove(groupListener)
        }
    }
}

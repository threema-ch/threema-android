/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.groupmanagement

import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ch.threema.app.DangerousTest
import ch.threema.app.activities.HomeActivity
import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage
import ch.threema.storage.models.GroupModel
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Ignore
import org.junit.Test
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
    fun testValidLeaveInMyGroup() {
        assertSuccessfulLeave(myGroup, contactA, true)
    }

    /**
     * Test that contact B leaving groupAB works as expected.
     */
    @Test
    fun testValidLeave() {
        assertSuccessfulLeave(groupAB, contactB)
    }

    /**
     * Test that the creator of a group cannot leave the group.
     */
    @Test
    @Ignore("TODO(ANDR-2385): ignore group leave messages from group creators")
    fun testLeaveFromSender() {
        assertUnsuccessfulLeave(groupA, contactA)
        assertUnsuccessfulLeave(groupB, contactB)
    }

    /**
     * Test that a leave message of an unknown group (where I am the owner) is discarded (and does
     * not change anything).
     */
    @Test
    fun testLeaveOfMyNonExistingGroup() {
        assertUnsuccessfulLeave(myUnknownGroup, contactA, emptyList())
    }

    /**
     * Test that a leave message of an unknown group (where I am not the owner) is discarded and has
     * no effect.
     */
    @Test
    fun testLeaveOfNonExistingGroup() {
        assertUnsuccessfulLeave(groupAUnknown, contactB, emptyList(), true)
    }

    /**
     * Test that a leave message of a left group (where I am not a member anymore) is discarded (and
     * does not change anything).
     */
    @Test
    fun testLeaveOfLeftGroup() {
        assertUnsuccessfulLeave(groupALeft, contactB, null, true)
    }

    /**
     * Test that a leave message of a left group (where I am the owner) is discarded (and nothing is
     * changed).
     */
    @Test
    fun testLeaveOfMyLeftGroup() {
        assertUnsuccessfulLeave(myLeftGroup, contactA)
    }

    /**
     * Test that a leave message of a sender that is not part of the group is discarded and has no
     * effect.
     */
    @Test
    fun testLeaveOfNonMember() {
        assertUnsuccessfulLeave(groupA, contactB)
    }

    @After
    fun removeAllGroupListeners() {
        GroupLeaveTracker.stopAllListeners()
    }

    override fun createMessageForGroup() = GroupLeaveMessage()

    override fun testCommonGroupReceiveStep2_1() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStep2_2() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStep3_1() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStep3_2() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStep4_1() {
        // The common group receive steps are not executed for group leave messages
    }

    override fun testCommonGroupReceiveStep4_2() {
        // The common group receive steps are not executed for group leave messages
    }

    private fun assertSuccessfulLeave(group: TestGroup, contact: TestContact, expectStateChange: Boolean = false) {
        launchActivity<HomeActivity>()

        serviceManager.groupService.resetCache(group.groupModel.id)

        assertEquals(
            group.members.map { it.identity },
            serviceManager.groupService.getGroupMemberModels(group.groupModel).map { it.identity })

        val leaveTracker = GroupLeaveTracker(group, contact.identity, expectStateChange)
            .apply { start() }

        // Process the group rename message
        processMessage(createEncryptedGroupLeaveMessage(group, contact), contact.identityStore)

        leaveTracker.assertMemberLeft()

        leaveTracker.stop()

        serviceManager.groupService.resetCache(group.groupModel.id)

        assertEquals(
            group.members.size - 1,
            serviceManager.groupService.countMembers(group.groupModel)
        )
        assertEquals(
            group.members.map { it.identity }.filter { it != contact.identity },
            serviceManager.groupService.getGroupMemberModels(group.groupModel).map { it.identity })

        // Assert that no message has been sent as a response to a group leave
        assertEquals(0, sentMessages.size)
    }

    private fun assertUnsuccessfulLeave(
        group: TestGroup,
        contact: TestContact,
        expectedMembers: List<String>? = null,
        shouldSendSyncRequest: Boolean = false,
    ) {
        launchActivity<HomeActivity>()

        val expectedMemberList = expectedMembers ?: group.members.map { it.identity }

        serviceManager.groupService.resetCache(group.groupModel.id)

        assertEquals(
            expectedMemberList,
            serviceManager.groupService.getGroupMemberModels(group.groupModel).map { it.identity })

        val leaveTracker = GroupLeaveTracker(group, contact.identity).apply { start() }

        // Process the group rename message
        processMessage(createEncryptedGroupLeaveMessage(group, contact), contact.identityStore)

        leaveTracker.assertNoMemberLeft()

        leaveTracker.stop()

        serviceManager.groupService.resetCache(group.groupModel.id)

        assertEquals(
            expectedMemberList,
            serviceManager.groupService.getGroupMemberModels(group.groupModel).map { it.identity })
        assertEquals(
            expectedMemberList.size,
            serviceManager.groupService.countMembers(group.groupModel)
        )

        if (shouldSendSyncRequest) {
            // Should send sync request to the group creator
            assertEquals(1, sentMessages.size)
            val sentMessage = sentMessages.first() as GroupRequestSyncMessage
            assertEquals(myContact.identity, sentMessage.fromIdentity)
            assertEquals(group.groupCreator.identity, sentMessage.toIdentity)
            assertEquals(group.apiGroupId, sentMessage.apiGroupId)
            assertEquals(group.groupCreator.identity, sentMessage.groupCreator)
        } else {
            // Assert that no message has been sent as a response to the leave message
            assertEquals(0, sentMessages.size)
        }
    }

    private fun createEncryptedGroupLeaveMessage(group: TestGroup, contact: TestContact) =
        createMessageForGroup().apply {
            groupCreator = group.groupCreator.identity
            apiGroupId = group.apiGroupId
            fromIdentity = contact.identity
            toIdentity = myContact.identity
        }

    private class GroupLeaveTracker(
        private val group: TestGroup?,
        private val leavingIdentity: String?,
        private val expectStateChange: Boolean = false,
    ) {
        private var memberHasLeft = false

        private val groupListener = object : GroupListener {
            override fun onCreate(newGroupModel: GroupModel?) = fail()

            override fun onRename(groupModel: GroupModel?) = fail()

            override fun onUpdatePhoto(groupModel: GroupModel?) = fail()

            override fun onRemove(groupModel: GroupModel?) = fail()

            override fun onNewMember(
                group: GroupModel?,
                newIdentity: String?,
                previousMemberCount: Int
            ) = fail()

            override fun onMemberLeave(
                groupModel: GroupModel?,
                identity: String?,
                previousMemberCount: Int
            ) {
                assertFalse(memberHasLeft)
                group?.let {
                    assertArrayEquals(it.apiGroupId.groupId, groupModel?.apiGroupId?.groupId)
                    assertEquals(it.groupCreator.identity, groupModel?.creatorIdentity)
                    assertEquals(leavingIdentity, identity)
                }
                memberHasLeft = true
            }

            override fun onMemberKicked(
                group: GroupModel?,
                identity: String?,
                previousMemberCount: Int
            ) = fail()

            override fun onUpdate(groupModel: GroupModel?) = fail()

            override fun onLeave(groupModel: GroupModel?) = fail()

            override fun onGroupStateChanged(
                groupModel: GroupModel?,
                oldState: Int,
                newState: Int
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

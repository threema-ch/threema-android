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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ch.threema.app.DangerousTest
import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.storage.models.GroupModel
import junit.framework.TestCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs different tests that verify that incoming group setup messages are handled according to the
 * protocol.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupSetupTest : GroupConversationListTest<GroupSetupMessage>() {

    override fun createMessageForGroup() = GroupSetupMessage()

    /**
     * Test a group setup message of an unknown group where the user is not not a member.
     */
    @Test
    fun testUnknownGroupNotMember() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups)

        val setupTracker = GroupSetupTracker(
            groupAUnknown,
            myContact.identity,
            expectCreate = false,
            expectKick = false,
            emptyList(),
            emptyList(),
        )
        setupTracker.start()

        // Create the group setup message
        val message = createGroupSetupMessage(groupAUnknown)
        // Remove this user from the members
        message.members = message.members.filter { it != myContact.identity }.toTypedArray()
        // Create message box from contact A (group creator)
        processMessage(message, groupAUnknown.groupCreator.identityStore)

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(scenario, initialGroups)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that no action has been triggered
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()
    }

    /**
     * Test a group setup message of an unknown group that has no members.
     */
    @Test
    fun testUnknownEmptyGroup() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups)

        val setupTracker = GroupSetupTracker(
            groupAUnknown,
            myContact.identity,
            expectCreate = false,
            expectKick = false,
            emptyList(),
            emptyList(),
        )
        setupTracker.start()

        // Create the group setup message
        val message = createGroupSetupMessage(groupAUnknown)
        // Group is empty
        message.members = emptyArray()
        // Create message box from contact A (group creator)
        processMessage(message, groupAUnknown.groupCreator.identityStore)

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(scenario, initialGroups)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that no action has been triggered
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()
    }

    /**
     * Test a group setup message of a blocked contact.
     */
    @Test
    fun testBlocked() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups)

        serviceManager.blockedIdentitiesService.blockIdentity(contactA.identity)
        serviceManager.blockedIdentitiesService.blockIdentity(contactB.identity)

        val setupTracker = GroupSetupTracker(
            newAGroup,
            myContact.identity,
            expectCreate = false,
            expectKick = false,
            emptyList(),
            emptyList(),
        )
        setupTracker.start()

        // Create the group setup message
        val message = createGroupSetupMessage(newAGroup)
        // Create message box from contact A (group creator)
        processMessage(message, newAGroup.groupCreator.identityStore)

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(scenario, initialGroups)

        // Assert that a group leave message is sent to the created and all provided members
        // including those that are blocked
        assertEquals(2, sentMessagesInsideTask.size)
        val first = sentMessagesInsideTask.first() as GroupLeaveMessage
        assertEquals(myContact.identity, first.fromIdentity)
        assertEquals(newAGroup.apiGroupId, first.apiGroupId)
        assertEquals(newAGroup.groupCreator.identity, first.groupCreator)
        val second = sentMessagesInsideTask.last() as GroupLeaveMessage
        assertEquals(myContact.identity, second.fromIdentity)
        assertEquals(newAGroup.apiGroupId, second.apiGroupId)
        assertEquals(newAGroup.groupCreator.identity, second.groupCreator)
        // Assert that one message is for contact A and the other for contact B
        assertTrue(
            (first.toIdentity == contactA.identity && second.toIdentity == contactB.identity)
                    || (first.toIdentity == contactB.identity && second.toIdentity == contactA.identity)
        )

        // Assert that no action has been triggered
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()
    }

    /**
     * Test a group setup message of a group where the user is not a member anymore.
     */
    @Test
    fun testKicked() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups)

        // Assert that the user is a member of groupAB
        val beforeKicked = serviceManager.groupService.getById(groupAB.groupModel.id)
        assertNotNull(beforeKicked)
        assertEquals(GroupModel.UserState.MEMBER, beforeKicked!!.userState)
        assertTrue(serviceManager.groupService.isGroupMember(beforeKicked))

        val setupTracker = GroupSetupTracker(
            groupAB,
            myContact.identity,
            expectCreate = false,
            expectKick = true,
            emptyList(),
            listOf(myContact.identity),
        )
        setupTracker.start()

        // Create the group setup message
        val message = createGroupSetupMessage(groupAB)
        // Only contact B is a member of this group, so this user has been kicked
        message.members = arrayOf(contactB.identity)
        // Create message box from contact A (group creator)
        processMessage(message, groupAB.groupCreator.identityStore)

        // Assert that the user state has been changed to 'kicked'
        val afterKicked = serviceManager.groupService.getById(groupAB.groupModel.id)
        assertNotNull(afterKicked)
        assertEquals(GroupModel.UserState.KICKED, afterKicked!!.userState)
        assertFalse(serviceManager.groupService.isGroupMember(afterKicked))

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(scenario, initialGroups)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that the user has been kicked and the members are updated
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()
    }

    /**
     * Test a group setup message of a group where the members changed.
     */
    @Test
    fun testMembersChanged() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups)

        val setupTracker = GroupSetupTracker(
            groupAB,
            myContact.identity,
            expectCreate = false,
            expectKick = false,
            listOf(contactC.identity),
            listOf(contactB.identity),
        )
        setupTracker.start()

        // Create the group setup message
        val message = createGroupSetupMessage(groupAB).apply {
            // Remove contact B from group and add contact C to group
            members = members.toList().replace(contactB.identity, contactC.identity).toTypedArray()
        }
        // Create message box from contact A (group creator)
        processMessage(message, groupAB.groupCreator.identityStore)

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(scenario, initialGroups)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that the members have changed
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()
    }

    /**
     * Test a group setup message of a newly created group.
     */
    @Test
    fun testNewGroup() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups)

        val newGroup = TestGroup(
            newAGroup.apiGroupId,
            newAGroup.groupCreator,
            newAGroup.members,
            // Note that this will be the group name because we only test the group setup message
            // that is not followed by a group rename
            "Me, 12345678, ABCDEFGH",
            myContact.identity,
        )

        val setupTracker = GroupSetupTracker(
            newGroup,
            myContact.identity,
            expectCreate = true,
            expectKick = false,
            newGroup.members.map { it.identity } + newGroup.groupCreator.identity,
            emptyList(),
        )
        setupTracker.start()

        // Create the group setup message
        val message = createGroupSetupMessage(newGroup)
        // Create message box from contact A (group creator)
        processMessage(message, newGroup.groupCreator.identityStore)

        // Assert that the new group appears in the list
        assertGroupConversations(scenario, initialGroups + newGroup)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that the group has been created and the new members are set correctly
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()

        // Assert that the group has the correct members
        val group = serviceManager.groupService.getByApiGroupIdAndCreator(newGroup.apiGroupId, newGroup.groupCreator.identity)
        assertNotNull(group!!)
        val expectedMemberCount = newGroup.members.size
        // Assert that there is one more member than member models (as the user is not stored into
        // the database).
        assertEquals(expectedMemberCount, serviceManager.databaseServiceNew.groupMemberModelFactory.getByGroupId(group.id).size + 1)
        assertEquals(expectedMemberCount, serviceManager.databaseServiceNew.groupMemberModelFactory.countMembersWithoutUser(group.id).toInt() + 1)

        // Assert that the group service returns the member lists including the user
        assertEquals(expectedMemberCount, serviceManager.groupService.getMembers(group).size)
        assertEquals(expectedMemberCount, serviceManager.groupService.getGroupIdentities(group).size)
        assertEquals(expectedMemberCount, serviceManager.groupService.getMembersWithoutUser(group).size + 1)
        assertEquals(expectedMemberCount, serviceManager.groupService.countMembers(group))
        assertEquals(expectedMemberCount, serviceManager.groupService.countMembersWithoutUser(group) + 1)
    }

    /**
     * Test two group setup messages that remove and then add the user.
     */
    @Test
    fun testRemoveJoin() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups)

        val setupTracker = GroupSetupTracker(
            groupAB,
            myContact.identity,
            expectCreate = false,
            expectKick = true,
            listOf(myContact.identity),
            listOf(myContact.identity),
        )
        setupTracker.start()

        // Create the group setup message
        val removeMessage = createGroupSetupMessage(groupAB)
        // Only contact B is a member of this group, so this user has been kicked
        removeMessage.members = arrayOf(contactB.identity)
        // Create message box from contact A (group creator)
        processMessage(removeMessage, groupAB.groupCreator.identityStore)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Create the group setup message (now again with this user)
        val addMessage = createGroupSetupMessage(groupAB)
        // Now we again include this user
        addMessage.members = arrayOf(contactB.identity, myContact.identity)
        // Create message box from contact A (group creator)
        processMessage(addMessage, groupAB.groupCreator.identityStore)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that the user has been kicked and added again
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()
    }

    @Test
    fun testGroupContainingInvalidIDs() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups)

        val invalidMemberId = ",,,,,,,,"

        val newGroup = TestGroup(
            newAGroup.apiGroupId,
            newAGroup.groupCreator,
            newAGroup.members + TestContact(invalidMemberId), // Note that this ID is not valid
            // Note that this will be the group name because we only test the group setup message
            // that is not followed by a group rename
            "Me, 12345678, ABCDEFGH",
            myContact.identity,
        )

        val setupTracker = GroupSetupTracker(
            newGroup,
            myContact.identity,
            expectCreate = true,
            expectKick = false,
            newGroup.members.filter { it.identity != invalidMemberId }
                .map { it.identity } + newGroup.groupCreator.identity,
            emptyList(),
        )
        setupTracker.start()

        // Create the group setup message
        val message = createGroupSetupMessage(newGroup)
        // Create message box from contact A (group creator)
        processMessage(message, newGroup.groupCreator.identityStore)

        // Assert that the new group appears in the list
        assertGroupConversations(scenario, initialGroups + newGroup)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that the group has been created and the new members are set correctly
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()
    }

    private fun createGroupSetupMessage(testGroup: TestGroup) = GroupSetupMessage()
        .apply {
        apiGroupId = testGroup.apiGroupId
        groupCreator = testGroup.groupCreator.identity
        fromIdentity = testGroup.groupCreator.identity
        toIdentity = myContact.identity
        members =
            testGroup.members.map { it.identity }.filter { it != testGroup.groupCreator.identity }
                .toTypedArray()
    }

    private class GroupSetupTracker(
        private val group: TestGroup?,
        private val myIdentity: String,
        private val expectCreate: Boolean,
        private val expectKick: Boolean,
        private val newMembers: List<String>,
        private val kickedMembers: List<String>,
    ) {
        private var hasBeenCreated = false
        private var hasBeenKicked = false
        private var newMembersAdded = mutableListOf<String>()
        private var kickedMembersRemoved = mutableListOf<String>()

        private val groupListener = object : GroupListener {
            override fun onCreate(newGroupModel: GroupModel?) {
                assertTrue(expectCreate)
                assertFalse(hasBeenCreated)
                group?.let {
                    Assert.assertArrayEquals(
                        it.apiGroupId.groupId,
                        newGroupModel?.apiGroupId?.groupId
                    )
                    TestCase.assertEquals(it.groupCreator.identity, newGroupModel?.creatorIdentity)
                }
                hasBeenCreated = true
            }

            override fun onRename(groupModel: GroupModel?) = fail()

            override fun onUpdatePhoto(groupModel: GroupModel?) = fail()

            override fun onRemove(groupModel: GroupModel?) = fail()

            override fun onNewMember(
                group: GroupModel?,
                newIdentity: String?,
            ) {
                assertTrue("Did not expect member $newIdentity", newMembers.contains(newIdentity))
                newMembersAdded.add(newIdentity!!)
            }

            override fun onMemberLeave(
                group: GroupModel?,
                identity: String?,
            ) = fail()

            override fun onMemberKicked(
                group: GroupModel?,
                identity: String?,
            ) {
                assertTrue(kickedMembers.contains(identity))
                kickedMembersRemoved.add(identity!!)

                if (identity == myIdentity) {
                    assertTrue(expectKick)
                    assertFalse(hasBeenKicked)
                    hasBeenKicked = true
                }
            }

            override fun onUpdate(groupModel: GroupModel?) {
                // This should only be called if the receiver has been changed (a member has been
                // added or kicked)
                assertTrue(newMembers.isNotEmpty() || kickedMembers.isNotEmpty())
            }

            override fun onLeave(groupModel: GroupModel?) = fail()

            override fun onGroupStateChanged(
                groupModel: GroupModel?,
                oldState: Int,
                newState: Int
            ) {
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

        fun assertAllNewMembersAdded() {
            assertEquals(newMembers.toSet(), newMembersAdded.toSet())
        }

        fun assertAllKickedMembersRemoved() {
            assertEquals(kickedMembers.toSet(), kickedMembersRemoved.toSet())
        }

        fun assertCreateLeave() {
            assertEquals(expectCreate, hasBeenCreated)
            assertEquals(expectKick, hasBeenKicked)
        }

        fun stop() {
            ListenerManager.groupListeners.remove(groupListener)
            groupListeners.remove(groupListener)
        }
    }

    @After
    fun removeAllGroupListeners() {
        GroupSetupTracker.stopAllListeners()
    }

    override fun testCommonGroupReceiveStep2_1() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStep2_2() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStep3_1() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStep3_2() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStep4_1() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStep4_2() {
        // The common group receive steps are not executed for group setup messages
    }
}

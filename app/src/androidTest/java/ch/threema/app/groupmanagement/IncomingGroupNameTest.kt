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
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.messages.GroupRenameMessage
import ch.threema.storage.models.GroupModel
import junit.framework.TestCase.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

/**
 * Tests that incoming group name messages are handled correctly.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupNameTest : GroupConversationListTest<GroupRenameMessage>() {

    override fun createMessageForGroup(): GroupRenameMessage {
        return GroupRenameMessage().apply { groupName = "New Group Name" }
    }

    /**
     * Tests that a (valid) group rename message really changes the group name.
     */
    @Test
    fun testValidGroupRename() {
        // Start home activity and navigate to chat section
        val activityScenario = startScenario()

        // Assert initial groups
        assertGroupConversations(activityScenario, initialGroups)

        // Create group rename message
        val groupARenamed =
            TestGroup(groupA.apiGroupId, groupA.groupCreator, groupA.members, "GroupARenamed")

        val renameTracker = GroupRenameTracker(groupARenamed).apply { start() }

        val message = createEncryptedRenameMessage(
            groupARenamed.groupName,
            groupARenamed.groupCreator.identity,
            groupARenamed.apiGroupId,
            groupARenamed.groupCreator
        )

        // Process the group rename message
        processMessage(message, groupARenamed.groupCreator.identityStore)

        // Assert that the listeners were triggered
        renameTracker.assertRename()
        renameTracker.stop()

        // Assert that the group name change has been processed
        assertGroupConversations(activityScenario, initialGroups.replace(groupA, groupARenamed))
    }

    /**
     * Check that a group rename message from a wrong sender (not the group creator, just a member)
     * does not lead to a group name change.
     */
    @Test
    fun testInvalidGroupRenameSender() {
        // Start home activity and navigate to chat section
        val activityScenario = startScenario()

        // Assert initial groups
        assertGroupConversations(activityScenario, initialGroups)

        // Create group rename message (from wrong sender)
        val groupARenamed =
            TestGroup(groupA.apiGroupId, groupA.groupCreator, groupA.members, "GroupARenamed")

        val renameTracker = GroupRenameTracker(null).apply { start() }

        val message = createEncryptedRenameMessage(
            groupARenamed.groupName,
            groupARenamed.groupCreator.identity, // Note that this will be ignored anyway
            groupARenamed.apiGroupId,
            contactB // Not the creator of this group!
        )

        // Process the group rename message
        processMessage(message, contactB.identityStore)

        renameTracker.assertNoRename()
        renameTracker.stop()

        assertGroupConversations(activityScenario, initialGroups)
    }

    override fun testCommonGroupReceiveStep2_1() {
        runWithoutGroupRename { super.testCommonGroupReceiveStep2_1() }
    }

    override fun testCommonGroupReceiveStep2_2() {
        runWithoutGroupRename { super.testCommonGroupReceiveStep2_2() }
    }

    override fun testCommonGroupReceiveStep3_1() {
        // Don't test this step. The group rename message is always sent as creator of the group
        // and if the sender of the message is the creator of a group owned by this user, then the
        // message comes from this user itself - which is impossible.
    }

    override fun testCommonGroupReceiveStep3_2() {
        runWithoutGroupRename { super.testCommonGroupReceiveStep3_2() }
    }

    override fun testCommonGroupReceiveStep4_1() {
        // Don't test this step. The group rename message is always sent as creator of the group
        // and therefore the sender of the message is never missing in the group. However, the group
        // model is (very likely) not found and therefore handled in step 2.1 of the common group
        // receive steps.
    }

    override fun testCommonGroupReceiveStep4_2() {
        // Don't test this step. The group rename message is always sent as creator of the group
        // and therefore the sender of the message is never missing in the group. However, the group
        // model is (very likely) not found and therefore handled in step 2.2 of the common group
        // receive steps.
    }

    @After
    fun removeAllGroupListeners() {
        GroupRenameTracker.stopAllListeners()
    }

    private fun createEncryptedRenameMessage(
        newGroupName: String,
        groupCreatorIdentity: String,
        apiGroupId: GroupId,
        fromContact: TestContact,
    ) = GroupRenameMessage().apply {
        groupName = newGroupName
        groupCreator = groupCreatorIdentity
        fromIdentity = fromContact.identity
        setApiGroupId(apiGroupId)
        toIdentity = myContact.identity
    }

    /**
     * Run [processMessage] and assert that no group rename happens.
     */
    private fun runWithoutGroupRename(processMessage: () -> Unit) {
        val groupRenameTracker = GroupRenameTracker(null).apply { start() }

        processMessage()

        groupRenameTracker.assertNoRename()
        groupRenameTracker.stop()
    }

    private class GroupRenameTracker(private val group: TestGroup?) {
        private var hasBeenRenamed = false

        private val groupListener = object : GroupListener {
            override fun onCreate(newGroupModel: GroupModel?) {
                fail()
            }

            override fun onRename(groupModel: GroupModel?) {
                assertFalse(hasBeenRenamed)
                group?.let {
                    assertArrayEquals(it.apiGroupId.groupId, groupModel?.apiGroupId?.groupId)
                    assertEquals(it.groupCreator.identity, groupModel?.creatorIdentity)
                    assertEquals(it.groupName, groupModel?.name)
                }
                hasBeenRenamed = true
            }

            override fun onUpdatePhoto(groupModel: GroupModel?) {
                fail()
            }

            override fun onRemove(groupModel: GroupModel?) {
                fail()
            }

            override fun onNewMember(
                group: GroupModel?,
                newIdentity: String?,
                previousMemberCount: Int
            ) {
                fail()
            }

            override fun onMemberLeave(
                group: GroupModel?,
                identity: String?,
                previousMemberCount: Int
            ) {
                fail()
            }

            override fun onMemberKicked(
                group: GroupModel?,
                identity: String?,
                previousMemberCount: Int
            ) {
                fail()
            }

            override fun onUpdate(groupModel: GroupModel?) {
                fail()
            }

            override fun onLeave(groupModel: GroupModel?) {
                fail()
            }

            override fun onGroupStateChanged(
                groupModel: GroupModel?,
                oldState: Int,
                newState: Int
            ) {
                fail()
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

        fun assertRename() {
            assertTrue(hasBeenRenamed)
        }

        fun assertNoRename() {
            assertFalse(hasBeenRenamed)
        }

        fun stop() {
            ListenerManager.groupListeners.remove(groupListener)
            groupListeners.remove(groupListener)
        }
    }

}

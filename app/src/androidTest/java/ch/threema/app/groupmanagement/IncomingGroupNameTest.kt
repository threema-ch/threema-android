/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import junit.framework.TestCase.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that incoming group name messages are handled correctly.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupNameTest : GroupConversationListTest<GroupNameMessage>() {
    override fun createMessageForGroup(): GroupNameMessage {
        return GroupNameMessage()
            .apply { groupName = "New Group Name" }
    }

    /**
     * Tests that a (valid) group rename message really changes the group name.
     */
    @Test
    fun testValidGroupRename() = runTest {
        // Start home activity and navigate to chat section
        val activityScenario = startScenario()

        // Assert initial groups
        assertGroupConversations(activityScenario, initialGroups)

        // Create group rename message
        val groupARenamed =
            TestGroup(
                groupA.apiGroupId,
                groupA.groupCreator,
                groupA.members,
                "GroupARenamed",
                myContact.identity,
            )

        val renameTracker = GroupRenameTracker(groupARenamed).apply { start() }

        val message = createEncryptedRenameMessage(
            groupARenamed.groupName,
            groupARenamed.groupCreator.identity,
            groupARenamed.apiGroupId,
            groupARenamed.groupCreator,
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
    fun testInvalidGroupRenameSender() = runTest {
        // Start home activity and navigate to chat section
        val activityScenario = startScenario()

        // Assert initial groups
        assertGroupConversations(activityScenario, initialGroups)

        // Create group rename message (from wrong sender)
        val groupARenamed =
            TestGroup(
                groupA.apiGroupId,
                groupA.groupCreator,
                groupA.members,
                "GroupARenamed",
                myContact.identity,
            )

        val renameTracker = GroupRenameTracker(null).apply { start() }

        val message = createEncryptedRenameMessage(
            newGroupName = groupARenamed.groupName,
            // Note that this will be ignored anyway
            groupCreatorIdentity = groupARenamed.groupCreator.identity,
            apiGroupId = groupARenamed.apiGroupId,
            // Not the creator of this group!
            fromContact = contactB,
        )

        // Process the group rename message
        processMessage(message, contactB.identityStore)

        renameTracker.assertNoRename()
        renameTracker.stop()

        assertGroupConversations(activityScenario, initialGroups)
    }

    override fun testCommonGroupReceiveStepUnknownGroupUserCreator() {
        // Don't test this as a group name message always comes from the group creator which would
        // be this user in this test
    }

    override fun testCommonGroupReceiveStepUnknownGroupUserNotCreator() {
        runWithoutGroupRename { super.testCommonGroupReceiveStepUnknownGroupUserNotCreator() }
    }

    override fun testCommonGroupReceiveStepLeftGroupUserCreator() {
        // Don't test this step. The group rename message is always sent as creator of the group
        // and if the sender of the message is the creator of a group owned by this user, then the
        // message comes from this user itself - which is impossible.
    }

    override fun testCommonGroupReceiveStepLeftGroupUserNotCreator() {
        runWithoutGroupRename { super.testCommonGroupReceiveStepLeftGroupUserNotCreator() }
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserCreator() {
        // Don't test this step. The group rename message is always sent as creator of the group
        // and therefore the sender of the message is never missing in the group. However, the group
        // model is (very likely) not found and therefore handled earlier in the common group
        // receive steps.
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserNotCreator() {
        // Don't test this step. The group rename message is always sent as creator of the group
        // and therefore the sender of the message is never missing in the group. However, the group
        // model is (very likely) not found and therefore handled earlier in the common group
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
    ) = GroupNameMessage().apply {
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
            override fun onCreate(groupIdentity: GroupIdentity) {
                fail()
            }

            override fun onRename(groupIdentity: GroupIdentity) {
                assertFalse(hasBeenRenamed)
                group?.let {
                    assertEquals(it.apiGroupId.toLong(), groupIdentity.groupId)
                    assertEquals(it.groupCreator.identity, groupIdentity.creatorIdentity)
                }
                hasBeenRenamed = true
            }

            override fun onUpdatePhoto(groupIdentity: GroupIdentity) {
                fail()
            }

            override fun onRemove(groupDbId: Long) {
                fail()
            }

            override fun onNewMember(
                groupIdentity: GroupIdentity,
                identityNew: String?,
            ) {
                fail()
            }

            override fun onMemberLeave(
                groupIdentity: GroupIdentity,
                identityLeft: String,
            ) {
                fail()
            }

            override fun onMemberKicked(
                groupIdentity: GroupIdentity,
                identityKicked: String?,
            ) {
                fail()
            }

            override fun onUpdate(groupIdentity: GroupIdentity) {
                fail()
            }

            override fun onLeave(groupIdentity: GroupIdentity) {
                fail()
            }

            override fun onGroupStateChanged(
                groupIdentity: GroupIdentity,
                oldState: Int,
                newState: Int,
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

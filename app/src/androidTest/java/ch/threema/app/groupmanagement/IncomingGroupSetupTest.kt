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
import ch.threema.base.crypto.NaCl
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.GroupModel
import java.util.Date
import junit.framework.TestCase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/**
 * Runs different tests that verify that incoming group setup messages are handled according to the
 * protocol.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupSetupTest : GroupConversationListTest<GroupSetupMessage>() {
    private val groupService by lazy { serviceManager.groupService }
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }

    override fun createMessageForGroup() = GroupSetupMessage()

    /**
     * Test a group setup message of an unknown group where the user is not not a member.
     */
    @Test
    fun testUnknownGroupNotMember() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups, "initial groups")

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
        assertGroupConversations(scenario, initialGroups, "no changes")

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
        assertGroupConversations(scenario, initialGroups, "epect initial group")

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
        assertGroupConversations(scenario, initialGroups, "no changes")

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

        val newGroup = TestGroup(
            newAGroup.apiGroupId,
            newAGroup.groupCreator,
            newAGroup.members,
            // Note that this will be the group name because we only test the group setup message
            // that is not followed by a group rename
            "Me, 12345678, ABCDEFGH",
            myContact.identity,
        )

        testNewGroup(newGroup)
    }

    /**
     * Test a group setup message of a group where the user is not a member anymore.
     */
    @Test
    fun testKicked() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups, "initial groups")

        // Assert that the user is a member of groupAB
        val beforeKicked = groupService.getById(groupAB.groupModel.id)
        assertNotNull(beforeKicked)
        assertEquals(GroupModel.UserState.MEMBER, beforeKicked.userState)
        assertTrue(groupService.isGroupMember(beforeKicked))

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
        val afterKicked = groupModelRepository.getByGroupIdentity(
            GroupIdentity(groupAB.groupCreator.identity, groupAB.apiGroupId.toLong()),
        )
        assertNotNull(afterKicked)
        assertEquals(GroupModel.UserState.KICKED, afterKicked.data?.userState)

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(scenario, initialGroups, "no changes")

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
        assertGroupConversations(scenario, initialGroups, "no changes")

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
        val newGroup = TestGroup(
            newBGroup.apiGroupId,
            newBGroup.groupCreator,
            newBGroup.members,
            // Note that this will be the group name because we only test the group setup message
            // that is not followed by a group rename
            "Me, ABCDEFGH",
            myContact.identity,
        )

        testNewGroup(newGroup)
    }

    /**
     * Test two group setup messages that remove and then add the user.
     */
    @Test
    fun testRemoveJoin() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups, "initial groups")

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
            // Note that this ID is not valid
            newAGroup.members + TestContact(invalidMemberId),
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
        assertGroupConversations(scenario, listOf(newGroup) + initialGroups)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that the group has been created and the new members are set correctly
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()
    }

    @Test
    fun testGroupContainingRevokedButKnownContact() = runTest {
        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups)

        // Add a revoked contact
        serviceManager.modelRepositories.contacts.createFromLocal(revokedContactModelData)

        val newGroup = TestGroup(
            newAGroup.apiGroupId,
            newAGroup.groupCreator,
            // Note that the activity state of this contact is INVALID
            newAGroup.members + TestContact(revokedContactModelData.identity),
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
            newGroup.members.filter { it.identity != revokedContactModelData.identity }
                .map { it.identity } + newGroup.groupCreator.identity,
            emptyList(),
        )
        setupTracker.start()

        // Create the group setup message
        val message = createGroupSetupMessage(newGroup)

        // Check that the group setup message contains the revoked ID. Otherwise this test does not make sense.
        assertTrue(message.members.contains(revokedContactModelData.identity))

        // Create message box from contact A (group creator)
        processMessage(message, newGroup.groupCreator.identityStore)

        // Assert that the new group appears in the list
        assertGroupConversations(scenario, listOf(newGroup) + initialGroups)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that the group has been created and the new members are set correctly
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()

        // Get the group model of the group and check that it exists and the revoked identity is not listed as a member
        val newGroupModel = groupModelRepository.getByCreatorIdentityAndId(newGroup.groupCreator.identity, newGroup.apiGroupId)
        assertNotNull(newGroupModel)
        val data = newGroupModel.data
        assertNotNull(data)
        assertFalse(data.otherMembers.contains(revokedContactModelData.identity))
    }

    private suspend fun testNewGroup(newGroup: TestGroup) {
        assertNull(
            groupModelRepository.getByCreatorIdentityAndId(
                newGroup.groupCreator.identity,
                newGroup.apiGroupId,
            )?.data,
        )

        val scenario = startScenario()

        // Assert initial group conversations
        assertGroupConversations(scenario, initialGroups, "initial groups")

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

        // Assert that the new group model exists
        val groupModel = groupModelRepository.getByCreatorIdentityAndId(
            creatorIdentity = newGroup.groupCreator.identity,
            groupId = newGroup.apiGroupId,
        )
        assertNotNull(groupModel)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that the group has been created and the new members are set correctly
        setupTracker.assertAllNewMembersAdded()
        setupTracker.assertAllKickedMembersRemoved()
        setupTracker.assertCreateLeave()
        setupTracker.stop()

        // Assert that the group has the correct members
        val group = groupService.getByApiGroupIdAndCreator(
            newGroup.apiGroupId,
            newGroup.groupCreator.identity,
        )
        assertNotNull(group!!)
        val expectedMemberCount = newGroup.members.size
        // Assert that there is one more member than member models (as the user is not stored into
        // the database).
        assertEquals(
            expectedMemberCount,
            serviceManager.databaseService.groupMemberModelFactory.getByGroupId(group.id).size + 1,
        )
        assertEquals(
            expectedMemberCount,
            serviceManager.databaseService.groupMemberModelFactory.countMembersWithoutUser(group.id)
                .toInt() + 1,
        )

        // Assert that the group service returns the member lists including the user
        assertEquals(expectedMemberCount, groupService.getMembers(group).size)
        assertEquals(expectedMemberCount, groupService.getGroupMemberIdentities(group).size)
        assertEquals(expectedMemberCount, groupService.getMembersWithoutUser(group).size + 1)
        assertEquals(expectedMemberCount, groupService.countMembers(group))
        assertEquals(expectedMemberCount, groupService.countMembersWithoutUser(group) + 1)

        // Assert that the new group appears in the list
        assertGroupConversations(scenario, listOf(newGroup) + initialGroups)
    }

    private fun createGroupSetupMessage(testGroup: TestGroup) = GroupSetupMessage()
        .apply {
            apiGroupId = testGroup.apiGroupId
            groupCreator = testGroup.groupCreator.identity
            fromIdentity = testGroup.groupCreator.identity
            toIdentity = myContact.identity
            members =
                testGroup.members.map { it.identity }
                    .filter { it != testGroup.groupCreator.identity }
                    .toTypedArray()
        }

    private class GroupSetupTracker(
        private val group: TestGroup?,
        private val myIdentity: Identity,
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
            override fun onCreate(groupIdentity: GroupIdentity) {
                assertTrue(expectCreate)
                assertFalse(hasBeenCreated)
                group?.let {
                    assertEquals(
                        it.apiGroupId.toLong(),
                        groupIdentity.groupId,
                    )
                    TestCase.assertEquals(it.groupCreator.identity, groupIdentity.creatorIdentity)
                }
                hasBeenCreated = true
            }

            override fun onRename(groupIdentity: GroupIdentity) = fail()

            override fun onUpdatePhoto(groupIdentity: GroupIdentity) = fail()

            override fun onRemove(groupDbId: Long) = fail()

            override fun onNewMember(
                groupIdentity: GroupIdentity,
                identityNew: String?,
            ) {
                assertTrue(newMembers.contains(identityNew), "Did not expect member $identityNew")
                newMembersAdded.add(identityNew!!)
            }

            override fun onMemberLeave(
                groupIdentity: GroupIdentity,
                identityLeft: String,
            ) = fail()

            override fun onMemberKicked(
                groupIdentity: GroupIdentity,
                identityKicked: String,
            ) {
                assertTrue(kickedMembers.contains(identityKicked))
                kickedMembersRemoved.add(identityKicked)

                if (identityKicked == myIdentity) {
                    assertTrue(expectKick)
                    assertFalse(hasBeenKicked)
                    hasBeenKicked = true
                }
            }

            override fun onUpdate(groupIdentity: GroupIdentity) {
                // This should only be called if the receiver has been changed (a member has been
                // added or kicked)
                assertTrue(newMembers.isNotEmpty() || kickedMembers.isNotEmpty())
            }

            override fun onLeave(groupIdentity: GroupIdentity) = fail()

            override fun onGroupStateChanged(
                groupIdentity: GroupIdentity,
                oldState: Int,
                newState: Int,
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

    @AfterTest
    fun removeAllGroupListeners() {
        GroupSetupTracker.stopAllListeners()
    }

    private val revokedContactModelData = ContactModelData(
        identity = "01238765",
        publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES),
        createdAt = Date(),
        firstName = "1234",
        lastName = "8765",
        nickname = null,
        verificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        identityType = IdentityType.NORMAL,
        acquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState = IdentityState.INVALID,
        syncState = ContactSyncState.INITIAL,
        featureMask = 0u,
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
    )

    override fun testCommonGroupReceiveStepUnknownGroupUserCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepUnknownGroupUserNotCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepLeftGroupUserCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepLeftGroupUserNotCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserNotCreator() {
        // The common group receive steps are not executed for group setup messages
    }
}

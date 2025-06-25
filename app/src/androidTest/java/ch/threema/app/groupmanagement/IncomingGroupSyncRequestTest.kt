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

import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ch.threema.app.DangerousTest
import ch.threema.app.home.HomeActivity
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/**
 * Tests that incoming group sync request messages are handled correctly.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupSyncRequestTest : GroupControlTest<GroupSyncRequestMessage>() {
    override fun createMessageForGroup() = GroupSyncRequestMessage()

    @Test
    fun testValidSyncRequest() = runTest {
        assertValidGroupSyncRequest(myGroup, contactA)

        // Assert that the same group sync request will be ignored if a group sync request from the
        // same sender for the same group has already been handled in the last hour.
        assertIgnoredGroupSyncRequest(myGroup, contactA)

        // Assert that a group sync request in the same group but from a different member is still
        // answered.
        assertValidGroupSyncRequest(myGroup, contactB)
    }

    @Test
    fun testSyncRequestToMember() = runTest {
        assertIgnoredGroupSyncRequest(groupAB, contactB)
    }

    @Test
    fun testSyncRequestFromNonMember() = runTest {
        assertLeftGroupSyncRequest(myGroup, contactC)
    }

    @Test
    fun testSyncRequestToLeftGroup() = runTest {
        assertLeftGroupSyncRequest(myLeftGroup, contactA)
    }

    override fun testCommonGroupReceiveStepUnknownGroupUserCreator() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStepUnknownGroupUserNotCreator() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStepLeftGroupUserCreator() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStepLeftGroupUserNotCreator() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserCreator() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserNotCreator() {
        // Common group receive steps are not executed for group sync request messages
    }

    private suspend fun assertValidGroupSyncRequest(group: TestGroup, contact: TestContact) {
        launchActivity<HomeActivity>()

        // Create group sync request message
        val groupSyncRequestMessage = GroupSyncRequestMessage()
            .apply {
                fromIdentity = contact.identity
                toIdentity = myContact.identity
                apiGroupId = group.apiGroupId
                groupCreator = group.groupCreator.identity
            }

        // Process sync request message
        processMessage(groupSyncRequestMessage, contact.identityStore)

        // Check that the first sent message (setup) is correct
        val setupMessage = sentMessagesInsideTask.poll() as GroupSetupMessage
        assertContentEquals(
            group.membersWithoutCreator.map { it.identity }.toTypedArray(),
            setupMessage.members,
        )
        assertEquals(myContact.contact.identity, setupMessage.fromIdentity)
        assertEquals(contact.identity, setupMessage.toIdentity)
        assertEquals(group.groupCreator.identity, setupMessage.groupCreator)
        assertEquals(group.apiGroupId, setupMessage.apiGroupId)

        // Check that the second sent message (rename) is correct
        val renameMessage = sentMessagesInsideTask.poll() as GroupNameMessage
        assertEquals(group.groupName, renameMessage.groupName)
        assertEquals(myContact.identity, renameMessage.fromIdentity)
        assertEquals(contact.identity, renameMessage.toIdentity)
        assertEquals(group.groupCreator.identity, renameMessage.groupCreator)
        assertEquals(group.apiGroupId, renameMessage.apiGroupId)

        assertNull(group.profilePicture, "Groups with photo are not supported for testing")

        // Check that the third sent message (set/delete photo) is correct
        val deletePhotoMessage = sentMessagesInsideTask.poll() as GroupDeleteProfilePictureMessage
        assertEquals(myContact.identity, deletePhotoMessage.fromIdentity)
        assertEquals(contact.identity, deletePhotoMessage.toIdentity)
        assertEquals(group.groupCreator.identity, deletePhotoMessage.groupCreator)
        assertEquals(group.apiGroupId, deletePhotoMessage.apiGroupId)

        assertTrue(sentMessagesInsideTask.isEmpty())
    }

    private suspend fun assertIgnoredGroupSyncRequest(group: TestGroup, contact: TestContact) {
        launchActivity<HomeActivity>()

        // Create group sync request message
        val groupSyncRequestMessage = GroupSyncRequestMessage()
            .apply {
                fromIdentity = contact.identity
                toIdentity = myContact.identity
                apiGroupId = group.apiGroupId
                groupCreator = group.groupCreator.identity
            }

        processMessage(groupSyncRequestMessage, contact.identityStore)

        assertTrue(sentMessagesInsideTask.isEmpty())
    }

    private suspend fun assertLeftGroupSyncRequest(group: TestGroup, contact: TestContact) {
        launchActivity<HomeActivity>()

        // Create group sync request message
        val groupSyncRequestMessage = GroupSyncRequestMessage()
            .apply {
                fromIdentity = contact.identity
                toIdentity = myContact.identity
                apiGroupId = group.apiGroupId
                groupCreator = group.groupCreator.identity
            }

        processMessage(groupSyncRequestMessage, contact.identityStore)

        // Check that a setup message has been sent with empty members list
        assertEquals(1, sentMessagesInsideTask.size)
        val setupMessage = sentMessagesInsideTask.first() as GroupSetupMessage
        assertContentEquals(emptyArray(), setupMessage.members)
        assertEquals(myContact.contact.identity, setupMessage.fromIdentity)
        assertEquals(contact.identity, setupMessage.toIdentity)
        assertEquals(group.groupCreator.identity, setupMessage.groupCreator)
        assertEquals(group.apiGroupId, setupMessage.apiGroupId)
    }
}

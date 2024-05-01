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
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that incoming group sync request messages are handled correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupSyncRequestTest : GroupControlTest<GroupSyncRequestMessage>() {

    override fun createMessageForGroup() = GroupSyncRequestMessage()

    @Test
    fun testValidSyncRequest() {
        assertValidGroupSyncRequest(myGroup, contactA)
    }

    @Test
    fun testSyncRequestToMember() = runTest {
        assertIgnoredGroupSyncRequest(groupAB, contactB)
    }

    @Test
    fun testSyncRequestFromNonMember() = runTest {
        assertLeftGroupSyncRequest(myGroup, contactB)
    }

    @Test
    fun testSyncRequestToLeftGroup() = runTest {
        assertLeftGroupSyncRequest(myLeftGroup, contactA)
    }

    override fun testCommonGroupReceiveStep2_1() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStep2_2() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStep3_1() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStep3_2() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStep4_1() {
        // Common group receive steps are not executed for group sync request messages
    }

    override fun testCommonGroupReceiveStep4_2() {
        // Common group receive steps are not executed for group sync request messages
    }

    private fun assertValidGroupSyncRequest(group: TestGroup, contact: TestContact) = runTest {
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
        assertArrayEquals(group.members.map { it.identity }.toTypedArray(), setupMessage.members)
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

        assertTrue("Groups with photo are not supported for testing", group.profilePicture == null)

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
        assertArrayEquals(emptyArray(), setupMessage.members)
        assertEquals(myContact.contact.identity, setupMessage.fromIdentity)
        assertEquals(contact.identity, setupMessage.toIdentity)
        assertEquals(group.groupCreator.identity, setupMessage.groupCreator)
        assertEquals(group.apiGroupId, setupMessage.apiGroupId)
    }
}

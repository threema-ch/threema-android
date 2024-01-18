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
import ch.threema.domain.protocol.csp.messages.GroupCreateMessage
import ch.threema.domain.protocol.csp.messages.GroupDeletePhotoMessage
import ch.threema.domain.protocol.csp.messages.GroupRenameMessage
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that incoming group sync request messages are handled correctly.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupSyncRequestTest : GroupControlTest<GroupRequestSyncMessage>() {

    override fun createMessageForGroup() = GroupRequestSyncMessage()

    @Test
    fun testValidSyncRequest() {
        assertValidGroupSyncRequest(myGroup, contactA)
    }

    @Test
    fun testSyncRequestToMember() {
        assertIgnoredGroupSyncRequest(groupAB, contactB)
    }

    @Test
    fun testSyncRequestFromNonMember() {
        assertLeftGroupSyncRequest(myGroup, contactB)
    }

    @Test
    fun testSyncRequestFromMyself() {
        assertIgnoredGroupSyncRequest(myGroup, myContact)
    }

    @Test
    fun testSyncRequestToLeftGroup() {
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

    private fun assertValidGroupSyncRequest(group: TestGroup, contact: TestContact) {
        launchActivity<HomeActivity>()

        // Create group sync request message
        val groupRequestSyncMessage = GroupRequestSyncMessage().apply {
            fromIdentity = contact.identity
            toIdentity = myContact.identity
            apiGroupId = group.apiGroupId
            groupCreator = group.groupCreator.identity
        }

        // Process sync request message
        processMessage(groupRequestSyncMessage, contact.identityStore)

        assertEquals(3, sentMessages.size)

        // Check that the first sent message (setup) is correct
        val setupMessage = sentMessages[0] as GroupCreateMessage
        assertArrayEquals(group.members.map { it.identity }.toTypedArray(), setupMessage.members)
        assertEquals(myContact.contact.identity, setupMessage.fromIdentity)
        assertEquals(contact.identity, setupMessage.toIdentity)
        assertEquals(group.groupCreator.identity, setupMessage.groupCreator)
        assertEquals(group.apiGroupId, setupMessage.apiGroupId)

        // Check that the second sent message (rename) is correct
        val renameMessage = sentMessages[1] as GroupRenameMessage
        assertEquals(group.groupName, renameMessage.groupName)
        assertEquals(myContact.identity, renameMessage.fromIdentity)
        assertEquals(contact.identity, renameMessage.toIdentity)
        assertEquals(group.groupCreator.identity, renameMessage.groupCreator)
        assertEquals(group.apiGroupId, renameMessage.apiGroupId)

        assertTrue("Groups with photo are not supported for testing", group.profilePicture == null)

        // Check that the third sent message (set/delete photo) is correct
        val deletePhotoMessage = sentMessages[2] as GroupDeletePhotoMessage
        assertEquals(myContact.identity, deletePhotoMessage.fromIdentity)
        assertEquals(contact.identity, deletePhotoMessage.toIdentity)
        assertEquals(group.groupCreator.identity, deletePhotoMessage.groupCreator)
        assertEquals(group.apiGroupId, deletePhotoMessage.apiGroupId)
    }

    private fun assertIgnoredGroupSyncRequest(group: TestGroup, contact: TestContact) {
        launchActivity<HomeActivity>()

        // Create group sync request message
        val groupRequestSyncMessage = GroupRequestSyncMessage().apply {
            fromIdentity = contact.identity
            toIdentity = myContact.identity
            apiGroupId = group.apiGroupId
            groupCreator = group.groupCreator.identity
        }

        processMessage(groupRequestSyncMessage, contact.identityStore)

        assertEquals(0, sentMessages.size)
    }

    private fun assertLeftGroupSyncRequest(group: TestGroup, contact: TestContact) {
        launchActivity<HomeActivity>()

        // Create group sync request message
        val groupRequestSyncMessage = GroupRequestSyncMessage().apply {
            fromIdentity = contact.identity
            toIdentity = myContact.identity
            apiGroupId = group.apiGroupId
            groupCreator = group.groupCreator.identity
        }

        processMessage(groupRequestSyncMessage, contact.identityStore)

        // Check that a setup message has been sent with empty members list
        assertEquals(1, sentMessages.size)
        val setupMessage = sentMessages.first() as GroupCreateMessage
        assertArrayEquals(emptyArray(), setupMessage.members)
        assertEquals(myContact.contact.identity, setupMessage.fromIdentity)
        assertEquals(contact.identity, setupMessage.toIdentity)
        assertEquals(group.groupCreator.identity, setupMessage.groupCreator)
        assertEquals(group.apiGroupId, setupMessage.apiGroupId)
    }
}

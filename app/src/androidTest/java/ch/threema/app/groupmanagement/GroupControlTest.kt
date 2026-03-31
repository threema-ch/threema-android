package ch.threema.app.groupmanagement

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import ch.threema.app.home.HomeActivity
import ch.threema.app.processors.MessageProcessorProvider
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule

/**
 * A collection of basic data and utility functions to test group control messages. If the common
 * group receive steps should not be executed for a certain message type, the common group step
 * receive methods should be overridden.
 */
abstract class GroupControlTest<T : AbstractGroupMessage> : MessageProcessorProvider() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<HomeActivity>()

    /**
     * Create a message of the tested group message type. This is used to create a message that will
     * be used to test the common group receive steps.
     */
    abstract fun createMessageForGroup(): T

    /**
     * Check common group receive steps: The group could not be found and the user is the creator of
     * the group (as alleged by the message). The message should be discarded.
     */
    @Test
    open fun testCommonGroupReceiveStepUnknownGroupUserCreator() = runTest {
        val (message, identityStore) = getMyUnknownGroupMessage()
        processMessage(message, identityStore)

        // Nothing is expected to be sent
        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    /**
     * Check common group receive steps: The group could not be found and the user is not the
     * creator. In this case, a group sync request should be sent.
     */
    @Test
    open fun testCommonGroupReceiveStepUnknownGroupUserNotCreator() = runTest {
        val (message, identityStore) = getUnknownGroupMessage()
        processMessage(message, identityStore)

        val firstMessage = sentMessagesInsideTask.poll() as GroupSyncRequestMessage
        assertEquals(message.groupCreator, firstMessage.toIdentity)
        assertEquals(message.toIdentity, firstMessage.fromIdentity)
        assertEquals(message.apiGroupId, firstMessage.apiGroupId)
        assertEquals(message.groupCreator, firstMessage.groupCreator)

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    /**
     * Check common group receive steps: The group is marked as left and the user is the creator of
     * the group. In this case, a group setup with an empty member list should be sent back to the
     * sender.
     */
    @Test
    open fun testCommonGroupReceiveStepLeftGroupUserCreator() = runTest {
        val (message, identityStore) = getMyLeftGroupMessage()
        processMessage(message, identityStore)

        // Check that empty sync is sent.
        val firstMessage = sentMessagesInsideTask.poll() as GroupSetupMessage
        assertEquals(message.fromIdentity, firstMessage.toIdentity)
        assertEquals(myContact.identity, firstMessage.fromIdentity)
        assertEquals(message.apiGroupId, firstMessage.apiGroupId)
        assertEquals(message.groupCreator, firstMessage.groupCreator)
        assertContentEquals(emptyArray<String>(), firstMessage.members)

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    /**
     * Check common group receive steps: The group is marked left and the user is not the creator of
     * the group. In this case, a group leave should be sent back to the sender.
     */
    @Test
    open fun testCommonGroupReceiveStepLeftGroupUserNotCreator() = runTest {
        // First, test the common group receive steps for a message from the group creator
        val (firstIncomingMessage, firstIdentityStore) = getLeftGroupMessageFromCreator()
        processMessage(firstIncomingMessage, firstIdentityStore)

        // Check that a group leave is sent back to the sender
        val firstSentMessage = sentMessagesInsideTask.poll() as GroupLeaveMessage
        assertEquals(firstIncomingMessage.fromIdentity, firstSentMessage.toIdentity)
        assertEquals(myContact.identity, firstSentMessage.fromIdentity)
        assertEquals(firstIncomingMessage.apiGroupId, firstSentMessage.apiGroupId)
        assertEquals(firstIncomingMessage.groupCreator, firstSentMessage.groupCreator)

        assertTrue(sentMessagesInsideTask.isEmpty())

        // Second, test the common group receive steps for a message from a group member
        val (secondIncomingMessage, secondIdentityStore) = getLeftGroupMessage()
        processMessage(secondIncomingMessage, secondIdentityStore)

        // Check that a group leave is sent back to the sender
        val secondSentMessage = sentMessagesInsideTask.poll() as GroupLeaveMessage
        assertEquals(secondIncomingMessage.fromIdentity, secondSentMessage.toIdentity)
        assertEquals(myContact.identity, secondSentMessage.fromIdentity)
        assertEquals(secondIncomingMessage.apiGroupId, secondSentMessage.apiGroupId)
        assertEquals(secondIncomingMessage.groupCreator, secondSentMessage.groupCreator)

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    /**
     * Check common group receive steps: The sender is not a member of the group and the user is the
     * creator of the group. In this case, a group setup with an empty members list should be sent
     * back to the sender.
     */
    @Test
    open fun testCommonGroupReceiveStepSenderNotMemberUserCreator() = runTest {
        val (message, identityStore) = getSenderNotMemberOfMyGroupMessage()
        processMessage(message, identityStore)

        // Check that a group setup with empty member list is sent back to the sender
        val firstMessage = sentMessagesInsideTask.poll() as GroupSetupMessage
        assertEquals(message.fromIdentity, firstMessage.toIdentity)
        assertEquals(myContact.identity, firstMessage.fromIdentity)
        assertEquals(message.apiGroupId, firstMessage.apiGroupId)
        assertEquals(message.groupCreator, firstMessage.groupCreator)
        assertContentEquals(emptyArray<String>(), firstMessage.members)

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    /**
     * Check common group receive steps: The sender is not a member of the group and the user is not
     * the creator of the group. The message should be discarded.
     */
    @Test
    open fun testCommonGroupReceiveStepSenderNotMemberUserNotCreator() = runTest {
        val (message, identityStore) = getSenderNotMemberMessage()
        processMessage(message, identityStore)

        // Check that a group sync request has been sent to the creator of the group
        val firstMessage = sentMessagesInsideTask.poll() as GroupSyncRequestMessage
        assertEquals(message.groupCreator, firstMessage.toIdentity)
        assertEquals(myContact.identity, firstMessage.fromIdentity)
        assertEquals(message.apiGroupId, firstMessage.apiGroupId)
        assertEquals(message.groupCreator, firstMessage.groupCreator)

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    /**
     * Get a group message where the user is the creator (as alleged by the received message).
     * Common Group Receive Step 2.1
     */
    private fun getMyUnknownGroupMessage() = createMessageForGroup().apply {
        enrich(myUnknownGroup)
        // Set from identity to a different identity than myself. Note that this may have an effect
        // on the group creator for messages that are wrapped in a group-creator-container.
        fromIdentity = contactA.identity
    } to myUnknownGroup.groupCreator.identityStore

    /**
     * Get a group message in an unknown group.
     * Common Group Receive Step 2.2
     */
    private fun getUnknownGroupMessage() = createMessageForGroup().apply {
        enrich(groupAUnknown)
    } to groupAUnknown.groupCreator.identityStore

    /**
     * Get a group message that is marked 'left' where the user is the creator.
     * Common Group Receive Step 3.1
     */
    private fun getMyLeftGroupMessage() = createMessageForGroup().apply {
        enrich(myLeftGroup)
        fromIdentity = contactA.identity
    } to contactA.identityStore

    /**
     * Get a group message that is marked 'left' from a group member.
     * Common Group Receive Step 3.2
     */
    private fun getLeftGroupMessage() = createMessageForGroup().apply {
        enrich(groupALeft)
    } to groupALeft.groupCreator.identityStore

    /**
     * Get a group message that is marked 'left' from the group creator.
     * Common Group Receive Step 3.2
     */
    private fun getLeftGroupMessageFromCreator() = createMessageForGroup().apply {
        enrich(groupALeft)
    } to groupALeft.groupCreator.identityStore

    /**
     * Get a group message from a sender that is no member of the group where the user is the
     * creator.
     * Common Group Receive Step 4.1
     */
    private fun getSenderNotMemberOfMyGroupMessage() = createMessageForGroup().apply {
        enrich(myGroup)
        fromIdentity = contactC.identity
    } to contactC.identityStore

    /**
     * Get a group message from a sender that is no member of the group.
     * Common Group Receive Step 4.2
     */
    private fun getSenderNotMemberMessage() = createMessageForGroup().apply {
        enrich(groupA)
        fromIdentity = contactB.identity
    } to contactB.identityStore

    private fun AbstractGroupMessage.enrich(group: TestGroup) {
        apiGroupId = group.apiGroupId
        groupCreator = group.groupCreator.identity
        fromIdentity = group.groupCreator.identity
        toIdentity = myContact.identity
    }

    protected fun <T> Iterable<T>.replace(original: T, new: T) =
        map { if (it == original) new else it }
}

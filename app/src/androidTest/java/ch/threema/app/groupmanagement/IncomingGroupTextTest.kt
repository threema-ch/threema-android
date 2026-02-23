package ch.threema.app.groupmanagement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ch.threema.app.DangerousTest
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

/**
 * Tests that the common group receive steps are executed for a group text message.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupTextTest : GroupControlTest<GroupTextMessage>() {
    @Test
    fun testForwardSecureTextMessages() = runBlocking {
        val firstMessage = GroupTextMessage()
        firstMessage.fromIdentity = contactA.identity
        firstMessage.toIdentity = myContact.identity
        firstMessage.text = "First"
        firstMessage.groupCreator = groupA.groupCreator.identity
        firstMessage.apiGroupId = groupA.apiGroupId

        // We enforce forward secure messages in the TestTaskCodec and forward security status
        // listener. Therefore it is sufficient to test that processing a message succeeds.
        processMessage(firstMessage, contactA.identityStore)

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    override fun createMessageForGroup(): GroupTextMessage {
        return GroupTextMessage().apply { text = "Group text message" }
    }
}

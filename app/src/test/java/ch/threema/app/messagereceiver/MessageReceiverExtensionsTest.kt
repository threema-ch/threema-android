package ch.threema.app.messagereceiver

import ch.threema.storage.models.ContactModel
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageReceiverExtensionsTest {
    @Test
    fun `direct chat with gateway contact is considered a gateway chat`() {
        val messageReceiverMock = mockk<ContactMessageReceiver>()
        val contactModel = ContactModel.create("*TESTING", MOCK_PUBLIC_KEY)
        every { messageReceiverMock.contact } returns contactModel

        assertTrue(messageReceiverMock.isGatewayChat())
    }

    @Test
    fun `direct chat with non-gateway contact is not considered a gateway chat`() {
        val messageReceiverMock = mockk<ContactMessageReceiver>()
        val contactModel = ContactModel.create("TESTUSER", MOCK_PUBLIC_KEY)
        every { messageReceiverMock.contact } returns contactModel

        assertFalse(messageReceiverMock.isGatewayChat())
    }

    @Test
    fun `group chat is not considered a gateway chat`() {
        val messageReceiverMock = mockk<GroupMessageReceiver>()

        assertFalse(messageReceiverMock.isGatewayChat())
    }

    @Test
    fun `distribution list chat is not considered a gateway chat`() {
        val messageReceiverMock = mockk<DistributionListMessageReceiver>()

        assertFalse(messageReceiverMock.isGatewayChat())
    }

    companion object {
        private val MOCK_PUBLIC_KEY = ByteArray(32)
    }
}

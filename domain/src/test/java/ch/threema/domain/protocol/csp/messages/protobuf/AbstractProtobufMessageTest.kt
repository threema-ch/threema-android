package ch.threema.domain.protocol.csp.messages.protobuf

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals

class AbstractProtobufMessageTest {
    @Test
    fun testGetBody() {
        val bytes = byteArrayOf(0, 1, 2)
        val protobufDataStub = mockk<ProtobufDataInterface<*>> {
            every { toProtobufBytes() } returns bytes
        }
        val message = object : AbstractProtobufMessage<ProtobufDataInterface<*>>(3, protobufDataStub) {
            override fun getType() = 0

            override fun getMinimumRequiredForwardSecurityVersion() = null

            override fun allowUserProfileDistribution() = false

            override fun exemptFromBlocking() = false

            override fun createImplicitlyDirectContact() = false

            override fun protectAgainstReplay() = false

            override fun reflectIncoming() = false

            override fun reflectOutgoing() = false

            override fun reflectSentUpdate() = false

            override fun sendAutomaticDeliveryReceipt() = false

            override fun bumpLastUpdate() = false
        }

        assertContentEquals(bytes, message.body)
    }
}

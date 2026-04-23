package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface
import ch.threema.protobuf.csp.e2e.DeleteMessage
import com.google.protobuf.InvalidProtocolBufferException
import java.util.Objects

class DeleteMessageData(
    val messageId: Long,
) : ProtobufDataInterface<DeleteMessage> {
    companion object {
        @JvmStatic
        fun fromProtobuf(rawProtobufMessage: ByteArray): DeleteMessageData {
            try {
                val protobufMessage = DeleteMessage.parseFrom(rawProtobufMessage)
                return DeleteMessageData(
                    protobufMessage.messageId,
                )
            } catch (e: InvalidProtocolBufferException) {
                throw BadMessageException("Invalid DeleteMessage protobuf data", e)
            } catch (e: IllegalArgumentException) {
                throw BadMessageException("Could not create DeleteMessage data", e)
            }
        }
    }

    override fun toProtobufMessage(): DeleteMessage {
        return DeleteMessage.newBuilder()
            .setMessageId(messageId)
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeleteMessageData) return false

        if (messageId != other.messageId) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(messageId)
    }
}

package ch.threema.domain.protocol.connection.data

internal interface TypedMessage {
    val type: String
}

internal sealed interface InboundL1Message : TypedMessage

internal sealed interface InboundL2Message : TypedMessage
internal sealed interface OutboundL2Message : TypedMessage

internal sealed interface InboundL3Message : TypedMessage
internal sealed interface OutboundL3Message : TypedMessage

internal sealed interface InboundL4Message : TypedMessage {
    fun toInboundMessage(): InboundMessage
}

internal sealed interface OutboundL4Message : TypedMessage

internal sealed interface OutboundL5Message : TypedMessage

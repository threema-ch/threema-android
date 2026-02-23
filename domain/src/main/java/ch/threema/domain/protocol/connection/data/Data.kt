package ch.threema.domain.protocol.connection.data

import ch.threema.base.utils.Utils

sealed interface InboundMessage {
    val payloadType: UByte
}

sealed interface OutboundMessage {
    val payloadType: UByte
}

fun UByte.toHex(): String {
    return Utils.byteToHex(toByte(), false, false)
}

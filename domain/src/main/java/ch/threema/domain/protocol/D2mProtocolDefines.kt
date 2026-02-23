package ch.threema.domain.protocol

object D2mProtocolDefines {
    const val D2M_PROTOCOL_VERSION_MIN = 0u
    const val D2M_PROTOCOL_VERSION_MAX = 0u

    const val D2M_FRAME_MIN_BYTES_LENGTH = 4
    const val D2M_FRAME_MAX_BYTES_LENGTH = 65536

    const val DGK_LENGTH_BYTES = 32
}

object D2mPayloadType {
    // CSP proxying
    const val PROXY: UByte = 0x00u

    // Handshake
    const val SERVER_HELLO: UByte = 0x10u
    const val CLIENT_HELLO: UByte = 0x11u
    const val SERVER_INFO: UByte = 0x12u

    // States
    const val REFLECTION_QUEUE_DRY: UByte = 0x20u
    const val ROLE_PROMOTED_TO_LEADER: UByte = 0x21u

    // Device Management
    const val GET_DEVICES_INFO: UByte = 0x30u
    const val DEVICES_INFO: UByte = 0x31u
    const val DROP_DEVICE: UByte = 0x32u
    const val DROP_DEVICE_ACK: UByte = 0x33u
    const val SET_SHARED_DEVICE_DATA: UByte = 0x34u

    // Transactions
    const val BEGIN_TRANSACTION: UByte = 0x40u
    const val BEGIN_TRANSACTION_ACK: UByte = 0x41u
    const val COMMIT_TRANSACTION: UByte = 0x42u
    const val COMMIT_TRANSACTION_ACK: UByte = 0x43u
    const val TRANSACTION_REJECTED: UByte = 0x44u
    const val TRANSACTION_ENDED: UByte = 0x45u

    // Reflection
    const val REFLECT: UByte = 0x80u
    const val REFLECT_ACK: UByte = 0x81u
    const val REFLECTED: UByte = 0x82u
    const val REFLECTED_ACK: UByte = 0x83u
}

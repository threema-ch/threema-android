package ch.threema.domain.protocol.connection.layer

internal data class ServerConnectionLayers(
    val layer1Codec: Layer1Codec,
    val layer2Codec: Layer2Codec,
    val layer3Codec: Layer3Codec,
    val layer4Codec: Layer4Codec,
    val layer5Codec: Layer5Codec,
)

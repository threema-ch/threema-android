package ch.threema.domain.onprem

data class OnPremConfigChat(
    val hostname: String,
    val ports: IntArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?) =
        other is OnPremConfigChat &&
            hostname == other.hostname &&
            ports.contentEquals(other.ports) &&
            publicKey.contentEquals(other.publicKey)

    override fun hashCode() =
        hostname.hashCode() + (ports.contentHashCode() * 31 + publicKey.contentHashCode()) * 31
}

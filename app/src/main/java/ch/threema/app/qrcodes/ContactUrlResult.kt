package ch.threema.app.qrcodes

import ch.threema.common.secureContentEquals
import ch.threema.domain.types.IdentityString
import java.time.Instant

data class ContactUrlResult(
    val identity: IdentityString,
    val publicKey: ByteArray,
    val expiration: Instant?,
) {
    fun isExpired(now: Instant) =
        expiration != null && expiration < now

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ContactUrlResult
        if (identity != other.identity) return false
        if (!publicKey.secureContentEquals(other.publicKey)) return false
        if (expiration != other.expiration) return false
        return true
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + expiration.hashCode()
        return result
    }
}

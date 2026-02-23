package ch.threema.app.qrcodes

import ch.threema.base.crypto.NaCl
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.Identity
import java.time.Instant

class ContactUrlUtil {
    fun parse(scanResult: String): ContactUrlResult? {
        if (!scanResult.startsWith(PREFIX)) {
            return null
        }
        val parts = scanResult.removePrefix(PREFIX).split(',')
        if (parts.size < 2) {
            return null
        }
        val identity = parts[0]
        if (identity.length != ProtocolDefines.IDENTITY_LEN) {
            return null
        }
        val publicKeyHexString = parts[1]
        if (publicKeyHexString.length != NaCl.PUBLIC_KEY_BYTES * 2) {
            return null
        }
        val publicKey = try {
            publicKeyHexString.hexToByteArray()
        } catch (_: IllegalArgumentException) {
            return null
        }
        val expiration = parts.getOrNull(2)?.toLongOrNull()
            ?.let(Instant::ofEpochSecond)
        return ContactUrlResult(
            identity = identity,
            publicKey = publicKey,
            expiration = expiration,
        )
    }

    fun generate(identity: Identity, publicKey: ByteArray): String =
        "$PREFIX$identity,${publicKey.toHexString()}"

    companion object {
        private const val PREFIX = "3mid:"
    }
}

package ch.threema.app.voip.groupcall

import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.emptyByteArray
import ch.threema.libthreema.CryptoException
import ch.threema.libthreema.blake2bMac256

private val logger = getThreemaLogger("CryptoCallUtils")

object CryptoCallUtils {

    private const val PERSONAL = "3ma-call"
    const val SALT_CALL_ID = "i"
    const val SALT_GCKH = "#"
    const val SALT_GCHK = "h"
    const val SALT_GCSK = "s"
    const val SALT_CURRENT_PCMK = "m'"

    @Throws(ThreemaException::class)
    fun gcBlake2b256(
        key: ByteArray? = null,
        salt: String,
        data: ByteArray = emptyByteArray(),
    ): ByteArray = try {
        blake2bMac256(
            key = key,
            personal = PERSONAL.encodeToByteArray(),
            salt = salt.encodeToByteArray(),
            data = data,
        )
    } catch (cryptoException: CryptoException.InvalidParameter) {
        logger.error("Failed to compute blake2b hash", cryptoException)
        throw ThreemaException("Failed to compute blake2b hash", cryptoException)
    }
}

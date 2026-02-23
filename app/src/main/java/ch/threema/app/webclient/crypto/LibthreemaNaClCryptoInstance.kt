package ch.threema.app.webclient.crypto

import androidx.annotation.AnyThread
import ch.threema.base.crypto.NaCl
import org.saltyrtc.client.crypto.CryptoException
import org.saltyrtc.client.crypto.CryptoInstance

/**
 *  @throws CryptoException In case no NaCl instance could be instantiated
 */
@AnyThread
class LibthreemaNaClCryptoInstance internal constructor(
    ownPrivateKey: ByteArray,
    otherPublicKey: ByteArray,
) : CryptoInstance {

    private val nacl: NaCl =
        runCatching {
            NaCl(ownPrivateKey, otherPublicKey)
        }.getOrElse { throwable ->
            throw CryptoException("Could not create NaCl instance", throwable)
        }

    @Throws(CryptoException::class)
    override fun encrypt(data: ByteArray, nonce: ByteArray): ByteArray =
        runCatching {
            nacl.encrypt(
                data = data,
                nonce = nonce,
            )
        }.getOrElse { cause ->
            throw CryptoException("Could not encrypt data", cause)
        }

    @Throws(CryptoException::class)
    override fun decrypt(data: ByteArray, nonce: ByteArray): ByteArray =
        runCatching {
            nacl.decrypt(
                data = data,
                nonce = nonce,
            )
        }.getOrElse { throwable ->
            throw CryptoException("Could not decrypt data", throwable)
        }
}

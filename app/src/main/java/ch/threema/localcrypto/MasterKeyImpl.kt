/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.localcrypto

import ch.threema.common.generateRandomBytes
import ch.threema.common.models.CryptographicByteArray
import ch.threema.common.secureRandom
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MasterKeyImpl(
    value: ByteArray,
    private val random: SecureRandom = secureRandom(),
) : CryptographicByteArray(value), InvalidateableMasterKey {
    @Volatile
    private var isValid = true

    override fun isValid() = isValid

    override fun invalidate() {
        isValid = false
        value.fill(0)
    }

    @Throws(IOException::class)
    override fun decrypt(inputStream: InputStream): InputStream {
        val iv = ByteArray(IV_LENGTH)
        val readLen = inputStream.read(iv)
        if (readLen == -1) {
            throw IOException("Bad encrypted file (empty)")
        } else if (readLen != IV_LENGTH) {
            throw IOException("Bad encrypted file (invalid IV length $readLen)")
        }
        val cipher = getCipher(iv, mode = Cipher.DECRYPT_MODE)
        return CipherInputStream(inputStream, cipher)
    }

    @Throws(IOException::class)
    override fun encrypt(outputStream: OutputStream): OutputStream {
        val iv = random.generateRandomBytes(IV_LENGTH)
        outputStream.write(iv)
        val cipher = getCipher(iv, mode = Cipher.ENCRYPT_MODE)
        return CipherOutputStream(outputStream, cipher)
    }

    private fun getCipher(iv: ByteArray, mode: Int): Cipher {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val ivParams = IvParameterSpec(iv)
        cipher.init(mode, createKeySpec(), ivParams)
        return cipher
    }

    private fun createKeySpec(): SecretKeySpec {
        val keySpec = SecretKeySpec(value, SECRET_KEY_ALGORITHM)

        // SecretKeySpec internally creates a copy of the key value, but it might be that the key was invalidated during this copy operation,
        // leaving the key spec in an invalid state. Therefore, we check afterwards whether the key is still valid and abort otherwise.
        ensureValid()

        return keySpec
    }

    private fun ensureValid() {
        if (!isValid()) {
            throw IOException("Master key is no longer valid")
        }
    }

    override fun toString() = "[Master Key]"

    companion object {
        private const val CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val SECRET_KEY_ALGORITHM = "AES"
        private const val IV_LENGTH = 16
    }
}

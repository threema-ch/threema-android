package ch.threema.base.crypto;

import org.slf4j.Logger;

import java.security.SecureRandom;

import androidx.annotation.Nullable;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.libthreema.CryptoException;

import static ch.threema.common.SecureRandomExtensionsKt.generateRandomBytes;
import static ch.threema.common.SecureRandomExtensionsKt.secureRandom;

public class SymmetricEncryptionService {

    private static final Logger logger = getThreemaLogger("SymmetricEncryptionService");

    public byte[] generateSymmetricKey() {
        return generateRandomBytes(secureRandom(), NaCl.SYMM_KEY_BYTES);
    }

    /**
     * Decrypt a symmetrically encrypted array of bytes using the provided key and nonce.
     * Encryption takes place inplace in order to save memory. Therefore, the original array will
     * be modified and serves as output of the decryption result.
     *
     * @param data input and output data; will be modified
     */
    public void decryptInplace(byte[] data, byte[] key, byte[] nonce) throws IllegalArgumentException, CryptoException {
        NaCl.symmetricDecryptDataInPlace(data, key, nonce);
    }

    /**
     * Decrypt a symmetrically encrypted array of bytes using the provided key and nonce.
     *
     * @return the decrypted data or {@code null} if decryption failed
     */
    public @Nullable byte[] decrypt(byte[] data, byte[] key, byte[] nonce) {
        try {
            return NaCl.symmetricDecryptData(data, key, nonce);
        } catch (CryptoException cryptoException) {
            logger.error("Failed to decrypt data", cryptoException);
            return null;
        }
    }

    /**
     * Generates a symmetric key and encrypts the data.
     * Encryption is executed inplace to save memory. Therefore, the original data array will be modified.
     * <p>
     * The generated key will be returned with the encryption result.
     *
     * @param data the bytes to encrypt; will be altered during inplace encryption
     * @return The encrypted data alongside the used symmetric encryption key
     */
    public SymmetricEncryptionResult encryptInplace(byte[] data, byte[] nonce) {
        final byte[] key = generateSymmetricKey();
        return encryptInplace(data, key, nonce);
    }

    /**
     * Encrypts data inplace to save memory. Therefore the original data array will be modified.
     * <p>
     * The generated key will be returned with the encryption result.
     *
     * @param data the bytes to encrypt; will be altered during inplace encryption
     * @return The encrypted data alongside the used symmetric encryption key
     */
    public SymmetricEncryptionResult encryptInplace(byte[] data, byte[] key, byte[] nonce) {
        try {
            NaCl.symmetricEncryptDataInPlace(data, key, nonce);
            return new SymmetricEncryptionResult(data, key);
        } catch (IllegalArgumentException | CryptoException exception) {
            logger.error("Failed to encrypt data in-place", exception);
            return new SymmetricEncryptionResult(new byte[]{}, key);
        }
    }

    /**
     * Encrypts file data without altering the original data.
     *
     * @param data the bytes to encrypt
     * @return The encrypted data alongside the used symmetric encryption key
     */
    public SymmetricEncryptionResult encrypt(byte[] data, byte[] key, byte[] nonce) {
        byte[] encrypted;
        try {
            encrypted = NaCl.symmetricEncryptData(data, key, nonce);
        } catch (CryptoException cryptoException) {
            logger.error("Failed to encrypt data", cryptoException);
            encrypted = new byte[]{};
        }
        return new SymmetricEncryptionResult(encrypted, key);
    }
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
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

package ch.threema.localcrypto;

import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.AtomicFile;
import ch.threema.domain.libthreema.LibthreemaJavaBridge;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.libthreema.CryptoException;

import static ch.threema.libthreema.LibthreemaKt.scrypt;

/**
 * This class wraps the master key used to encrypt locally stored application data.
 * <p>
 * The master key has a length of 32 bytes. It is generated randomly the first time the
 * application is started and never changes (not even if the user changes the password)
 * to prevent the need to re-encrypt all stored data.
 * <p>
 * The master key is saved in a file and can be optionally protected with a passphrase.
 * <p>
 * Key file format:
 * <ul>
 *     <li>protection type (1 byte)
 *     		<ul>
 *     			<li>0 = unprotected</li>
 *          	<li>1 = protected with PBKDF2 passphrase</li>
 *          	<li>2 = protected with Scrypt passphrase</li>
 *     		</ul>
 *     </li>
 *     <li>key (32 bytes)</li>
 *     <li>salt (8 bytes)</li>
 *     <li>verification (4 bytes = start of SHA1 hash of master key)</li>
 * </ul>
 */
public class MasterKey {
    private static final Logger logger = LoggingUtil.getThreemaLogger("MasterKey");

    public static final int KEY_LENGTH = 32;

    private static final int SALT_LENGTH = 8;
    private static final int VERIFICATION_LENGTH = 4;
    private static final int IV_LENGTH = 16;

    private static final int PBKDF2_ITERATION_COUNT = 10000;

    // Static key used for obfuscating the stored master key.
    //
    // Introduced in really old app versions (long before APIs like the Android keystore system
    // existed) back when the app was closed source and retained for compatibility; of course pretty
    // pointless in an open source app, but does no harm either)
    private static final byte[] DEPRECATED_OBFUSCATION_KEY = new byte[]{
        (byte) 0x95, (byte) 0x0d, (byte) 0x26, (byte) 0x7a, (byte) 0x88, (byte) 0xea, (byte) 0x77, (byte) 0x10,
        (byte) 0x9c, (byte) 0x50, (byte) 0xe7, (byte) 0x3f, (byte) 0x47, (byte) 0xe0, (byte) 0x69, (byte) 0x72,
        (byte) 0xda, (byte) 0xc4, (byte) 0x39, (byte) 0x7c, (byte) 0x99, (byte) 0xea, (byte) 0x7e, (byte) 0x67,
        (byte) 0xaf, (byte) 0xfd, (byte) 0xdd, (byte) 0x32, (byte) 0xda, (byte) 0x35, (byte) 0xf7, (byte) 0x0c,
    };

    private final File keyFile;

    private byte[] masterKey;
    private boolean locked;

    private ProtectionType protectionType;
    private byte[] protectedKey;
    private byte[] salt;
    private byte[] verification;

    private boolean newKeyNotWrittenYet;

    private final SecureRandom random;

    /**
     * Initialise master key with the given key file. If the file exists, the key from it
     * is read; otherwise a new random key is generated and written to the key file.
     * <p>
     * A passphrase can be specified that is used to protect the master key if a new one needs
     * to be generated. The purpose of this is to avoid writing the unprotected master key to the
     * file, where it could potentially be retrieved even if a passphrase is later set.
     *
     * @param keyFile       e.g. "key.dat" in application directory
     * @param newPassphrase passphrase in case a new key file has to be created (null for no passphrase)
     * @param deferWrite    if true and a new master key needs to be created (key file does not exist) without a
     *                      passphrase, it will not be written yet.
     *                      Call {@link #setPassphrase(char[])} to trigger the write.
     */
    public MasterKey(File keyFile, char[] newPassphrase, boolean deferWrite) throws IOException {
        this.keyFile = keyFile;
        random = new SecureRandom();

        if (keyFile.exists()) {
            readFile();

            if (locked && newPassphrase != null) {
                unlock(newPassphrase);
            }
        } else {
            /* no key file - generate new key */
            generateKey();

            try {
                if (newPassphrase != null || !deferWrite) {
                    setPassphrase(newPassphrase);
                }
            } catch (MasterKeyLockedException e) {
                /* will never happen */
            }
        }
    }

    /**
     * Return whether the master key is currently locked with a passphrase. If it is locked,
     * the passphrase must be provided using setPassphrase() before the master key can be used.
     *
     * @return is locked true/false
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Lock the master key if a passphrase has been set.
     *
     * @return true on success, false if no passphrase is set
     */
    public boolean lock() {
        if (isProtected()) {
            locked = true;

            /* zeroize master key */
            if (masterKey != null) {
                Arrays.fill(masterKey, (byte) 0);
                masterKey = null;
            }

            return true;
        }
        return false;
    }

    /**
     * Unlock the master key with the supplied passphrase.
     *
     * @param passphrase passphrase for unlocking
     * @return true on success, false if the passphrase is wrong or an error occurred
     */
    public boolean unlock(char[] passphrase) {
        if (!locked) {
            return true;
        }

        /* derive encryption key from passphrase */
        try {
            byte[] passphraseKey = derivePassphraseKey(passphrase, salt, protectionType);

            /* decrypt master key with passphrase key */
            masterKey = new byte[KEY_LENGTH];
            for (int i = 0; i < KEY_LENGTH; i++) {
                masterKey[i] = (byte) (protectedKey[i] ^ passphraseKey[i]);
            }

            /* verify key */
            byte[] myVerification = calcVerification(masterKey);
            if (!MessageDigest.isEqual(myVerification, verification)) {
                masterKey = null;
                return false;
            }

            locked = false;

            /* Upgrade to Scrypt if necessary */
            if (protectionType == ProtectionType.PBKDF2) {
                logger.info("Upgrading passphrase protection from PBKDF2 to Scrypt");
                setPassphrase(passphrase);
                logger.info("Upgraded passphrase protection from PBKDF2 to Scrypt");
            }

            return true;
        } catch (Exception e) {
            logger.error("Exception", e);
            return false;
        }
    }

    /**
     * Check if the supplied passphrase is correct. This can be called regardless of
     * whether the master key is currently unlocked, and will not change the lock state.
     * If no passphrase is set, returns true.
     *
     * @param passphrase passphrase to be checked
     * @return true on success, false if the passphrase is wrong or an error occurred
     */
    public boolean checkPassphrase(char[] passphrase) {
        if (!isProtected()) {
            return true;
        }

        try {
            byte[] passphraseKey = derivePassphraseKey(passphrase, salt, protectionType);

            /* decrypt master key with passphrase key */
            byte[] myMasterKey = new byte[KEY_LENGTH];
            for (int i = 0; i < KEY_LENGTH; i++) {
                myMasterKey[i] = (byte) (protectedKey[i] ^ passphraseKey[i]);
            }

            /* verify key */
            byte[] myVerification = calcVerification(myMasterKey);

            return MessageDigest.isEqual(myVerification, verification);
        } catch (Exception e) {
            logger.error("Exception", e);
            return false;
        }
    }

    /**
     * Set or change the passphrase of the master key. The master key must be unlocked first.
     *
     * @param passphrase the new passphrase (null to remove the passphrase)
     */
    public void setPassphrase(@Nullable char[] passphrase) throws MasterKeyLockedException, IOException {
        if (locked) {
            throw new MasterKeyLockedException("Master key is locked");
        }

        if (passphrase == null) {
            if (!isProtected() && !newKeyNotWrittenYet) {
                return;
            }

            /* remove passphrase */
            protectionType = ProtectionType.UNPROTECTED;
            protectedKey = masterKey;

            /* salt is unused/zero in this case */
            salt = new byte[SALT_LENGTH];
        } else {
            /* encrypt current master key and save file again */

            /* derive encryption key from passphrase */
            salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            byte[] passphraseKey = derivePassphraseKey(passphrase, salt, ProtectionType.SCRYPT);

            /* since master key and passphrase key are the same length, we can simply use XOR */
            protectedKey = new byte[KEY_LENGTH];
            for (int i = 0; i < KEY_LENGTH; i++) {
                protectedKey[i] = (byte) (masterKey[i] ^ passphraseKey[i]);
            }

            protectionType = ProtectionType.SCRYPT;
        }
        writeFile();
    }

    /**
     * Returns the raw master key, e.g. for use with external encryption libraries.
     * Never store this key on disk!
     *
     * @return key
     * @throws MasterKeyLockedException if the master key is locked
     */
    public byte[] getKey() throws MasterKeyLockedException {
        if (locked) {
            throw new MasterKeyLockedException("Master key is locked");
        }

        return masterKey;
    }

    /**
     * Wrap an input stream (most commonly a {@link FileInputStream}) with a decryption operation
     * under this master key.
     * <p>
     * Note: CipherInputStream processes data in 512 byte chunks and returns false from available()
     * when a new chunk needs to be decrypted, so one needs to loop to read all the data or use
     * {@link DataInputStream#readFully(byte[])} (length needs to be known in advance).
     * <p>
     * Tip: use {@link org.apache.commons.io.IOUtils#toByteArray(java.io.InputStream)} to slurp a
     * file into a byte array if the length is not known beforehand.
     *
     * @param inputStream the raw ciphertext input stream
     * @return an input stream for reading plaintext data
     * @throws MasterKeyLockedException
     * @throws IOException
     */
    public CipherInputStream getCipherInputStream(@NonNull InputStream inputStream) throws MasterKeyLockedException, IOException {
        CipherInputStream cipherInputStream = null;

        try {
            if (locked) {
                throw new MasterKeyLockedException("Master key is locked");
            }

            /* read IV from input stream */
            byte[] iv = new byte[IV_LENGTH];
            int readLen = inputStream.read(iv);
            if (readLen == -1) {
                throw new IOException("Bad encrypted file (empty)");
            } else if (readLen != IV_LENGTH) {
                throw new IOException("Bad encrypted file (invalid IV length " + readLen + ")");
            }

            Cipher cipher = getDecryptCipher(iv);
            cipherInputStream = new CipherInputStream(inputStream, cipher);

            return cipherInputStream;
        } finally {
            if (cipherInputStream == null) {
                // close the input stream here as long as it's not attached to a CipherInputStream
                inputStream.close();
            }
        }
    }

    /**
     * Wrap an output stream (most commonly a {@link FileOutputStream}) with an encryption operation
     * under this master key.
     *
     * @param outputStream the raw ciphertext output stream
     * @return an output stream for writing plaintext data
     * @throws MasterKeyLockedException
     * @throws IOException
     */
    public CipherOutputStream getCipherOutputStream(OutputStream outputStream) throws MasterKeyLockedException, IOException {
        CipherOutputStream cipherOutputStream = null;
        try {
            if (locked) {
                throw new MasterKeyLockedException("Master key is locked");
            }

            /* generate random IV and write to output stream */
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            outputStream.write(iv);

            Cipher cipher = getEncryptCipher(iv);
            cipherOutputStream = new CipherOutputStream(outputStream, cipher);

            return cipherOutputStream;
        } finally {
            if (cipherOutputStream == null) {
                // close the output stream here as long as it's not attached to a CipherOutputStream
                outputStream.close();
            }
        }
    }

    public Cipher getDecryptCipher(byte[] iv) throws MasterKeyLockedException {
        if (locked) {
            throw new MasterKeyLockedException("Master key is locked");
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            SecretKeySpec keySpec = new SecretKeySpec(masterKey, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParams);
            return cipher;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Cipher getEncryptCipher(byte[] iv) throws MasterKeyLockedException {
        if (locked) {
            throw new MasterKeyLockedException("Master key is locked");
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            SecretKeySpec keySpec = new SecretKeySpec(masterKey, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParams);
            return cipher;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isProtected() {
        return this.protectionType != ProtectionType.UNPROTECTED;
    }

    private void generateKey() {
        /* generate a new random key */
        masterKey = new byte[KEY_LENGTH];
        random.nextBytes(masterKey);
        verification = calcVerification(masterKey);
        protectionType = ProtectionType.UNPROTECTED;
        locked = false;
        newKeyNotWrittenYet = true;
    }

    private void readFile() throws IOException {
        AtomicFile atomicKeyFile = new AtomicFile(keyFile);

        try (DataInputStream dis = new DataInputStream(atomicKeyFile.openRead())) {
            protectionType = ProtectionType.forCode(dis.readUnsignedByte());

            protectedKey = new byte[KEY_LENGTH];
            dis.readFully(protectedKey);

            /* deobfuscation */
            for (int i = 0; i < KEY_LENGTH; i++) {
                protectedKey[i] ^= DEPRECATED_OBFUSCATION_KEY[i];
            }

            salt = new byte[SALT_LENGTH];
            dis.readFully(salt);

            verification = new byte[VERIFICATION_LENGTH];
            dis.readFully(verification);

            if (isProtected()) {
                locked = true;
                masterKey = null;
            } else {
                locked = false;
                masterKey = protectedKey;

                /* verify now */
                byte[] myVerification = calcVerification(masterKey);
                if (!MessageDigest.isEqual(myVerification, verification))
                    throw new IOException("Corrupt key");
            }
        }
    }

    private void writeFile() throws IOException {
        AtomicFile atomicKeyFile = new AtomicFile(keyFile);
        FileOutputStream fos = atomicKeyFile.startWrite();

        try {
            // Note: stream *must not* be closed explicitly (see AtomicFile documentation)
            DataOutputStream dos = new DataOutputStream(fos);
            dos.writeByte(protectionType.getCode());

            byte[] protectedKeyObfusc = new byte[KEY_LENGTH];
            for (int i = 0; i < KEY_LENGTH; i++) {
                protectedKeyObfusc[i] = (byte) (protectedKey[i] ^ DEPRECATED_OBFUSCATION_KEY[i]);
            }
            dos.write(protectedKeyObfusc);

            dos.write(salt);
            dos.write(verification);
            dos.flush();
        } catch (IOException e) {
            atomicKeyFile.failWrite(fos);
            throw e;
        }

        atomicKeyFile.finishWrite(fos);

        newKeyNotWrittenYet = false;
    }

    private static byte[] derivePassphraseKey(
        @NonNull char[] passphrase,
        @NonNull byte[] salt,
        @NonNull ProtectionType protectionType
    ) {
        try {
            switch (protectionType) {
                case PBKDF2:
                    KeySpec keySpec = new PBEKeySpec(passphrase, salt, PBKDF2_ITERATION_COUNT, KEY_LENGTH * 8);
                    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                    return factory.generateSecret(keySpec).getEncoded();
                case SCRYPT:
                    return scrypt(
                        new String(passphrase).getBytes(StandardCharsets.UTF_8),
                        salt,
                        LibthreemaJavaBridge.createScryptParameters(
                            (byte) KEY_LENGTH
                        )
                    );
                default:
                    throw new RuntimeException("Unsupported protection type " + protectionType);
            }
        } catch (GeneralSecurityException | IllegalArgumentException | CryptoException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] calcVerification(byte[] key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(key);
            byte[] digest = md.digest();
            byte[] verification = new byte[VERIFICATION_LENGTH];

            System.arraycopy(digest, 0, verification, 0, VERIFICATION_LENGTH);

            return verification;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private enum ProtectionType {
        UNPROTECTED(0), PBKDF2(1), SCRYPT(2);

        private final int code;

        ProtectionType(int code) {
            this.code = code;
        }

        static ProtectionType forCode(int code) {
            switch (code) {
                case 0:
                    return UNPROTECTED;
                case 1:
                    return PBKDF2;
                case 2:
                    return SCRYPT;
                default:
                    throw new IllegalArgumentException("Bad protection type " + code);
            }
        }

        int getCode() {
            return code;
        }
    }
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.base.crypto;

import com.neilalexander.jnacl.NaCl;

import java.security.SecureRandom;

import androidx.annotation.Nullable;

public class SymmetricEncryptionService {
	public byte[] generateSymmetricKey() {
		final byte[] encryptionKey = new byte[NaCl.SYMMKEYBYTES];
		new SecureRandom().nextBytes(encryptionKey);
		return encryptionKey;
	}

	/**
	 * Decrypt a symmetrically encrypted array of bytes using the provided key and nonce.
	 * Encryption takes place inplace in order to save memory. Therefore, the original array will
	 * be modified and serves as output of the decryption result.
	 *
	 * @param io input and output data; will be modified
	 * @return true when decryption was successful, false otherwise
	 */
	public boolean decryptInplace(byte[] io, byte[] key, byte[] nonce) {
		return NaCl.symmetricDecryptDataInplace(io, key, nonce);
	}

	/**
	 * Decrypt a symmetrically encrypted array of bytes using the provided key and nonce.
	 *
	 * @return the decrypted data or {@code null} if decryption failed
	 */
	public @Nullable byte[] decrypt(byte[] data, byte[] key, byte[] nonce) {
		return NaCl.symmetricDecryptData(data, key, nonce);
	}

	/**
	 * Generates a symmetric key and encrypts the data.
	 * Encryption is executed inplace to save memory. Therefore, the original data array will be modified.
	 *
	 * The generated key will be returned with the encryption result.
	 *
	 * @param data the bytes to encrypt; will be altered during inplace encryption
	 * @return The encrypted data alongside the used symmetric encryption key
	 */
	public SymmetricEncryptionResult encryptInplace(byte[] data, byte[] nonce) {
		byte[] key = generateSymmetricKey();
		return encryptInplace(data, key, nonce);
	}

	/**
	 * Encrypts data inplace to save memory. Therefore the original data array will be modified.
	 *
	 * The generated key will be returned with the encryption result.
	 *
	 * @param data the bytes to encrypt; will be altered during inplace encryption
	 * @return The encrypted data alongside the used symmetric encryption key
	 */
	public SymmetricEncryptionResult encryptInplace(byte[] data, byte[] key, byte[] nonce) {
		NaCl.symmetricEncryptDataInplace(data, key, nonce);
		return new SymmetricEncryptionResult(data, key);
	}

	/**
	 * Encrypts file data without altering the original data.
	 *
	 * @param data the bytes to encrypt
	 * @return The encrypted data alongside the used symmetric encryption key
	 */
	public SymmetricEncryptionResult encrypt(byte[] data, byte[] key, byte[] nonce) {
		final byte[] encrypted = NaCl.symmetricEncryptData(data, key, nonce);
		return new SymmetricEncryptionResult(encrypted, key);
	}
}

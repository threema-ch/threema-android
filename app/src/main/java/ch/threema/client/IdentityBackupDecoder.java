/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

import ch.threema.base.ThreemaException;
import com.neilalexander.jnacl.NaCl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class IdentityBackupDecoder {

	private static final int SALT_LEN = 8;
	private static final int HASH_LEN = 2;
	private static final int PBKDF_ITERATIONS = 100000;

	private final String backup;

	private byte[] publicKey;
	private byte[] privateKey;
	private String identity;

	public IdentityBackupDecoder(String backup) {
		this.backup = backup;
	}

	/**
	 * Decode the identity backup using the given password.
	 *
	 * When this method returns successfully, call the getters to obtain the identity and key
	 * from the decrypted backup.
	 *
	 * @param password password that was used to encrypt the backup (min. 6 characters)
	 * @return true if decryption was successful
	 * @throws ThreemaException if an unexpected crypto error occurs
	 */
	public boolean decode(String password) throws ThreemaException {
		/* Base32 decode - strip undesirable characters first */
		StringBuilder cleanBackup = new StringBuilder();
		for (int i = 0; i < backup.length(); i++) {
			char c = backup.charAt(i);
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '2' && c <= '7'))
				cleanBackup.append(c);
		}

		byte[] backupDecoded = Base32.decode(cleanBackup.toString());

		if (backupDecoded.length != (SALT_LEN + ProtocolDefines.IDENTITY_LEN + NaCl.SECRETKEYBYTES + HASH_LEN))
			throw new ThreemaException("TI001");    /* Invalid backup length */

		/* extract salt */
		byte[] salt = new byte[SALT_LEN];
		System.arraycopy(backupDecoded, 0, salt, 0, SALT_LEN);

		/* derive key */
		try {
			byte[] key = PBKDF2.deriveKey(password.getBytes(StandardCharsets.UTF_8), salt, PBKDF_ITERATIONS, NaCl.STREAMKEYBYTES, "HmacSHA256");

			/* decrypt */
			byte[] encdata = new byte[ProtocolDefines.IDENTITY_LEN + NaCl.SECRETKEYBYTES + HASH_LEN];
			System.arraycopy(backupDecoded, SALT_LEN, encdata, 0, encdata.length);

			byte[] nonce = new byte[] {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
			byte[] decdata = NaCl.streamCryptData(encdata,  key, nonce);

			/* Calculate hash and verify */
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			messageDigest.update(decdata, 0, ProtocolDefines.IDENTITY_LEN + NaCl.SECRETKEYBYTES);
			byte[] sha256bytes = messageDigest.digest();
			byte[] hashCalc = new byte[HASH_LEN];
			System.arraycopy(sha256bytes, 0, hashCalc, 0, HASH_LEN);

			byte[] hashExtract = new byte[HASH_LEN];
			System.arraycopy(decdata, ProtocolDefines.IDENTITY_LEN + NaCl.SECRETKEYBYTES, hashExtract, 0, HASH_LEN);

			if (!Arrays.equals(hashCalc, hashExtract))
				return false;

			/* Decryption successful; extract identity and private key, derive public key */
			identity = new String(decdata, 0, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII);
			privateKey = new byte[NaCl.SECRETKEYBYTES];
			System.arraycopy(decdata, ProtocolDefines.IDENTITY_LEN, privateKey, 0, privateKey.length);
			publicKey = NaCl.derivePublicKey(privateKey);

			return true;

		} catch (Exception e) {
			throw new ThreemaException("TI002", e); /* Backup decryption failed */
		}
	}

	public byte[] getPublicKey() {
		return publicKey;
	}

	public byte[] getPrivateKey() {
		return privateKey;
	}

	public String getIdentity() {
		return identity;
	}
}

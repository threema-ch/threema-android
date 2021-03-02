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

import com.neilalexander.jnacl.NaCl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import ch.threema.base.ThreemaException;

public class IdentityBackupGenerator {

	private static final int PASSWORD_MIN_LEN = 6;
	public static final int SALT_LEN = 8;
	public static final int HASH_LEN = 2;
	public static final int PBKDF_ITERATIONS = 100000;

	private final String identity;
	private final byte[] privateKey;

	public IdentityBackupGenerator(String identity, byte[] privateKey) {
		this.identity = identity;
		this.privateKey = privateKey;

		if (identity.length() != ProtocolDefines.IDENTITY_LEN)
			throw new Error("TI003");  /* Bad identity length */

		if (privateKey.length != NaCl.SECRETKEYBYTES)
			throw new Error("TI004");   /* Bad private key length */
	}

	/**
	 * Generate a Threema identity backup by encrypting the identity and private key
	 * using the given password (minimum 6 characters).
	 *
	 * The backup will be returned in ASCII format and consists of 20 groups of 4
	 * uppercase characters and digits separated by '-'.
	 *
	 * @param password
	 * @return identity backup
	 * @throws ThreemaException if the password is too short or an unexpected crypto error occurs
	 */
	public String generateBackup(String password) throws ThreemaException {

		if (password.length() < PASSWORD_MIN_LEN)
			throw new ThreemaException("TI005");    /* Password too short */

		/* generate random salt (8 bytes) */
		SecureRandom rnd = new SecureRandom();
		byte[] salt = new byte[SALT_LEN];
		rnd.nextBytes(salt);

		try {
			byte[] identityBytes = identity.getBytes(StandardCharsets.US_ASCII);

			/* derive key from password */
			byte[] key = PBKDF2.deriveKey(password.getBytes(StandardCharsets.UTF_8), salt, PBKDF_ITERATIONS, NaCl.STREAMKEYBYTES, "HmacSHA256");

			/* calculate truncated hash (for verification purposes) */
			byte[] idkey = new byte[ProtocolDefines.IDENTITY_LEN + NaCl.SECRETKEYBYTES];
			System.arraycopy(identityBytes, 0, idkey, 0, ProtocolDefines.IDENTITY_LEN);
			System.arraycopy(privateKey, 0, idkey, ProtocolDefines.IDENTITY_LEN, NaCl.SECRETKEYBYTES);

			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			messageDigest.update(idkey);
			byte[] sha256bytes = messageDigest.digest();

			/* prepare bytes to be encrypted */
			byte[] decdata = new byte[idkey.length + HASH_LEN];
			System.arraycopy(idkey, 0, decdata, 0, idkey.length);
			System.arraycopy(sha256bytes, 0, decdata, idkey.length, HASH_LEN);

			/* encrypt */
			byte[] nonce = new byte[] {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
			byte[] encdata = NaCl.streamCryptData(decdata, key, nonce);

			/* prepend salt */
			byte[] outbytes = new byte[SALT_LEN + encdata.length];
			System.arraycopy(salt, 0, outbytes, 0, SALT_LEN);
			System.arraycopy(encdata, 0, outbytes, SALT_LEN, encdata.length);

			/* Base32 encode */
			String base32 = Base32.encode(outbytes);

			/* Add dashes */
			StringBuilder grouped = new StringBuilder();
			for (int i = 0; i < base32.length(); i += 4) {
				int len = 4;
				if ((i + len) > base32.length())
					len = base32.length() - i;

				if (grouped.length() > 0)
					grouped.append('-');
				grouped.append(base32.substring(i, i + len));
			}

			return grouped.toString();

		} catch (Exception e) {
			throw new ThreemaException("Backup encryption failed", e);
		}
	}
}

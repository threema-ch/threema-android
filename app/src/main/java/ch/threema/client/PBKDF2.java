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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class PBKDF2 {

	public static byte[] deriveKey(byte[] password, byte[] salt, int iterationCount, int dkLen, String algorithm)
			throws NoSuchAlgorithmException, InvalidKeyException
	{
		SecretKeySpec keyspec = new SecretKeySpec(password, algorithm);
		Mac prf = Mac.getInstance(algorithm);
		prf.init( keyspec );

		int hLen = prf.getMacLength();
		int l = ((dkLen + hLen - 1) / hLen);
		int r = dkLen - (l-1)*hLen;
		byte[] T = new byte[l * hLen];
		for (int i = 0; i < l; i++) {
			F(T, i*hLen, prf, salt, iterationCount, i+1);
		}

		if (r < hLen) {
			// Incomplete last block
			byte[] DK = new byte[dkLen];
			System.arraycopy(T, 0, DK, 0, dkLen);
			return DK;
		}
		return T;
	}

	private static void F(byte[] dest, int offset, Mac prf, byte[] S, int c, int blockIndex)
	{
		final int hLen = prf.getMacLength();
		byte[] U_r = new byte[ hLen ];
		// U0 = S || INT (i);
		byte[] U_i = new byte[S.length + 4];
		System.arraycopy(S, 0, U_i, 0, S.length);
		INT(U_i, S.length, blockIndex);
		for(int i = 0; i < c; i++) {
			U_i = prf.doFinal(U_i);
			xor( U_r, U_i );
		}

		System.arraycopy(U_r, 0, dest, offset, hLen);
	}

	private static void xor(byte[] dest, byte[] src)
	{
		for(int i = 0; i < dest.length; i++) {
			dest[i] ^= src[i];
		}
	}

	private static void INT(byte[] dest, int offset, int i)
	{
		dest[offset + 0] = (byte) (i / (256 * 256 * 256));
		dest[offset + 1] = (byte) (i / (256 * 256));
		dest[offset + 2] = (byte) (i / (256));
		dest[offset + 3] = (byte) (i);
	}

	// Costructor
	private PBKDF2 () {}

}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

package ch.threema.domain.onprem;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;

import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base64;

public class OnPremConfigVerifier {

	private final byte[][] trustedPublicKeys;

	/**
	 * Create an OPPF verifier with the specified trusted public keys.
	 *
	 * @param trustedPublicKeys The trusted public keys in Base64
	 */
	public OnPremConfigVerifier(String[] trustedPublicKeys) throws IOException {
		this.trustedPublicKeys = new byte[trustedPublicKeys.length][];
		for (int i = 0; i < trustedPublicKeys.length; i++) {
			this.trustedPublicKeys[i] = Base64.decode(trustedPublicKeys[i]);
		}
	}

	/**
	 * Verify an OPPF and return the resulting JSON document.
	 */
	public JSONObject verify(String oppfData) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, SignatureException, JSONException, ThreemaException {
		if (oppfData == null) {
			throw new ThreemaException("OPPF string is empty");
		}

		// Extract signature
		int lfIndex = oppfData.lastIndexOf('\n');
		if (lfIndex == -1) {
			throw new ThreemaException("Bad input OPPF data");
		}

		String jsonData = oppfData.substring(0, lfIndex);
		byte[] sig = Base64.decode(oppfData.substring(lfIndex + 1));

		// Verify signature
		EdDSAPublicKey chosenPublicKey = null;
		boolean valid = false;

		for (byte[] publicKey : trustedPublicKeys) {
			EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
			Signature signature = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
			EdDSAPublicKey edPublicKey = new EdDSAPublicKey(new EdDSAPublicKeySpec(publicKey, spec));
			signature.initVerify(edPublicKey);
			signature.setParameter(EdDSAEngine.ONE_SHOT_MODE);
			signature.update(jsonData.getBytes(StandardCharsets.UTF_8));
			if (signature.verify(sig)) {
				valid = true;
				chosenPublicKey = edPublicKey;
				break;
			}
		}

		if (!valid) {
			throw new ThreemaException("Signature verification failed");
		}

		// Parse the JSON
		JSONObject jsonObject = new JSONObject(jsonData);

		// Check that the version is supported
		if (!jsonObject.getString("version").startsWith("1.")) {
			throw new ThreemaException("Unsupported OPPF version");
		}

		// Check that the signature key matches
		byte[] signatureKey = Base64.decode(jsonObject.getString("signatureKey"));
		if (!Arrays.equals(signatureKey, chosenPublicKey.getA().toByteArray())) {
			// Signature key in JSON does not match supplied public key
			throw new ThreemaException("Signature key does not match supplied public key");
		}

		return jsonObject;
	}
}

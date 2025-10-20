/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import ch.threema.base.utils.Base64
import ch.threema.common.lastLine
import ch.threema.common.secureContentEquals
import ch.threema.common.withoutLastLine
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Signature
import java.security.SignatureException
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.json.JSONException
import org.json.JSONObject

/**
 * @param trustedPublicKeys The trusted public keys in Base64
 */
class OnPremConfigVerifier(
    trustedPublicKeys: Array<String>,
) {
    private val trustedPublicKeys = trustedPublicKeys.map { Base64.decode(it) }

    /**
     * Verify an OPPF and return the resulting JSON document.
     */
    @Throws(ThreemaException::class)
    fun verify(oppfData: String): JSONObject {
        try {
            val jsonString = oppfData.withoutLastLine()
            val signatureData = Base64.decode(oppfData.lastLine())

            // Verify signature
            val publicKey = findMatchingPublicKey(
                messageData = jsonString.toByteArray(),
                signatureData = signatureData,
            )
                ?: throw ThreemaException("Signature verification failed")

            // Parse the JSON
            val jsonObject = JSONObject(jsonString)

            // Check that the version is supported
            if (!jsonObject.getString("version").startsWith("1.")) {
                throw ThreemaException("Unsupported OPPF version")
            }

            // Check that the signature key matches
            val signatureKey = Base64.decode(jsonObject.getString("signatureKey"))
            if (!signatureKey.secureContentEquals(publicKey.a.toByteArray())) {
                // Signature key in JSON does not match supplied public key
                throw ThreemaException("Signature key does not match supplied public key")
            }

            return jsonObject
        } catch (e: NoSuchAlgorithmException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: IOException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: InvalidKeyException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: SignatureException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: JSONException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        }
    }

    private fun findMatchingPublicKey(messageData: ByteArray, signatureData: ByteArray): EdDSAPublicKey? {
        for (publicKey in trustedPublicKeys) {
            val spec: EdDSAParameterSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
            val signature: Signature = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            val edPublicKey = EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, spec))
            signature.initVerify(edPublicKey)
            signature.setParameter(EdDSAEngine.ONE_SHOT_MODE)
            signature.update(messageData)
            if (signature.verify(signatureData)) {
                return edPublicKey
            }
        }
        return null
    }
}

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

package ch.threema.app.qrcodes

import ch.threema.base.crypto.NaCl
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.Identity
import java.time.Instant

class ContactUrlUtil {
    fun parse(scanResult: String): ContactUrlResult? {
        if (!scanResult.startsWith(PREFIX)) {
            return null
        }
        val parts = scanResult.removePrefix(PREFIX).split(',')
        if (parts.size < 2) {
            return null
        }
        val identity = parts[0]
        if (identity.length != ProtocolDefines.IDENTITY_LEN) {
            return null
        }
        val publicKeyHexString = parts[1]
        if (publicKeyHexString.length != NaCl.PUBLIC_KEY_BYTES * 2) {
            return null
        }
        val publicKey = try {
            publicKeyHexString.hexToByteArray()
        } catch (_: IllegalArgumentException) {
            return null
        }
        val expiration = parts.getOrNull(2)?.toLongOrNull()
            ?.let(Instant::ofEpochSecond)
        return ContactUrlResult(
            identity = identity,
            publicKey = publicKey,
            expiration = expiration,
        )
    }

    fun generate(identity: Identity, publicKey: ByteArray): String =
        "$PREFIX$identity,${publicKey.toHexString()}"

    companion object {
        private const val PREFIX = "3mid:"
    }
}

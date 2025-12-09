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

import ch.threema.common.secureContentEquals
import ch.threema.domain.types.Identity
import java.time.Instant

data class ContactUrlResult(
    val identity: Identity,
    val publicKey: ByteArray,
    val expiration: Instant?,
) {
    fun isExpired(now: Instant) =
        expiration != null && expiration < now

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ContactUrlResult
        if (identity != other.identity) return false
        if (!publicKey.secureContentEquals(other.publicKey)) return false
        if (expiration != other.expiration) return false
        return true
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + expiration.hashCode()
        return result
    }
}

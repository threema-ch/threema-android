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

package ch.threema.common.models

import ch.threema.common.secureContentEquals

/**
 * A wrapper around a byte array which holds some cryptographic significance (e.g. an auth token, private key, password, hash, salt, etc.).
 * Using this class provides the following main benefits:
 * 1. since it implements its own equality, it can be used in data classes without needing to override their equals and hashCode, as would be
 * the case with a plain ByteArray
 * 2. protects the actual bytes from being accidentally logged or otherwise printed, due to the custom toString implementation
 */
open class CryptographicByteArray(val value: ByteArray) {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CryptographicByteArray
        return value.secureContentEquals(other.value)
    }

    final override fun hashCode() = value.contentHashCode()

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString() =
        "[${value.size} bytes: ${value.joinToString(separator = ",", limit = 3) { it.toHexString() }}]"

    operator fun component1() = value
}

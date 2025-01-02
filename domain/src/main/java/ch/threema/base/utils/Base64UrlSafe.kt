/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.base.utils

/**
 * Url safe en-/decoding according to https://datatracker.ietf.org/doc/html/rfc3548
 */
object Base64UrlSafe {
    /**
     * Encode with a url safe base 64 alphabet. Padding characters are stripped.
     */
    fun encode(bytes: ByteArray): String {
        return Base64.encodeBytes(bytes)
            .replace("+", "-")
            .replace("/", "_")
            .trimEnd('=')
    }

    /**
     * Decode a url safe base 64 string.
     */
    fun decode(s: String): ByteArray {
        val defaultBase64 = s
            .replace("-", "+")
            .replace("_", "/")

        val padding = when(defaultBase64.length % 4) {
            2 -> "=="
            3 -> "="
            else -> ""
        }

        return Base64.decode("$defaultBase64$padding")
    }
}

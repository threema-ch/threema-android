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

data class OnPremConfigChat(
    val hostname: String,
    val ports: IntArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?) =
        other is OnPremConfigChat &&
            hostname == other.hostname &&
            ports.contentEquals(other.ports) &&
            publicKey.contentEquals(other.publicKey)

    override fun hashCode() =
        hostname.hashCode() + (ports.contentHashCode() * 31 + publicKey.contentHashCode()) * 31
}

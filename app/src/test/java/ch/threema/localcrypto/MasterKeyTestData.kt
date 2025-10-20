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

package ch.threema.localcrypto

import ch.threema.base.utils.Base64
import ch.threema.common.emptyByteArray
import ch.threema.libthreema.blake2bMac256
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretHash

@Suppress("ktlint:standard:max-line-length", "ktlint:standard:property-wrapping")
object MasterKeyTestData {
    val MASTER_KEY = byteArrayOf(0x49.toByte(), 0x4b.toByte(), 0xe0.toByte(), 0xbe.toByte(), 0x35.toByte(), 0xaf.toByte(), 0x77.toByte(), 0xbb.toByte(), 0x12.toByte(), 0x87.toByte(), 0x94.toByte(), 0x1d.toByte(), 0x70.toByte(), 0x32.toByte(), 0x81.toByte(), 0x10.toByte(), 0xaf.toByte(), 0x2e.toByte(), 0xd0.toByte(), 0xae.toByte(), 0x5d.toByte(), 0x19.toByte(), 0x86.toByte(), 0x5b.toByte(), 0x53.toByte(), 0x72.toByte(), 0x25.toByte(), 0xe9.toByte(), 0x17.toByte(), 0x83.toByte(), 0x69.toByte(), 0x0a.toByte())

    val SALT = ByteArray(MasterKeyConfig.ARGON2_SALT_LENGTH) { it.toByte() }

    val REMOTE_SECRET = RemoteSecret(
        Base64.decode("AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="),
    )
    val REMOTE_SECRET_HASH = REMOTE_SECRET.deriveHash()

    private fun RemoteSecret.deriveHash(): RemoteSecretHash =
        RemoteSecretHash(
            blake2bMac256(
                key = value,
                personal = "3ma-rs".toByteArray(),
                salt = "rsh".toByteArray(),
                data = emptyByteArray(),
            ),
        )

    val AUTH_TOKEN = RemoteSecretAuthenticationToken(
        byteArrayOf(
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        ),
    )

    const val WORK_URL = "https://test/"

    object Version1 {
        val PROTECTED_KEY = byteArrayOf(0x23.toByte(), 0xa7.toByte(), 0x59.toByte(), 0x3f.toByte(), 0xf7.toByte(), 0x8e.toByte(), 0xa2.toByte(), 0xef.toByte(), 0xca.toByte(), 0x11.toByte(), 0x8b.toByte(), 0x3a.toByte(), 0xe6.toByte(), 0xc7.toByte(), 0xd0.toByte(), 0x85.toByte(), 0x20.toByte(), 0xd5.toByte(), 0xfa.toByte(), 0x68.toByte(), 0xb3.toByte(), 0x1a.toByte(), 0xd1.toByte(), 0x54.toByte(), 0xf8.toByte(), 0x98.toByte(), 0xd8.toByte(), 0x3c.toByte(), 0x69.toByte(), 0xc6.toByte(), 0xab.toByte(), 0x47.toByte())

        val SALT = byteArrayOf(0xe6.toByte(), 0x30.toByte(), 0xf8.toByte(), 0x49.toByte(), 0x31.toByte(), 0x4f.toByte(), 0xe5.toByte(), 0x7b.toByte())

        val VERIFICATION = byteArrayOf(0xb9.toByte(), 0xff.toByte(), 0x7f.toByte(), 0xe0.toByte())
    }
}

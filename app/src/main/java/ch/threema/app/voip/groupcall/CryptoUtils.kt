/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.voip.groupcall

import ove.crypto.digest.Blake2b
import java.security.SecureRandom

const val PERSONAL = "3ma-call"
const val SALT_CALL_ID = "i"
const val SALT_GCKH = "#"
const val SALT_GCHK = "h"
const val SALT_GCSK = "s"
const val SALT_CURRENT_PCMK = "m'"

fun gcBlake2b(length: Int, key: ByteArray, salt: String, input: ByteArray? = null): ByteArray {
    val params = Blake2b.Param()
    params.digestLength = length
    params.setKey(key)
    params.setSalt(salt.encodeToByteArray())
    params.setPersonal(PERSONAL.encodeToByteArray())
    val digest = Blake2b.Digest.newInstance(params)
    input?.let { digest.update(input) }
    return digest.digest()
}

fun getSecureRandomBytes(length: Int): ByteArray {
    val bytes = ByteArray(length)
    SecureRandom().nextBytes(bytes)
    return bytes
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.emptyByteArray
import ch.threema.libthreema.CryptoException
import ch.threema.libthreema.blake2bMac256

private val logger = getThreemaLogger("CryptoCallUtils")

object CryptoCallUtils {

    private const val PERSONAL = "3ma-call"
    const val SALT_CALL_ID = "i"
    const val SALT_GCKH = "#"
    const val SALT_GCHK = "h"
    const val SALT_GCSK = "s"
    const val SALT_CURRENT_PCMK = "m'"

    @Throws(ThreemaException::class)
    fun gcBlake2b256(
        key: ByteArray? = null,
        salt: String,
        data: ByteArray = emptyByteArray(),
    ): ByteArray = try {
        blake2bMac256(
            key = key,
            personal = PERSONAL.encodeToByteArray(),
            salt = salt.encodeToByteArray(),
            data = data,
        )
    } catch (cryptoException: CryptoException.InvalidParameter) {
        logger.error("Failed to compute blake2b hash", cryptoException)
        throw ThreemaException("Failed to compute blake2b hash", cryptoException)
    }
}

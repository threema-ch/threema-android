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

package ch.threema.domain.libthreema

import ch.threema.libthreema.ScryptParameters

object LibthreemaJavaBridge {

    /**
     * Fixes compiler error: ScryptParameters(byte,int,int,byte) has private access in ScryptParameters.
     *
     * Parameter default values as recommended by [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#scrypt)
     *
     * @see [ch.threema.libthreema.ScryptParameters]
     */
    @JvmStatic
    @JvmOverloads
    fun createScryptParameters(
        logMemoryCost: Byte = 16,
        blockSize: Int = 8,
        parallelism: Int = 1,
        outputLength: Byte,
    ) = ScryptParameters(
        logMemoryCost = logMemoryCost.toUByte(),
        blockSize = blockSize.toUInt(),
        parallelism = parallelism.toUInt(),
        outputLength = outputLength.toUByte(),
    )
}

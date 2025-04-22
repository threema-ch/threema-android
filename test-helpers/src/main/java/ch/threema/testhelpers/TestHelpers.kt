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

package ch.threema.testhelpers

import kotlin.random.Random

/**
 * Generate an array of length `length` and fill it using a non-cryptographically-secure
 * random number generator.
 *
 * (This is fine since it's only a test util.)
 */
fun nonSecureRandomArray(length: Int): ByteArray {
    return Random.nextBytes(length)
}

/**
 * Generate a random Threema ID using a non-cryptographically-secure random number generator.
 *
 * (This is fine since it's only a test util.)
 */
fun randomIdentity(): String {
    val allowedChars = ('A'..'Z') + ('0'..'9')
    return (1..8)
        .map { allowedChars.random() }
        .joinToString("")
}

@Suppress("FunctionName")
fun MUST_NOT_BE_CALLED(): Nothing {
    throw UnsupportedOperationException("This method must not be called")
}

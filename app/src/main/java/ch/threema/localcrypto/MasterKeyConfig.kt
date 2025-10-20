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

object MasterKeyConfig {
    const val KEY_LENGTH = 32

    const val ARGON2_MEMORY_BYTES = 128 * 1024
    const val ARGON2_ITERATIONS = 8
    const val ARGON2_PARALLELIZATION = 1
    const val ARGON2_SALT_LENGTH = 16

    const val SECRET_KEY_LENGTH = 32
    const val NONCE_LENGTH = 24

    const val REMOTE_SECRET_HASH_LENGTH = 32
    const val REMOTE_SECRET_AUTH_TOKEN_LENGTH = 32

    const val VERSION1_VERIFICATION_LENGTH = 4
}

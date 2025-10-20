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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PassphraseStoreTest {
    @Test
    fun `passphrase can be set, read and unset`() {
        val myPassphrase = "Hello".toCharArray()
        val passphraseStore = PassphraseStore()

        // Initially, there is no passphrase
        assertNull(passphraseStore.passphrase)

        // Passphrase can be set
        passphraseStore.passphrase = myPassphrase
        assertContentEquals("Hello".toCharArray(), myPassphrase)

        // Removing the passphrase clears it from memory
        val oldPassphrase = passphraseStore.passphrase!!
        passphraseStore.passphrase = null
        assertNull(passphraseStore.passphrase)
        assertTrue(oldPassphrase.all { it == ' ' })

        // Original passphrase instance is not changed
        assertContentEquals("Hello".toCharArray(), myPassphrase)
    }
}

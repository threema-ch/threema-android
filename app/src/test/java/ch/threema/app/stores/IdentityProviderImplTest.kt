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

package ch.threema.app.stores

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IdentityProviderImplTest {
    @Test
    fun `get non-existing identity`() {
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("identity") } returns null
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock)

        assertNull(identityProvider.getIdentity())
    }

    @Test
    fun `get existing identity`() {
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("identity") } returns "01234567"
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock)

        assertEquals("01234567", identityProvider.getIdentity())
    }

    @Test
    fun `get invalid identity`() {
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("identity") } returns "invalid"
        }

        assertFailsWith<IllegalArgumentException> {
            IdentityProviderImpl(preferenceStoreMock)
        }
    }

    @Test
    fun `set valid identity`() {
        val preferenceStoreMock = mockk<PreferenceStore>(relaxed = true) {
            every { getString("identity") } returns null
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock)

        identityProvider.setIdentity("01234567")

        verify { preferenceStoreMock.save("identity", "01234567") }
        assertEquals("01234567", identityProvider.getIdentity())
    }

    @Test
    fun `set invalid identity`() {
        val preferenceStoreMock = mockk<PreferenceStore>(relaxed = true) {
            every { getString("identity") } returns null
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock)

        assertFailsWith<IllegalArgumentException> {
            identityProvider.setIdentity("invalid")
        }

        verify(exactly = 0) { preferenceStoreMock.save("identity", any<String>()) }
    }
}

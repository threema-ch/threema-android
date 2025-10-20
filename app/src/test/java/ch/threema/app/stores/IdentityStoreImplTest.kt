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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IdentityStoreImplTest {
    @Test
    fun `no identity stored`() {
        val identityProviderMock = mockk<MutableIdentityProvider> {
            every { getIdentity() } returns null
        }
        val preferenceStoreMock = mockk<PreferenceStore>()
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore>()

        val identityStore = IdentityStoreImpl(
            identityProvider = identityProviderMock,
            preferenceStore = preferenceStoreMock,
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            onNicknameChanged = {},
        )

        assertNull(identityStore.getIdentity())
        assertNull(identityStore.getPublicKey())
        assertNull(identityStore.getPrivateKey())
        assertNull(identityStore.getServerGroup())
        assertEquals("", identityStore.getPublicNickname())
    }

    @Test
    fun `restoring valid identity`() {
        val identityProviderMock = mockk<MutableIdentityProvider> {
            every { getIdentity() } returns "01234567"
        }
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("server_group") } returns "foobar"
            every { getBytes("public_key") } returns PUBLIC_KEY
            every { getString("nickname") } returns "Nick"
        }
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore> {
            every { getBytes("private_key") } returns PRIVATE_KEY
        }

        val identityStore = IdentityStoreImpl(
            identityProvider = identityProviderMock,
            preferenceStore = preferenceStoreMock,
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            onNicknameChanged = {},
        )

        assertEquals("01234567", identityStore.getIdentity())
        assertContentEquals(PUBLIC_KEY, identityStore.getPublicKey())
        assertContentEquals(PRIVATE_KEY, identityStore.getPrivateKey())
        assertEquals("foobar", identityStore.getServerGroup())
        assertEquals("Nick", identityStore.getPublicNickname())
    }

    @Test
    fun `restoring valid identity with missing private key`() {
        val identityProviderMock = mockk<MutableIdentityProvider> {
            every { getIdentity() } returns "01234567"
        }
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("server_group") } returns "foobar"
            every { getBytes("public_key") } returns PUBLIC_KEY
            every { getString("nickname") } returns "Nick"
        }
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore> {
            every { getBytes("private_key") } returns ByteArray(0)
        }

        val identityStore = IdentityStoreImpl(
            identityProvider = identityProviderMock,
            preferenceStore = preferenceStoreMock,
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            onNicknameChanged = {},
        )

        assertNull(identityStore.getPrivateKey())
    }

    @Test
    fun `restoring invalid identity`() {
        val identityProviderMock = mockk<MutableIdentityProvider> {
            every { getIdentity() } returns "01234567"
        }
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("server_group") } returns "foobar"
            every { getBytes("public_key") } returns PUBLIC_KEY.copyOfRange(0, 30) // wrong length
            every { getString("nickname") } returns "Nick"
        }
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore> {
            every { getBytes("private_key") } returns PRIVATE_KEY
        }

        assertFailsWith<IllegalArgumentException> {
            IdentityStoreImpl(
                identityProvider = identityProviderMock,
                preferenceStore = preferenceStoreMock,
                encryptedPreferenceStore = encryptedPreferenceStoreMock,
                onNicknameChanged = {},
            )
        }
    }

    @Test
    fun `storing identity`() {
        val identityProviderMock = mockk<MutableIdentityProvider>(relaxed = true) {
            every { getIdentity() } returns null
        }
        val preferenceStoreMock = mockk<PreferenceStore>(relaxed = true)
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore>(relaxed = true)
        var onNickNameChangedCalledWith: String? = null
        val identityStore = IdentityStoreImpl(
            identityProvider = identityProviderMock,
            preferenceStore = preferenceStoreMock,
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            derivePublicKey = { PUBLIC_KEY },
            onNicknameChanged = { newNickName ->
                onNickNameChangedCalledWith = newNickName
            },
        )

        identityStore.storeIdentity(
            identity = "01234567",
            serverGroup = "foobar",
            privateKey = PRIVATE_KEY,
        )

        verify { identityProviderMock.setIdentity("01234567") }
        verify { preferenceStoreMock.save("server_group", "foobar") }
        verify { preferenceStoreMock.save("public_key", PUBLIC_KEY) }
        verify { encryptedPreferenceStoreMock.save("private_key", PRIVATE_KEY) }
        verify { preferenceStoreMock.save("nickname", "01234567") }
        assertEquals("01234567", identityStore.getIdentity())
        assertContentEquals(PUBLIC_KEY, identityStore.getPublicKey())
        assertContentEquals(PRIVATE_KEY, identityStore.getPrivateKey())
        assertEquals("foobar", identityStore.getServerGroup())
        assertEquals("01234567", identityStore.getPublicNickname())
        assertEquals("01234567", onNickNameChangedCalledWith)
    }

    @Test
    fun `setting public nickname`() {
        val identityProviderMock = mockk<MutableIdentityProvider> {
            every { getIdentity() } returns null
        }
        val preferenceStoreMock = mockk<PreferenceStore>(relaxed = true)
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore>()
        var onNickNameChangedCalledWith: String? = null
        val identityStore = IdentityStoreImpl(
            identityProvider = identityProviderMock,
            preferenceStore = preferenceStoreMock,
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            onNicknameChanged = { newNickName ->
                onNickNameChangedCalledWith = newNickName
            },
        )

        identityStore.setPublicNickname("Nicolas")

        assertEquals("Nicolas", identityStore.getPublicNickname())
        assertEquals("Nicolas", onNickNameChangedCalledWith)
        verify { preferenceStoreMock.save("nickname", "Nicolas") }
    }

    @Test
    fun `clearing identity`() {
        val identityProviderMock = mockk<MutableIdentityProvider>(relaxed = true) {
            every { getIdentity() } returns "01234567"
        }
        val preferenceStoreMock = mockk<PreferenceStore>(relaxed = true) {
            every { getString("server_group") } returns "foobar"
            every { getBytes("public_key") } returns PUBLIC_KEY
            every { getString("nickname") } returns "Nick"
        }
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore>(relaxed = true) {
            every { getBytes("private_key") } returns PRIVATE_KEY
        }
        val identityStore = IdentityStoreImpl(
            identityProvider = identityProviderMock,
            preferenceStore = preferenceStoreMock,
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            onNicknameChanged = {},
        )

        identityStore.clear()

        verify { identityProviderMock.setIdentity(null) }
        verify { preferenceStoreMock.remove(setOf("public_key", "server_group")) }
        verify { encryptedPreferenceStoreMock.remove("private_key") }
        assertNull(identityStore.getIdentity())
        assertNull(identityStore.getPublicKey())
        assertNull(identityStore.getPrivateKey())
        assertNull(identityStore.getServerGroup())
        assertEquals("", identityStore.getPublicNickname())
    }

    companion object {
        val PUBLIC_KEY = ByteArray(32) { it.toByte() }
        val PRIVATE_KEY = ByteArray(32) { (it + 1).toByte() }
    }
}

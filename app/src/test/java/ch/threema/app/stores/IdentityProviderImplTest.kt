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

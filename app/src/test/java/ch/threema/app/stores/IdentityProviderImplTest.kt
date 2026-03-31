package ch.threema.app.stores

import ch.threema.domain.types.Identity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertEquals(Identity("01234567"), identityProvider.getIdentity())
    }

    @Test
    fun `get invalid identity`() {
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("identity") } returns "invalid"
        }

        val identityProvider = IdentityProviderImpl(preferenceStoreMock)
        assertNull(identityProvider.getIdentity())
    }

    @Test
    fun `set identity`() {
        val preferenceStoreMock = mockk<PreferenceStore>(relaxed = true) {
            every { getString("identity") } returns null
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock)

        identityProvider.setIdentity(Identity("01234567"))

        verify { preferenceStoreMock.save("identity", "01234567") }
        assertEquals(Identity("01234567"), identityProvider.getIdentity())
    }
}

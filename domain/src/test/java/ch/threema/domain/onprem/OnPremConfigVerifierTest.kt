package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnPremConfigVerifierTest {

    @Test
    fun `verify valid config`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.PUBLIC_KEY))

        val obj = verifier.verify(OnPremConfigTestData.goodOppf)

        assertEquals("1.0", obj.getString("version"))
    }

    @Test
    fun `invalid signature`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.PUBLIC_KEY))

        assertFailsWith<ThreemaException> {
            verifier.verify(OnPremConfigTestData.badOppf)
        }
    }

    @Test
    fun `wrong public key`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.WRONG_PUBLIC_KEY))

        assertFailsWith<ThreemaException> {
            verifier.verify(OnPremConfigTestData.goodOppf)
        }
    }

    @Test
    fun `empty input is invalid`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.PUBLIC_KEY))

        assertFailsWith<ThreemaException> {
            verifier.verify("")
        }
    }
}

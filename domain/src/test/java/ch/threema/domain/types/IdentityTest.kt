package ch.threema.domain.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class IdentityTest {
    @Test
    fun `to string`() {
        assertEquals(
            "*ANDROID",
            Identity("*ANDROID").toString(),
        )
    }

    @Test
    fun `get value`() {
        assertEquals(
            "TESTTEST",
            Identity("TESTTEST").value,
        )
    }

    @Test
    fun equality() {
        assertEquals(
            Identity("TESTTEST"),
            Identity("TESTTEST"),
        )
    }

    @Test
    fun inequality() {
        assertNotEquals(
            Identity("TEST1234"),
            Identity("TEST5678"),
        )
    }

    @Test
    fun `valid identities`() {
        Identity("01234567")
        Identity("ABCDEFGH")
        Identity("IJKLMNOP")
        Identity("QRSTUVWX")
        Identity("YZ987654")
        Identity("*ANDROID")
    }

    @Test
    fun `invalid identities`() {
        assertIsInvalidIdentity("")
        assertIsInvalidIdentity("        ")
        assertIsInvalidIdentity("1234567")
        assertIsInvalidIdentity("123456789")
        assertIsInvalidIdentity("ABCDEFGh")
        assertIsInvalidIdentity("ÄBCDËFGH")
        assertIsInvalidIdentity("qrstuvwx")
    }

    @Test
    fun `to identity or null if invalid`() {
        assertEquals(
            Identity("01234567"),
            "01234567".toIdentityOrNull(),
        )
        assertNull("".toIdentityOrNull())
    }

    private fun assertIsInvalidIdentity(string: String) {
        assertFailsWith<IllegalArgumentException> {
            Identity(string)
        }
    }
}

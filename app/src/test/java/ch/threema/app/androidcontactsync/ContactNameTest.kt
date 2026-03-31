package ch.threema.app.androidcontactsync

import ch.threema.app.androidcontactsync.types.ContactName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContactNameTest {
    @Test
    fun `create non blank contact name`() {
        val contactName = ContactName.create(
            firstName = "Max",
            lastName = "Muster",
        )
        assertNotNull(contactName)
        assertEquals("Max", contactName.firstName)
        assertEquals("Muster", contactName.lastName)
    }

    @Test
    fun `create non blank contact name with trimming`() {
        val contactName = ContactName.create(
            firstName = " Max ",
            lastName = "   Muster   ",
        )
        assertNotNull(contactName)
        assertEquals("Max", contactName.firstName)
        assertEquals("Muster", contactName.lastName)
    }

    @Test
    fun `create contact with blank last name`() {
        val contactName = ContactName.create(
            firstName = " Max ",
            lastName = "   ",
        )
        assertNotNull(contactName)
        assertEquals("Max", contactName.firstName)
        assertEquals("", contactName.lastName)
    }

    @Test
    fun `create contact with empty last name`() {
        val contactName = ContactName.create(
            firstName = " Max ",
            lastName = "",
        )
        assertNotNull(contactName)
        assertEquals("Max", contactName.firstName)
        assertEquals("", contactName.lastName)
    }

    @Test
    fun `create contact with null last name`() {
        val contactName = ContactName.create(
            firstName = " Max ",
            lastName = null,
        )
        assertNotNull(contactName)
        assertEquals("Max", contactName.firstName)
        assertEquals("", contactName.lastName)
    }

    @Test
    fun `create contact with blank first name`() {
        val contactName = ContactName.create(
            firstName = "   ",
            lastName = "Muster",
        )
        assertNotNull(contactName)
        assertEquals("", contactName.firstName)
        assertEquals("Muster", contactName.lastName)
    }

    @Test
    fun `create contact with empty first name`() {
        val contactName = ContactName.create(
            firstName = "",
            lastName = "Muster",
        )
        assertNotNull(contactName)
        assertEquals("", contactName.firstName)
        assertEquals("Muster", contactName.lastName)
    }

    @Test
    fun `create contact with null first name`() {
        val contactName = ContactName.create(
            firstName = null,
            lastName = "Muster",
        )
        assertNotNull(contactName)
        assertEquals("", contactName.firstName)
        assertEquals("Muster", contactName.lastName)
    }

    @Test
    fun `create contact with both blank`() {
        assertNull(
            ContactName.create(
                firstName = "  ",
                lastName = "  ",
            ),
        )
    }

    @Test
    fun `create contact with first blank and last empty`() {
        assertNull(
            ContactName.create(
                firstName = "  ",
                lastName = "",
            ),
        )
    }

    @Test
    fun `create contact with first empty and last blank`() {
        assertNull(
            ContactName.create(
                firstName = "",
                lastName = "  ",
            ),
        )
    }

    @Test
    fun `create contact with both empty`() {
        assertNull(
            ContactName.create(
                firstName = "",
                lastName = "",
            ),
        )
    }

    @Test
    fun `create contact with first null and last blank`() {
        assertNull(
            ContactName.create(
                firstName = null,
                lastName = "    ",
            ),
        )
    }

    @Test
    fun `create contact with first blank and last null`() {
        assertNull(
            ContactName.create(
                firstName = "    ",
                lastName = null,
            ),
        )
    }

    @Test
    fun `create contact with both null`() {
        assertNull(
            ContactName.create(
                firstName = null,
                lastName = null,
            ),
        )
    }

    @Test
    fun `full name is just first name`() {
        val contactName = ContactName.create(firstName = "first", lastName = null)!!
        assertEquals(contactName.firstName, contactName.fullName)
    }

    @Test
    fun `full name is just last name`() {
        val contactName = ContactName.create(firstName = null, lastName = "last")!!
        assertEquals(contactName.lastName, contactName.fullName)
    }

    @Test
    fun `full name is just first and last name`() {
        val contactName = ContactName.create(firstName = "first", lastName = "last")!!
        assertEquals("${contactName.firstName} ${contactName.lastName}", contactName.fullName)
    }

    @Test
    fun `hash code and equals matches with only first name`() {
        val contactNameA = ContactName.create(firstName = "first", lastName = null)
        val contactNameB = ContactName.create(firstName = " first ", lastName = "")
        assertEquals(contactNameA, contactNameB)
        assertEquals(contactNameA.hashCode(), contactNameB.hashCode())
    }

    @Test
    fun `hash code and equals matches with only last name`() {
        val contactNameA = ContactName.create(firstName = "", lastName = "last")
        val contactNameB = ContactName.create(firstName = null, lastName = " last ")
        assertEquals(contactNameA, contactNameB)
        assertEquals(contactNameA.hashCode(), contactNameB.hashCode())
    }

    @Test
    fun `hash code and equals matches with both names`() {
        val contactNameA = ContactName.create(firstName = "first", lastName = "last")
        val contactNameB = ContactName.create(firstName = " first ", lastName = " last ")
        assertEquals(contactNameA, contactNameB)
        assertEquals(contactNameA.hashCode(), contactNameB.hashCode())
    }

    @Test
    fun `equals does not match with different names`() {
        val contactNameA = ContactName.create(firstName = "Max", lastName = "Muster")
        val contactNameB = ContactName.create(firstName = "Max", lastName = "Meier")
        assertNotEquals(contactNameA, contactNameB)
    }
}

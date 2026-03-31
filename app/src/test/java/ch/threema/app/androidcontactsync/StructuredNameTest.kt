package ch.threema.app.androidcontactsync

import ch.threema.app.androidcontactsync.types.ContactName
import ch.threema.app.androidcontactsync.types.StructuredName
import kotlin.test.Test
import kotlin.test.assertEquals

class StructuredNameTest {

    @Test
    fun `reduction to first and last name with all properties`() {
        val structuredName = StructuredName(
            prefix = "Prefix",
            givenName = "Given",
            middleName = "Middle",
            familyName = "Family",
            suffix = "Suffix",
            displayName = "Display",
        )

        val contactName = structuredName.reduceToFirstAndLastName()
        val expected = ContactName.create(
            firstName = "Prefix Given Middle",
            lastName = "Family Suffix",
        )

        assertEquals(expected, contactName)
    }

    @Test
    fun `reduction to first and last name with only given name`() {
        val structuredName = StructuredName(
            givenName = "Given",
        )

        val contactName = structuredName.reduceToFirstAndLastName()
        val expected = ContactName.create(
            firstName = "Given",
            lastName = "",
        )

        assertEquals(expected, contactName)
    }

    @Test
    fun `reduction to first and last name with only family name`() {
        val structuredName = StructuredName(
            familyName = "Family",
        )

        val contactName = structuredName.reduceToFirstAndLastName()
        val expected = ContactName.create(
            firstName = "",
            lastName = "Family",
        )

        assertEquals(expected, contactName)
    }

    @Test
    fun `reduction to first and last name with only given and family name`() {
        val structuredName = StructuredName(
            givenName = "Given",
            familyName = "Family",
        )

        val contactName = structuredName.reduceToFirstAndLastName()
        val expected = ContactName.create(
            firstName = "Given",
            lastName = "Family",
        )

        assertEquals(expected, contactName)
    }

    @Test
    fun `reduction to first and last name with untrimmed given and family name`() {
        val structuredName = StructuredName(
            givenName = "   Given   ",
            familyName = "   Family   ",
        )

        val contactName = structuredName.reduceToFirstAndLastName()
        val expected = ContactName.create(
            firstName = "Given",
            lastName = "Family",
        )

        assertEquals(expected, contactName)
    }

    @Test
    fun `reduction to first and last name with all names and spaces`() {
        val structuredName = StructuredName(
            prefix = "  Prefix    ",
            givenName = " Given ",
            middleName = "   Middle  ",
            familyName = "     Family    ",
            suffix = "Suffix  ",
            displayName = "    Display   ",
        )

        val contactName = structuredName.reduceToFirstAndLastName()
        val expected = ContactName.create(
            firstName = "Prefix Given Middle",
            lastName = "Family Suffix",
        )

        assertEquals(expected, contactName)
    }

    @Test
    fun `reduction to first and last name with fallback to basic display name`() {
        val structuredName = StructuredName(
            prefix = "",
            familyName = "   ",
            displayName = "    Display   ",
        )

        val contactName = structuredName.reduceToFirstAndLastName()
        val expected = ContactName.create(
            firstName = "Display",
            lastName = "",
        )

        assertEquals(expected, contactName)
    }

    @Test
    fun `reduction to first and last name with fallback to common display name`() {
        val structuredName = StructuredName(
            displayName = "Max Muster",
        )

        val contactName = structuredName.reduceToFirstAndLastName()
        val expected = ContactName.create(
            firstName = "Max",
            lastName = "Muster",
        )

        assertEquals(expected, contactName)
    }

    @Test
    fun `reduction to first and last name with fallback to long display name`() {
        val structuredName = StructuredName(
            displayName = "Max Maximilian Erika      Muster",
        )

        val contactName = structuredName.reduceToFirstAndLastName()
        val expected = ContactName.create(
            firstName = "Max",
            lastName = "Maximilian Erika Muster",
        )

        assertEquals(expected, contactName)
    }
}

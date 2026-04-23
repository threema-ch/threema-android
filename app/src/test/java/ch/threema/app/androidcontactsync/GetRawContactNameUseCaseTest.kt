package ch.threema.app.androidcontactsync

import ch.threema.app.androidcontactsync.types.ContactName
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.app.androidcontactsync.types.StructuredName
import ch.threema.app.androidcontactsync.usecases.GetRawContactNameUseCase
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetRawContactNameUseCaseTest {

    @Test
    fun `no name is set`() {
        val rawContact = getRawContactWithNames(
            structuredNames = emptySet(),
        )

        val contactName = GetRawContactNameUseCase().call(rawContact)
        assertNull(contactName)
    }

    @Test
    fun `only one name is set`() {
        val expectedContactName = ContactName.create(firstName = "Max", lastName = "Muster")!!
        val rawContact = getRawContactWithNames(
            structuredNames = setOf(
                mockedStructuredNameOf(expectedContactName),
            ),
        )

        val contactName = GetRawContactNameUseCase().call(rawContact)
        assertEquals(
            expected = expectedContactName,
            actual = contactName,
        )
    }

    @Test
    fun `all names are the same`() {
        val expectedContactName = ContactName.create(firstName = "Erika", lastName = "Muster")!!
        val rawContact = getRawContactWithNames(
            structuredNames = setOf(
                mockedStructuredNameOf(expectedContactName),
                mockedStructuredNameOf(expectedContactName),
                mockedStructuredNameOf(expectedContactName),
            ),
        )

        val actualContactName = GetRawContactNameUseCase().call(rawContact)
        assertEquals(
            expected = expectedContactName,
            actual = actualContactName,
        )
    }

    @Test
    fun `first name and full name set`() {
        val expectedContactName = ContactName.create(firstName = "Erika", lastName = "Muster")!!
        val onlyFirstName = ContactName.create(firstName = expectedContactName.firstName, lastName = "")!!
        val rawContact = getRawContactWithNames(
            structuredNames = setOf(
                mockedStructuredNameOf(expectedContactName),
                mockedStructuredNameOf(onlyFirstName),
            ),
        )

        val actualContactName = GetRawContactNameUseCase().call(rawContact)
        assertEquals(
            expected = expectedContactName,
            actual = actualContactName,
        )
    }

    @Test
    fun `first name is set more often than full name`() {
        val expectedContactName = ContactName.create(firstName = "Erika", lastName = "")!!
        val fullName = ContactName.create(firstName = expectedContactName.firstName, lastName = "Muster")!!
        val rawContact = getRawContactWithNames(
            structuredNames = setOf(
                mockedStructuredNameOf(expectedContactName),
                mockedStructuredNameOf(expectedContactName),
                mockedStructuredNameOf(fullName),
            ),
        )

        val actualContactName = GetRawContactNameUseCase().call(rawContact)
        assertEquals(
            expected = expectedContactName,
            actual = actualContactName,
        )
    }

    @Test
    fun `longer names are preferred`() {
        val expectedContactName = ContactName.create(firstName = "Erika", lastName = "Muster")!!
        val otherContactName = ContactName.create(firstName = "Max", lastName = "Muster")!!
        val rawContact = getRawContactWithNames(
            structuredNames = setOf(
                mockedStructuredNameOf(expectedContactName),
                mockedStructuredNameOf(otherContactName),
            ),
        )

        val actualContactName = GetRawContactNameUseCase().call(rawContact)
        assertEquals(
            expected = expectedContactName,
            actual = actualContactName,
        )
    }

    private fun mockedStructuredNameOf(contactName: ContactName): StructuredName = mockk {
        every { reduceToFirstAndLastName() } returns contactName
    }

    private fun getRawContactWithNames(
        rawContactId: RawContactId = RawContactId(0UL),
        structuredNames: Set<StructuredName>,
    ) = RawContact(
        rawContactId = rawContactId,
        lookupInfo = mockk(),
        phoneNumbers = emptySet(),
        emailAddresses = emptySet(),
        structuredNames = structuredNames,
    )
}

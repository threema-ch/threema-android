package ch.threema.app.androidcontactsync

import ch.threema.app.androidcontactsync.read.ContactDataRow
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.LookupKey
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.RawContactId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RawContactTest {

    @Test
    fun `building raw contact fails when raw contact id does not match`() {
        val rawContactId = RawContactId(42u)
        val lookupInfo = LookupInfo(
            lookupKey = LookupKey("lookupKey"),
            contactId = ContactId(42u),
        )
        val rawContactBuilder = RawContact.Builder(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo,
        )
        val contactDataRowOfDifferentRawContact = ContactDataRow.PhoneNumberRow(
            rawContactId = RawContactId(43u),
            lookupInfo = lookupInfo,
            phoneNumber = PhoneNumber("+41 327 634 321"),
        )

        assertFailsWith<RawContact.Builder.RawContactBuildException> {
            rawContactBuilder.addContactDataRow(
                contactDataRow = contactDataRowOfDifferentRawContact,
            )
        }
    }

    @Test
    fun `building raw contact fails when contact id does not match`() {
        val rawContactId = RawContactId(42u)
        val lookupInfo = LookupInfo(
            lookupKey = LookupKey("lookupKey"),
            contactId = ContactId(42u),
        )
        val rawContactBuilder = RawContact.Builder(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo,
        )
        val contactDataRowOfDifferentRawContact = ContactDataRow.PhoneNumberRow(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo.copy(contactId = ContactId(43u)),
            phoneNumber = PhoneNumber("+41 327 634 321"),
        )

        assertFailsWith<RawContact.Builder.RawContactBuildException> {
            rawContactBuilder.addContactDataRow(
                contactDataRow = contactDataRowOfDifferentRawContact,
            )
        }
    }

    @Test
    fun `building raw contact fails when contact lookup key does not match`() {
        val rawContactId = RawContactId(42u)
        val lookupInfo = LookupInfo(
            lookupKey = LookupKey("lookupKey"),
            contactId = ContactId(42u),
        )
        val rawContactBuilder = RawContact.Builder(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo,
        )
        val contactDataRowOfDifferentRawContact = ContactDataRow.PhoneNumberRow(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo.copy(lookupKey = LookupKey("otherLookupKey")),
            phoneNumber = PhoneNumber("+41 327 634 321"),
        )

        assertFailsWith<RawContact.Builder.RawContactBuildException> {
            rawContactBuilder.addContactDataRow(
                contactDataRow = contactDataRowOfDifferentRawContact,
            )
        }
    }

    @Test
    fun `building raw contact works with multiple data`() {
        val rawContactId = RawContactId(42u)
        val lookupInfo = LookupInfo(
            lookupKey = LookupKey("lookupKey"),
            contactId = ContactId(42u),
        )
        val rawContactBuilder = RawContact.Builder(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo,
        )

        val phoneNumberRows = getPhoneNumberRowSequence(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo,
        ).take(10)
        val emailAddressRows = getEmailAddressRowSequence(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo,
        ).take(10)
        val structuredNameRows = getStructuredNameRowSequence(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo,
        ).take(10)

        (phoneNumberRows + emailAddressRows + structuredNameRows).forEach { contactDataRow ->
            rawContactBuilder.addContactDataRow(contactDataRow)
        }

        val expectedRawContact = RawContact(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo,
            phoneNumbers = phoneNumberRows.map(ContactDataRow.PhoneNumberRow::phoneNumber).toSet(),
            emailAddresses = emailAddressRows.map(ContactDataRow.EmailAddressRow::emailAddress).toSet(),
            structuredNames = structuredNameRows.map(ContactDataRow.StructuredNameRow::structuredName).toSet(),
        )
        assertEquals(expectedRawContact, rawContactBuilder.build())
    }
}

package ch.threema.app.androidcontactsync

import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.LookupKey
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.app.androidcontactsync.types.StructuredName
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AndroidContactTest {

    @Test
    fun `android contact without raw contacts can be created`() {
        val androidContact = AndroidContact(
            lookupInfo = mockk(),
            rawContacts = emptySet(),
        )
        assertNotNull(androidContact)
    }

    @Test
    fun `creating android contact with raw contact with different lookup info fails`() {
        val androidContactLookupInfo = LookupInfo(
            lookupKey = LookupKey("androidContactLookupKey"),
            contactId = ContactId(42u),
        )
        val rawContactLookupInfo = LookupInfo(
            lookupKey = LookupKey("rawContactLookupInfo"),
            contactId = ContactId(43u),
        )
        val rawContact = RawContact(
            rawContactId = RawContactId(42u),
            lookupInfo = rawContactLookupInfo,
            phoneNumbers = emptySet(),
            emailAddresses = emptySet(),
            structuredNames = emptySet(),
        )
        assertFailsWith<IllegalArgumentException> {
            AndroidContact(
                lookupInfo = androidContactLookupInfo,
                rawContacts = setOf(rawContact),
            )
        }
    }

    @Test
    fun `creating android contact with raw contacts with duplicate raw contact ids fails`() {
        val lookupInfo = LookupInfo(
            lookupKey = LookupKey("lookupKey"),
            contactId = ContactId(42u),
        )
        val rawContacts = setOf(
            RawContact(
                rawContactId = RawContactId(42u),
                lookupInfo = lookupInfo,
                phoneNumbers = emptySet(),
                emailAddresses = emptySet(),
                structuredNames = setOf(
                    StructuredName(
                        familyName = "Family!",
                    ),
                ),
            ),
            RawContact(
                rawContactId = RawContactId(43u),
                lookupInfo = lookupInfo,
                phoneNumbers = emptySet(),
                emailAddresses = emptySet(),
                structuredNames = setOf(
                    StructuredName(
                        givenName = "Given!",
                    ),
                ),
            ),
            RawContact(
                // The same raw contact id as the first raw contact
                rawContactId = RawContactId(42u),
                lookupInfo = lookupInfo,
                phoneNumbers = emptySet(),
                emailAddresses = emptySet(),
                structuredNames = setOf(
                    StructuredName(
                        middleName = "Middle!",
                    ),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            AndroidContact(
                lookupInfo = lookupInfo,
                rawContacts = rawContacts,
            )
        }
    }
}

package ch.threema.app.androidcontactsync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import ch.threema.app.androidcontactsync.read.AndroidContactLookupException
import ch.threema.app.androidcontactsync.read.AndroidContactReadException
import ch.threema.app.androidcontactsync.read.ContactDataRow
import ch.threema.app.androidcontactsync.read.LookupInfoReader
import ch.threema.app.androidcontactsync.read.RawContactCursor
import ch.threema.app.androidcontactsync.read.RawContactCursorProvider
import ch.threema.app.androidcontactsync.read.RawContactReader
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.LookupKey
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.app.androidcontactsync.types.StructuredName
import ch.threema.app.test.testDispatcherProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RawContactReaderTest {
    @Test
    fun `read empty contacts`() = runTest {
        val rawContactCursorProviderMock = mockRawContactCursorProvider(emptyList())
        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProviderMock,
            lookupInfoReader = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        assertTrue { rawContactReader.readAllRawContacts().isEmpty() }
    }

    @Test
    fun `read valid raw contacts`() = runTest {
        val rawContact0 = RawContact(
            rawContactId = RawContactId(0u),
            lookupInfo = LookupInfo(
                lookupKey = LookupKey("lookup0"),
                contactId = ContactId(0u),
            ),
            phoneNumbers = setOf(PhoneNumber("0123456789"), PhoneNumber("0987654321")),
            emailAddresses = setOf(EmailAddress("email0@example.com")),
            structuredNames = setOf(
                StructuredName(
                    prefix = "Dr.",
                    givenName = "Erika",
                    familyName = "Muster",
                ),
            ),
        )
        val rawContact1 = RawContact(
            rawContactId = RawContactId(1u),
            lookupInfo = LookupInfo(
                lookupKey = LookupKey("lookup1"),
                contactId = ContactId(1u),
            ),
            phoneNumbers = emptySet(),
            emailAddresses = setOf(EmailAddress("max@example.com")),
            structuredNames = setOf(
                StructuredName(
                    givenName = "Max",
                    familyName = "Muster",
                ),
                StructuredName(
                    givenName = "A",
                    familyName = "B",
                ),
                StructuredName(
                    givenName = "C",
                    familyName = "D",
                ),
                StructuredName(
                    givenName = "E",
                    familyName = "F",
                ),
            ),
        )

        val rawContactCursorProviderMock = mockRawContactCursorProvider(
            // Note that we use seeded random for reproducible results
            contactDataRows = (rawContact0.toContactDataRows() + rawContact1.toContactDataRows()).shuffled(Random(42)),
        )

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProviderMock,
            lookupInfoReader = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )
        val rawContacts = rawContactReader.readAllRawContacts()

        assertEquals(2, rawContacts.size)
        assertContains(rawContacts, rawContact0)
        assertContains(rawContacts, rawContact1)
    }

    @Test
    fun `read fails when lookup key is used with different contact ids`() = runTest {
        val rawContactCursorProviderMock = mockRawContactCursorProvider(
            listOf(
                ContactDataRow.StructuredNameRow(
                    rawContactId = RawContactId(0u),
                    lookupInfo = LookupInfo(
                        lookupKey = LookupKey("lookup"),
                        contactId = ContactId(0u),
                    ),
                    structuredName = StructuredName(),
                ),
                ContactDataRow.StructuredNameRow(
                    rawContactId = RawContactId(1u),
                    lookupInfo = LookupInfo(
                        lookupKey = LookupKey("lookup"),
                        // Different contact id but same lookup key
                        contactId = ContactId(1u),
                    ),
                    structuredName = StructuredName(),
                ),
            ),
        )

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProviderMock,
            lookupInfoReader = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )
        assertFailsWith<AndroidContactReadException.MultipleContactIdsPerLookupKey> {
            rawContactReader.readAllRawContacts()
        }
    }

    @Test
    fun `read fails when contact id is used by different lookup keys`() = runTest {
        val rawContactCursorProviderMock = mockRawContactCursorProvider(
            listOf(
                ContactDataRow.StructuredNameRow(
                    rawContactId = RawContactId(0u),
                    lookupInfo = LookupInfo(
                        lookupKey = LookupKey("lookup"),
                        contactId = ContactId(0u),
                    ),
                    structuredName = StructuredName(),
                ),
                ContactDataRow.StructuredNameRow(
                    rawContactId = RawContactId(1u),
                    lookupInfo = LookupInfo(
                        // Other lookup key but same contact id
                        lookupKey = LookupKey("otherLookupKey"),
                        contactId = ContactId(0u),
                    ),
                    structuredName = StructuredName(),
                ),
            ),
        )

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProviderMock,
            lookupInfoReader = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )
        assertFailsWith<AndroidContactReadException.MultipleLookupKeysPerContactId> {
            rawContactReader.readAllRawContacts()
        }
    }

    @Test
    fun `read fails when raw contact is associated with different lookup information`() = runTest {
        val rawContactCursorProviderMock = mockRawContactCursorProvider(
            listOf(
                ContactDataRow.StructuredNameRow(
                    rawContactId = RawContactId(0u),
                    lookupInfo = LookupInfo(
                        lookupKey = LookupKey("lookup"),
                        contactId = ContactId(0u),
                    ),
                    structuredName = StructuredName(),
                ),
                ContactDataRow.StructuredNameRow(
                    // The same raw contact, but different lookup info
                    rawContactId = RawContactId(0u),
                    lookupInfo = LookupInfo(
                        lookupKey = LookupKey("otherLookupKey"),
                        contactId = ContactId(1u),
                    ),
                    structuredName = StructuredName(),
                ),
            ),
        )

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProviderMock,
            lookupInfoReader = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )
        assertFailsWith<AndroidContactReadException.MultipleLookupKeysPerRawContact> {
            rawContactReader.readAllRawContacts()
        }
    }

    @Test
    fun `name is found successfully`() = runTest {
        val lookupInfo = LookupInfo(
            lookupKey = LookupKey("lookup"),
            contactId = ContactId(0u),
        )

        val rawContacts = setOf(
            RawContact(
                rawContactId = RawContactId(0u),
                lookupInfo = lookupInfo,
                phoneNumbers = setOf(PhoneNumber("0123456789"), PhoneNumber("0987654321")),
                emailAddresses = setOf(EmailAddress("email0@example.com")),
                structuredNames = setOf(
                    StructuredName(
                        prefix = "Dr.",
                        givenName = "Erika",
                        familyName = "Muster",
                    ),
                ),
            ),
            RawContact(
                rawContactId = RawContactId(1u),
                lookupInfo = lookupInfo,
                phoneNumbers = emptySet(),
                emailAddresses = setOf(EmailAddress("max@example.com")),
                structuredNames = setOf(
                    StructuredName(
                        givenName = "Max",
                        familyName = "Muster",
                    ),
                    StructuredName(
                        givenName = "A",
                        familyName = "B",
                    ),
                    StructuredName(
                        givenName = "C",
                        familyName = "D",
                    ),
                    StructuredName(
                        givenName = "E",
                        familyName = "F",
                    ),
                ),
            ),
        )

        val rawContactCursorProviderMock = mockRawContactCursorProvider(
            contactDataRows = rawContacts.flatMap(RawContact::toContactDataRows),
            lookupInfo = lookupInfo,
        )

        val uriMock: Uri = mockk()

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProviderMock,
            lookupInfoReader = mockLookupInfoReader(mapOf(uriMock to lookupInfo)),
            dispatcherProvider = testDispatcherProvider(),
        )

        rawContactReader.readRawContactsFromLookup(uriMock)
    }

    @Test
    fun `name lookup returns empty set if lookup info is not found`() = runTest {
        val rawContactCursorProviderMock = mockRawContactCursorProvider(
            contactDataRows = emptyList(),
            lookupInfo = null,
        )

        val uriMock: Uri = mockk()

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProviderMock,
            lookupInfoReader = mockLookupInfoReader(mapOf(uriMock to null)),
            dispatcherProvider = testDispatcherProvider(),
        )

        assertTrue { rawContactReader.readRawContactsFromLookup(uriMock).isEmpty() }
    }

    @Test
    fun `name lookup fails if the wrong contact is received`() = runTest {
        val lookupInfo = LookupInfo(
            lookupKey = LookupKey("lookup"),
            contactId = ContactId(0u),
        )

        val wrongRawContact = RawContact(
            rawContactId = RawContactId(0u),
            lookupInfo = lookupInfo.copy(lookupKey = LookupKey("Wrong-lookup-key")),
            phoneNumbers = setOf(PhoneNumber("0123456789"), PhoneNumber("0987654321")),
            emailAddresses = setOf(EmailAddress("email0@example.com")),
            structuredNames = setOf(
                StructuredName(
                    prefix = "Dr.",
                    givenName = "Erika",
                    familyName = "Muster",
                ),
            ),
        )

        val rawContactCursorProviderMock = mockRawContactCursorProvider(
            contactDataRows = wrongRawContact.toContactDataRows(),
            lookupInfo = lookupInfo,
        )

        val uriMock: Uri = mockk()

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProviderMock,
            lookupInfoReader = mockLookupInfoReader(mapOf(uriMock to lookupInfo)),
            dispatcherProvider = testDispatcherProvider(),
        )

        assertFailsWith<AndroidContactLookupException.WrongContactReceivedForLookup> {
            rawContactReader.readRawContactsFromLookup(uriMock)
        }
    }

    @Test
    fun `name lookup fails if lookup info query is unstable`() = runTest {
        val lookupInfo = LookupInfo(
            lookupKey = LookupKey("lookup"),
            contactId = ContactId(0u),
        )

        val rawContact = RawContact(
            rawContactId = RawContactId(0u),
            lookupInfo = lookupInfo,
            phoneNumbers = setOf(PhoneNumber("0123456789"), PhoneNumber("0987654321")),
            emailAddresses = setOf(EmailAddress("email0@example.com")),
            structuredNames = setOf(
                StructuredName(
                    prefix = "Dr.",
                    givenName = "Erika",
                    familyName = "Muster",
                ),
            ),
        )

        val rawContactCursorProviderMock = mockRawContactCursorProvider(
            contactDataRows = rawContact.toContactDataRows(),
            lookupInfo = lookupInfo,
        )

        val uriMock: Uri = mockk()

        val lookupInfoReaderMock: LookupInfoReader = mockk {
            var firstExecution = true
            every { getLookupInfo(uriMock) } answers {
                if (firstExecution) {
                    firstExecution = false
                    lookupInfo
                } else {
                    LookupInfo(
                        lookupKey = LookupKey("otherLookupKey"),
                        contactId = ContactId(1u),
                    )
                }
            }
        }

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProviderMock,
            lookupInfoReader = lookupInfoReaderMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        assertFailsWith<AndroidContactLookupException.UnstableContactLookupInfo> {
            rawContactReader.readRawContactsFromLookup(uriMock)
        }
    }

    @Test
    fun `read fails if no permission is given to read the contacts`() = runTest {
        val appContextMock: Context = mockk {
            every { contentResolver } returns mockContentResolverWithoutPermission()
        }
        val rawContactCursorProvider = RawContactCursorProvider(appContext = appContextMock)

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = rawContactCursorProvider,
            lookupInfoReader = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        assertFailsWith<AndroidContactReadException.MissingPermission> {
            rawContactReader.readAllRawContacts()
        }
    }

    @Test
    fun `name lookup fails if no permission is given to read the contacts`() = runTest {
        val appContextMock: Context = mockk {
            every { contentResolver } returns mockContentResolverWithoutPermission()
        }

        val lookupInfoReader = LookupInfoReader(
            appContext = appContextMock,
        )

        val rawContactReader = RawContactReader(
            rawContactCursorProvider = mockk(),
            lookupInfoReader = lookupInfoReader,
            dispatcherProvider = testDispatcherProvider(),
        )

        assertFailsWith<AndroidContactReadException.MissingPermission> {
            rawContactReader.readRawContactsFromLookup(mockk())
        }
    }

    private fun mockContentResolverWithoutPermission(): ContentResolver = mockk {
        every { query(any(), any(), any(), any()) } throws SecurityException()
        every { query(any(), any(), any(), any(), any()) } throws SecurityException()
        every { query(any(), any(), any(), any(), any(), any()) } throws SecurityException()
    }

    private fun mockRawContactCursorProvider(
        contactDataRows: List<ContactDataRow>,
        lookupInfo: LookupInfo? = null,
    ): RawContactCursorProvider = mockk {
        var cursorPosition = -1
        val rawContactCursorMock: RawContactCursor = mockk {
            every { moveToNext() } answers {
                cursorPosition++
                cursorPosition < contactDataRows.size
            }

            every { getContactDataRow() } answers {
                contactDataRows[cursorPosition]
            }

            every { close() } just Runs
        }

        every { getRawContactCursor() } returns rawContactCursorMock
        if (lookupInfo != null) {
            every { getRawContactCursorForLookup(lookupInfo) } returns rawContactCursorMock
        }
    }

    private fun mockLookupInfoReader(lookupInfoMap: Map<Uri, LookupInfo?>): LookupInfoReader = mockk {
        lookupInfoMap.forEach { (uri, lookupInfo) ->
            every { getLookupInfo(uri) } returns lookupInfo
        }
    }
}

private fun RawContact.toContactDataRows(): List<ContactDataRow> = buildList {
    addAll(
        elements = phoneNumbers.map { phoneNumber ->
            ContactDataRow.PhoneNumberRow(
                rawContactId = rawContactId,
                lookupInfo = lookupInfo,
                phoneNumber = phoneNumber,
            )
        },
    )
    addAll(
        elements = emailAddresses.map { emailAddress ->
            ContactDataRow.EmailAddressRow(
                rawContactId = rawContactId,
                lookupInfo = lookupInfo,
                emailAddress = emailAddress,
            )
        },
    )
    addAll(
        elements = structuredNames.map { structuredName ->
            ContactDataRow.StructuredNameRow(
                rawContactId = rawContactId,
                lookupInfo = lookupInfo,
                structuredName = structuredName,
            )
        },
    )
}

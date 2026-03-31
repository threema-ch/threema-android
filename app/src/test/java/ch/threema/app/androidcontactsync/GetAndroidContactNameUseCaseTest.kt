package ch.threema.app.androidcontactsync

import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.ContactName
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.LookupKey
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.app.androidcontactsync.usecases.GetAndroidContactNameUseCase
import ch.threema.app.androidcontactsync.usecases.GetRawContactNameUseCase
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetAndroidContactNameUseCaseTest {
    @Test
    fun `no raw contact is available`() {
        val androidContact = AndroidContact(
            lookupInfo = mockk(),
            rawContacts = emptySet(),
        )

        val contactName = GetAndroidContactNameUseCase(mockk()).call(androidContact)
        assertNull(contactName)
    }

    @Test
    fun `only one raw contact is available`() {
        val rawContact: RawContact = createRawContact()
        val expectedName = ContactName.create(firstName = "Erika", lastName = "Muster")!!
        val androidContact = AndroidContact(
            lookupInfo = rawContact.lookupInfo,
            rawContacts = setOf(rawContact),
        )

        val getRawContactNameUseCase = mockGetRawContactNameUseCase(mapOf(rawContact to expectedName))

        val contactName = GetAndroidContactNameUseCase(getRawContactNameUseCase).call(androidContact)
        assertEquals(
            expected = expectedName,
            actual = contactName,
        )
    }

    @Test
    fun `all names are the same`() {
        val rawContacts: Set<RawContact> = setOf(
            createRawContact(rawContactId = RawContactId(0u)),
            createRawContact(rawContactId = RawContactId(1u)),
            createRawContact(rawContactId = RawContactId(2u)),
        )
        val expectedName = ContactName.create(firstName = "Erika", lastName = "Muster")!!
        val androidContact = AndroidContact(
            lookupInfo = rawContacts.first().lookupInfo,
            rawContacts = rawContacts,
        )

        val getRawContactNameUseCase = mockGetRawContactNameUseCase(rawContacts.associateWith { expectedName })

        val contactName = GetAndroidContactNameUseCase(getRawContactNameUseCase).call(androidContact)
        assertEquals(
            expected = expectedName,
            actual = contactName,
        )
    }

    @Test
    fun `name of raw contact with smallest id is taken in doubt`() {
        val expectedName = ContactName.create(firstName = "Erika", lastName = "Muster")!!
        val otherName = ContactName.create(firstName = "Max", lastName = "Muster")!!
        val anotherName = ContactName.create(firstName = "Maximilian", lastName = "Muster")!!
        val rawContactToName = mapOf(
            createRawContact(rawContactId = RawContactId(2u)) to anotherName,
            createRawContact(rawContactId = RawContactId(1u)) to otherName,
            createRawContact(rawContactId = RawContactId(0u)) to expectedName,
        )
        val androidContact = AndroidContact(
            lookupInfo = rawContactToName.keys.first().lookupInfo,
            rawContacts = rawContactToName.keys,
        )

        val getRawContactNameUseCase = mockGetRawContactNameUseCase(rawContactToName)

        val contactName = GetAndroidContactNameUseCase(getRawContactNameUseCase).call(androidContact)
        assertEquals(
            expected = expectedName,
            actual = contactName,
        )
    }

    @Test
    fun `name of raw contact with smallest id is taken in doubt and with multiple most frequent names`() {
        val expectedName = ContactName.create(firstName = "Erika", lastName = "Muster")!!
        val otherName = ContactName.create(firstName = "Max", lastName = "Muster")!!
        val anotherName = ContactName.create(firstName = "Maximilian", lastName = "Muster")!!
        val rawContactToName = mapOf(
            createRawContact(rawContactId = RawContactId(2u)) to anotherName,
            createRawContact(rawContactId = RawContactId(1u)) to otherName,
            createRawContact(rawContactId = RawContactId(0u)) to expectedName,
            createRawContact(rawContactId = RawContactId(3u)) to expectedName,
            createRawContact(rawContactId = RawContactId(4u)) to otherName,
        )
        val androidContact = AndroidContact(
            lookupInfo = rawContactToName.keys.first().lookupInfo,
            rawContacts = rawContactToName.keys,
        )

        val getRawContactNameUseCase = mockGetRawContactNameUseCase(rawContactToName)

        val contactName = GetAndroidContactNameUseCase(getRawContactNameUseCase).call(androidContact)
        assertEquals(
            expected = expectedName,
            actual = contactName,
        )
    }

    private fun createRawContact(
        rawContactId: RawContactId = RawContactId(0u),
        lookupInfo: LookupInfo = LookupInfo(LookupKey("lookupKey"), ContactId(42u)),
    ) = RawContact(
        rawContactId = rawContactId,
        lookupInfo = lookupInfo,
        phoneNumbers = emptySet(),
        emailAddresses = emptySet(),
        structuredNames = emptySet(),
    )

    private fun mockGetRawContactNameUseCase(rawContactToName: Map<RawContact, ContactName>): GetRawContactNameUseCase = mockk {
        rawContactToName.forEach { (rawContact, contactName) ->
            every { call(rawContact) } returns contactName
        }
    }
}

package ch.threema.app.androidcontactsync

import android.net.Uri
import ch.threema.app.androidcontactsync.read.AndroidContactReader
import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.ContactName
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.LookupKey
import ch.threema.app.androidcontactsync.usecases.GetAndroidContactNameUseCase
import ch.threema.app.androidcontactsync.usecases.UpdateContactNameUseCase
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.models.ContactModel
import ch.threema.domain.types.Identity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import testdata.TestData

class UpdateContactNameUseCaseTest {

    @Test
    fun `contact updated with current lookup info`() = runTest {
        val lookupInfo = LookupInfo(
            lookupKey = LookupKey("lookup"),
            contactId = ContactId(42u),
        )

        val uriMock: Uri = mockk()
        val androidContactLookupInfo = AndroidContactLookupInfo(
            lookupKey = lookupInfo.lookupKey.key,
            contactId = lookupInfo.contactId.id.toLong(),
        )
        val androidContactLookupInfoSpy: AndroidContactLookupInfo = spyk(androidContactLookupInfo) {
            every { getContactUri() } returns uriMock
        }
        val androidContact = AndroidContact(
            lookupInfo = lookupInfo,
            rawContacts = emptySet(),
        )

        val contactModelMock = getContactModelMock(
            androidContactLookupInfo = androidContactLookupInfoSpy,
        )
        with(contactModelMock) {
            every { setNameFromLocal("Erika", "Muster") } just Runs
        }

        val androidContactReaderMock: AndroidContactReader = mockk {
            coEvery { readAndroidContactWithLookup(uriMock) } returns androidContact
        }
        val getAndroidContactNameUseCaseMock: GetAndroidContactNameUseCase = mockk {
            every { call(androidContact) } returns ContactName.create(
                firstName = "Erika",
                lastName = "Muster",
            )
        }

        val updateContactNameUseCase = UpdateContactNameUseCase(
            androidContactReader = androidContactReaderMock,
            getAndroidContactNameUseCase = getAndroidContactNameUseCaseMock,
        )
        updateContactNameUseCase.call(setOf(contactModelMock))

        verify(exactly = 1) { contactModelMock.setNameFromLocal("Erika", "Muster") }
        verify(exactly = 0) { contactModelMock.setAndroidContactLookupKey(any()) }
    }

    @Test
    fun `contact updated with outdated lookup info`() = runTest {
        // This is the current lookup info that is returned from the content provider
        val newLookupInfo = LookupInfo(
            lookupKey = LookupKey("lookup"),
            contactId = ContactId(42u),
        )
        val newAndroidContactLookupInfo = AndroidContactLookupInfo(
            lookupKey = newLookupInfo.lookupKey.key,
            contactId = newLookupInfo.contactId.id.toLong(),
        )

        val uriMock: Uri = mockk()
        val outdatedAndroidContactLookupInfo = AndroidContactLookupInfo(
            lookupKey = "outdated",
            contactId = 41,
        )
        val outdatedAndroidContactLookupInfoSpy: AndroidContactLookupInfo =
            spyk(outdatedAndroidContactLookupInfo) {
                every { getContactUri() } returns uriMock
            }
        val androidContact = AndroidContact(
            lookupInfo = newLookupInfo,
            rawContacts = emptySet(),
        )

        val contactModelMock = getContactModelMock(
            androidContactLookupInfo = outdatedAndroidContactLookupInfoSpy,
        )
        with(contactModelMock) {
            every { setNameFromLocal("Erika", "Muster") } just Runs
            every { setAndroidContactLookupKey(newAndroidContactLookupInfo) } just Runs
        }

        val androidContactReaderMock: AndroidContactReader = mockk {
            coEvery { readAndroidContactWithLookup(uriMock) } returns androidContact
        }
        val getAndroidContactNameUseCaseMock: GetAndroidContactNameUseCase = mockk {
            every { call(androidContact) } returns ContactName.create(
                firstName = "Erika",
                lastName = "Muster",
            )
        }

        val updateContactNameUseCase = UpdateContactNameUseCase(
            androidContactReader = androidContactReaderMock,
            getAndroidContactNameUseCase = getAndroidContactNameUseCaseMock,
        )
        updateContactNameUseCase.call(setOf(contactModelMock))

        verify(exactly = 1) { contactModelMock.setNameFromLocal("Erika", "Muster") }
        verify(exactly = 1) {
            contactModelMock.setAndroidContactLookupKey(
                newAndroidContactLookupInfo,
            )
        }
    }

    @Test
    fun `contact not updated if no android contact is found`() = runTest {
        val uriMock: Uri = mockk()
        val androidContactLookupInfo = AndroidContactLookupInfo(
            lookupKey = "lookup",
            contactId = 42,
        )
        val androidContactLookupInfoSpy: AndroidContactLookupInfo = spyk(androidContactLookupInfo) {
            every { getContactUri() } returns uriMock
        }

        val contactModelMock = getContactModelMock(
            androidContactLookupInfo = androidContactLookupInfoSpy,
        )

        val androidContactReaderMock: AndroidContactReader = mockk {
            coEvery { readAndroidContactWithLookup(uriMock) } returns null
        }

        val updateContactNameUseCase = UpdateContactNameUseCase(
            androidContactReader = androidContactReaderMock,
            getAndroidContactNameUseCase = mockk(),
        )
        updateContactNameUseCase.call(setOf(contactModelMock))

        verify(exactly = 0) { contactModelMock.setNameFromLocal(any(), any()) }
        verify(exactly = 0) { contactModelMock.setAndroidContactLookupKey(any()) }
    }

    @Test
    fun `contact name not updated if no name can be determined from android contact`() = runTest {
        val uriMock: Uri = mockk()
        val androidContactLookupInfo = AndroidContactLookupInfo(
            lookupKey = "lookup",
            contactId = 42,
        )
        val androidContactLookupInfoSpy: AndroidContactLookupInfo = spyk(androidContactLookupInfo) {
            every { getContactUri() } returns uriMock
        }

        val androidContactMock: AndroidContact = mockk {
            every { lookupInfo } returns LookupInfo(
                lookupKey = LookupKey("lookup"),
                contactId = ContactId(42u),
            )
        }

        val contactModelMock = getContactModelMock(
            androidContactLookupInfo = androidContactLookupInfoSpy,
        )

        val androidContactReaderMock: AndroidContactReader = mockk {
            coEvery { readAndroidContactWithLookup(uriMock) } returns androidContactMock
        }
        val getAndroidContactNameUseCaseMock: GetAndroidContactNameUseCase = mockk {
            every { call(androidContactMock) } returns null
        }

        val updateContactNameUseCase = UpdateContactNameUseCase(
            androidContactReader = androidContactReaderMock,
            getAndroidContactNameUseCase = getAndroidContactNameUseCaseMock,
        )
        updateContactNameUseCase.call(setOf(contactModelMock))

        verify(exactly = 0) { contactModelMock.setNameFromLocal(any(), any()) }
        verify(exactly = 0) { contactModelMock.setAndroidContactLookupKey(any()) }
    }

    @Test
    fun `contact not updated if not linked to android contact`() = runTest {
        val contactModelMock = getContactModelMock()

        val androidContactReaderMock: AndroidContactReader = mockk()

        val updateContactNameUseCase = UpdateContactNameUseCase(
            androidContactReader = androidContactReaderMock,
            getAndroidContactNameUseCase = mockk(),
        )
        updateContactNameUseCase.call(setOf(contactModelMock))

        verify(exactly = 0) { contactModelMock.setNameFromLocal(any(), any()) }
        verify(exactly = 0) { contactModelMock.setAndroidContactLookupKey(any()) }
    }

    private fun getContactModelMock(
        identity: Identity = TestData.Identities.OTHER_1,
        androidContactLookupInfo: AndroidContactLookupInfo? = null,
    ): ContactModel {
        val contactModelDataMock = TestData.createContactModelData(
            identity = identity,
            androidContactLookupInfo = androidContactLookupInfo,
        )

        return mockk {
            every { this@mockk.identity } returns identity.value
            every { data } returns contactModelDataMock
        }
    }
}

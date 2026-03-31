package ch.threema.data

import ch.threema.app.managers.CoreServiceManager
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.models.ContactModelData
import ch.threema.domain.stores.IdentityStore
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData

class ContactModelDataTest {

    @Test
    fun `getDisplayName should pick correct values`() {
        val coreServiceManagerMock = mockk<CoreServiceManager>(relaxed = true) {
            every { identityStore } returns mockk<IdentityStore> {
                every { getIdentityString() } returns TestData.Identities.ME.value
            }
        }

        val testSet = mapOf(
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = "Firstname", lastname = "Lastname", nickname = "Nick")
                to "Firstname Lastname",
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = "Firstname", lastname = "", nickname = "Nick")
                to "Firstname",
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "Lastname", nickname = "Nick")
                to "Lastname",
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "", nickname = "Nick")
                to "~Nick",
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = "Firstname", lastname = "Lastname", nickname = null)
                to "Firstname Lastname",
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = "Firstname", lastname = "", nickname = null)
                to "Firstname",
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "Lastname", nickname = null)
                to "Lastname",
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "", nickname = null)
                to TestData.Identities.OTHER_1.value,
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "", nickname = "")
                to TestData.Identities.OTHER_1.value,
            TestData.createContactModel(identity = TestData.Identities.OTHER_1, firstname = " ", lastname = " ", nickname = " ")
                to TestData.Identities.OTHER_1.value,
            TestData.createContactModel(
                identity = null,
                firstname = "",
                lastname = "",
                nickname = null,
                coreServiceManagerMock = coreServiceManagerMock,
            )
                to ContactModelData.DISPLAY_NAME_INVALID_CONTACT,
            TestData.createContactModel(
                identity = TestData.Identities.OTHER_1,
                firstname = "",
                lastname = "",
                nickname = TestData.Identities.OTHER_1.value,
            )
                to TestData.Identities.OTHER_1.value,
        )
        val actualDisplayNames = testSet.keys.map { contactModel ->
            contactModel.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
                nicknameHasPrefix = true,
            )
        }
        assertEquals(
            expected = testSet.values.toList(),
            actual = actualDisplayNames,
        )
    }

    @Test
    fun `getDisplayName handles contactNameFormat correctly`() {
        val contact1 = TestData.createContactModel(firstname = "Firstname", lastname = "Lastname")
        assertEquals(
            expected = "Firstname Lastname",
            actual = contact1.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )

        val contact2 = TestData.createContactModel(firstname = "Firstname", lastname = "Lastname")
        assertEquals(
            expected = "Lastname Firstname",
            actual = contact2.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.LASTNAME_FIRSTNAME,
            ),
        )

        val contact3 = TestData.createContactModel(firstname = "  ", lastname = "Lastname")
        assertEquals(
            expected = "Lastname",
            actual = contact3.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )

        val contact4 = TestData.createContactModel(firstname = "Firstname", lastname = "  ")
        assertEquals(
            expected = "Firstname",
            actual = contact4.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )

        val contact5 = TestData.createContactModel(firstname = "  ", lastname = "Lastname")
        assertEquals(
            expected = "Lastname",
            actual = contact5.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.LASTNAME_FIRSTNAME,
            ),
        )

        val contact6 = TestData.createContactModel(firstname = "Firstname", lastname = "  ")
        assertEquals(
            expected = "Firstname",
            actual = contact6.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.LASTNAME_FIRSTNAME,
            ),
        )

        val contact7 = TestData.createContactModel(firstname = " Firstname ", lastname = " Lastname ")
        assertEquals(
            expected = "Firstname Lastname",
            actual = contact7.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )

        val contact8 = TestData.createContactModel(firstname = " Firstname ", lastname = " Lastname ")
        assertEquals(
            expected = "Lastname Firstname",
            actual = contact8.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.LASTNAME_FIRSTNAME,
            ),
        )

        val contact9 = TestData.createContactModel(firstname = " First Name ", lastname = " Last Name ")
        assertEquals(
            expected = "First Name Last Name",
            actual = contact9.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )
    }

    @Test
    fun `getDisplayName handles nicknameHasPrefix correctly`() {
        val contact1 = TestData.createContactModel(firstname = "", lastname = "", nickname = "Nick")
        assertEquals(
            expected = "~Nick",
            actual = contact1.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
                nicknameHasPrefix = true,
            ),
        )

        val contact2 = TestData.createContactModel(firstname = "", lastname = "", nickname = "Nick")
        assertEquals(
            expected = "Nick",
            actual = contact2.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
                nicknameHasPrefix = false,
            ),
        )

        val contact3 = TestData.createContactModel(firstname = "", lastname = "", nickname = " Nick ")
        assertEquals(
            expected = "~Nick",
            actual = contact3.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
                nicknameHasPrefix = true,
            ),
        )

        val contact4 = TestData.createContactModel(firstname = "", lastname = "", nickname = " Nick ")
        assertEquals(
            expected = "Nick",
            actual = contact4.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
                nicknameHasPrefix = false,
            ),
        )

        val contact5 = TestData.createContactModel(firstname = "", lastname = "", nickname = " ${TestData.Identities.OTHER_1} ")
        assertEquals(
            expected = TestData.Identities.OTHER_1.value,
            actual = contact5.data?.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
                nicknameHasPrefix = true,
            ),
        )
    }
}

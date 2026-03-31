package ch.threema.app.data.datatypes

import ch.threema.android.ResolvedString
import ch.threema.android.ResourceIdString
import ch.threema.android.toResolvedString
import ch.threema.app.R
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.MentionNameData
import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData

class MentionNameDataTest {

    @Test
    fun `getDisplayName for contacts should pick correct values`() {
        val testSet = mapOf(
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = "Firstname", lastname = "Lastname", nickname = "Nick")
                to "Firstname Lastname".toResolvedString(),
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = "Firstname", lastname = "", nickname = "Nick")
                to "Firstname".toResolvedString(),
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "Lastname", nickname = "Nick")
                to "Lastname".toResolvedString(),
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "", nickname = "Nick")
                to "~Nick".toResolvedString(),
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = "Firstname", lastname = "Lastname", nickname = null)
                to "Firstname Lastname".toResolvedString(),
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = "Firstname", lastname = "", nickname = null)
                to "Firstname".toResolvedString(),
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "Lastname", nickname = null)
                to "Lastname".toResolvedString(),
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "", nickname = null)
                to TestData.Identities.OTHER_1.value.toResolvedString(),
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = "", lastname = "", nickname = "")
                to TestData.Identities.OTHER_1.value.toResolvedString(),
            MentionNameData.Contact(identity = TestData.Identities.OTHER_1, firstname = " ", lastname = " ", nickname = " ")
                to TestData.Identities.OTHER_1.value.toResolvedString(),
            MentionNameData.Contact(
                identity = TestData.Identities.OTHER_1,
                firstname = "",
                lastname = "",
                nickname = TestData.Identities.OTHER_1.value,
            )
                to TestData.Identities.OTHER_1.value.toResolvedString(),
        )
        val actualDisplayNames = testSet.keys.map { contactModel ->
            contactModel.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            )
        }
        assertEquals(
            expected = testSet.values.toList(),
            actual = actualDisplayNames,
        )
    }

    @Test
    fun `getDisplayName for contacts handles contactNameFormat correctly`() {
        val mentionNameDataContact1 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "Firstname",
            lastname = "Lastname",
            nickname = null,
        )
        assertEquals(
            expected = "Firstname Lastname".toResolvedString(),
            actual = mentionNameDataContact1.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )

        val mentionNameDataContact2 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "Firstname",
            lastname = "Lastname",
            nickname = null,
        )
        assertEquals(
            expected = "Lastname Firstname".toResolvedString(),
            actual = mentionNameDataContact2.getDisplayName(
                contactNameFormat = ContactNameFormat.LASTNAME_FIRSTNAME,
            ),
        )

        val mentionNameDataContact3 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "  ",
            lastname = "Lastname",
            nickname = null,
        )
        assertEquals(
            expected = "Lastname".toResolvedString(),
            actual = mentionNameDataContact3.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )

        val mentionNameDataContact4 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "Firstname",
            lastname = "  ",
            nickname = null,
        )
        assertEquals(
            expected = "Firstname".toResolvedString(),
            actual = mentionNameDataContact4.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )

        val mentionNameDataContact5 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "  ",
            lastname = "Lastname",
            nickname = null,
        )
        assertEquals(
            expected = "Lastname".toResolvedString(),
            actual = mentionNameDataContact5.getDisplayName(
                contactNameFormat = ContactNameFormat.LASTNAME_FIRSTNAME,
            ),
        )

        val mentionNameDataContact6 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "Firstname",
            lastname = "  ",
            nickname = null,
        )
        assertEquals(
            expected = "Firstname".toResolvedString(),
            actual = mentionNameDataContact6.getDisplayName(
                contactNameFormat = ContactNameFormat.LASTNAME_FIRSTNAME,
            ),
        )

        val mentionNameDataContact7 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = " Firstname ",
            lastname = " Lastname ",
            nickname = null,
        )
        assertEquals(
            expected = "Firstname Lastname".toResolvedString(),
            actual = mentionNameDataContact7.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )

        val mentionNameDataContact8 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = " Firstname ",
            lastname = " Lastname ",
            nickname = null,
        )
        assertEquals(
            expected = "Lastname Firstname".toResolvedString(),
            actual = mentionNameDataContact8.getDisplayName(
                contactNameFormat = ContactNameFormat.LASTNAME_FIRSTNAME,
            ),
        )

        val mentionNameDataContact9 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = " First Name ",
            lastname = " Last Name ",
            nickname = null,
        )
        assertEquals(
            expected = "First Name Last Name".toResolvedString(),
            actual = mentionNameDataContact9.getDisplayName(
                contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
            ),
        )
    }

    @Test
    fun `getDisplayName for contacts handles nickname prefix correct`() {
        val mentionNameDataContact1 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "",
            lastname = "",
            nickname = "Nick",
        )
        assertEquals(
            expected = "~Nick".toResolvedString(),
            actual = mentionNameDataContact1.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
            ),
        )

        val mentionNameDataContact2 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "",
            lastname = "",
            nickname = " Nick ",
        )
        assertEquals(
            expected = "~Nick".toResolvedString(),
            actual = mentionNameDataContact2.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
            ),
        )

        val mentionNameDataContact3 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "",
            lastname = "",
            nickname = TestData.Identities.OTHER_1.value,
        )
        assertEquals(
            expected = TestData.Identities.OTHER_1.value.toResolvedString(),
            actual = mentionNameDataContact3.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
            ),
        )

        val mentionNameDataContact4 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "",
            lastname = "",
            nickname = " ${TestData.Identities.OTHER_1} ",
        )
        assertEquals(
            expected = TestData.Identities.OTHER_1.value.toResolvedString(),
            actual = mentionNameDataContact4.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
            ),
        )

        val mentionNameDataContact5 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "",
            lastname = "",
            nickname = "A${TestData.Identities.OTHER_1} ",
        )
        assertEquals(
            expected = "~A${TestData.Identities.OTHER_1}".toResolvedString(),
            actual = mentionNameDataContact5.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
            ),
        )

        val mentionNameDataContact6 = MentionNameData.Contact(
            identity = TestData.Identities.OTHER_1,
            firstname = "",
            lastname = "",
            nickname = " A ${TestData.Identities.OTHER_1} ",
        )
        assertEquals(
            expected = "~A ${TestData.Identities.OTHER_1}".toResolvedString(),
            actual = mentionNameDataContact6.getDisplayName(
                contactNameFormat = ContactNameFormat.DEFAULT,
            ),
        )
    }

    @Test
    fun `getDisplayName for own user picks correct value`() {
        // arrange
        val testSet = mapOf(
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = null)
                to ResourceIdString(R.string.me_myself_and_i),
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = "")
                to ResourceIdString(R.string.me_myself_and_i),
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = " ")
                to ResourceIdString(R.string.me_myself_and_i),
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = TestData.Identities.ME.value)
                to ResourceIdString(R.string.me_myself_and_i),
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = " ${TestData.Identities.ME} ")
                to ResourceIdString(R.string.me_myself_and_i),
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = "A${TestData.Identities.ME}")
                to ResolvedString("A${TestData.Identities.ME}"),
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = "Nickname")
                to ResolvedString("Nickname"),
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = "Nick Name")
                to ResolvedString("Nick Name"),
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = " Nickname ")
                to ResolvedString("Nickname"),
            MentionNameData.Me(identity = TestData.Identities.ME, nickname = " Nick Name ")
                to ResolvedString("Nick Name"),
        )

        // act/assert
        testSet.forEach { mapEntry ->
            assertEquals(
                mapEntry.value,
                mapEntry.key.getDisplayName(ContactNameFormat.DEFAULT),
            )
        }
    }
}

package ch.threema.app.utils

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactUtilTest {

    @Test
    fun canHaveCustomAvatar() {
        assertFalse(ContactUtil.canHaveCustomAvatar(null))
        // Normal contact, not linked
        assertTrue(ContactUtil.canHaveCustomAvatar(createModel("ECHOECHO").setAndroidContactLookupKey(null)))
        // Normal contact, linked
        assertFalse(ContactUtil.canHaveCustomAvatar(createModel("ECHOECHO").setAndroidContactLookupKey("ABC")))
        // Gateway contact, not linked
        assertFalse(ContactUtil.canHaveCustomAvatar(createModel("*COMPANY").setAndroidContactLookupKey(null)))
        // Gateway contact, linked
        assertFalse(ContactUtil.canHaveCustomAvatar(createModel("*COMPANY").setAndroidContactLookupKey("ABC")))
    }

    @Test
    fun canChangeFirstName() {
        assertFalse(ContactUtil.canChangeFirstName(null))

        assertTrue(ContactUtil.canChangeFirstName(createModel("*COMPANY").setAndroidContactLookupKey(null)))
        assertFalse(ContactUtil.canChangeFirstName(createModel("*COMPANY").setAndroidContactLookupKey("abc")))

        assertTrue(ContactUtil.canChangeFirstName(createModel("ECHOECHO").setAndroidContactLookupKey(null)))
        assertFalse(ContactUtil.canChangeFirstName(createModel("ECHOECHO").setAndroidContactLookupKey("abc")))
    }

    @Test
    fun canChangeLastName() {
        assertFalse(ContactUtil.canChangeLastName(null))

        assertFalse(ContactUtil.canChangeLastName(createModel("*COMPANY").setAndroidContactLookupKey(null)))
        assertFalse(ContactUtil.canChangeLastName(createModel("*COMPANY").setAndroidContactLookupKey("abc")))

        assertTrue(ContactUtil.canChangeLastName(createModel("ECHOECHO").setAndroidContactLookupKey(null)))
        assertFalse(ContactUtil.canChangeLastName(createModel("ECHOECHO").setAndroidContactLookupKey("abc")))
    }

    @Test
    fun canChangeAvatar() {
        val preferenceServiceMock = mockk<PreferenceService> {
            // Preferences disabled
            every { profilePicReceive } returns false
        }
        val fileServiceMock = mockk<FileService> {
            // No contact defined profile picture set
            every { hasContactDefinedProfilePicture(any()) } returns false
        }

        // Normal contact, not linked
        assertTrue(
            ContactUtil.canChangeAvatar(
                createModel("ECHOECHO").setAndroidContactLookupKey(null),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Normal contact, linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("ECHOECHO").setAndroidContactLookupKey("ABC"),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Gateway contact, not linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("*COMPANY").setAndroidContactLookupKey(null),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Gateway contact, linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("*COMPANY").setAndroidContactLookupKey("ABC"),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )

        // Preferences disabled
        every { preferenceServiceMock.profilePicReceive } returns false

        // Contact defined profile picture set
        every { fileServiceMock.hasContactDefinedProfilePicture(any()) } returns true

        // Normal contact, not linked
        assertTrue(
            ContactUtil.canChangeAvatar(
                createModel("ECHOECHO").setAndroidContactLookupKey(null),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Normal contact, linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("ECHOECHO").setAndroidContactLookupKey("ABC"),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Gateway contact, not linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("*COMPANY").setAndroidContactLookupKey(null),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Gateway contact, linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("*COMPANY").setAndroidContactLookupKey("ABC"),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )

        // Preferences enabled
        every { preferenceServiceMock.profilePicReceive } returns true

        // No contact defined profile picture set
        every { fileServiceMock.hasContactDefinedProfilePicture(any()) } returns false

        // Normal contact, not linked
        assertTrue(
            ContactUtil.canChangeAvatar(
                createModel("ECHOECHO").setAndroidContactLookupKey(null),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Normal contact, linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("ECHOECHO").setAndroidContactLookupKey("ABC"),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Gateway contact, not linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("*COMPANY").setAndroidContactLookupKey(null),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Gateway contact, linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("*COMPANY").setAndroidContactLookupKey("ABC"),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )

        // Preferences enabled
        every { preferenceServiceMock.profilePicReceive } returns true

        // Contact defined profile picture set
        every { fileServiceMock.hasContactDefinedProfilePicture(any()) } returns true

        // Normal contact, not linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("ECHOECHO").setAndroidContactLookupKey(null),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Normal contact, linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("ECHOECHO").setAndroidContactLookupKey("ABC"),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Gateway contact, not linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("*COMPANY").setAndroidContactLookupKey(null),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
        // Gateway contact, linked
        assertFalse(
            ContactUtil.canChangeAvatar(
                createModel("*COMPANY").setAndroidContactLookupKey("ABC"),
                preferenceServiceMock,
                fileServiceMock,
            ),
        )
    }

    private fun createModel(identity: Identity) = ContactModel.create(identity, ByteArray(32))
}

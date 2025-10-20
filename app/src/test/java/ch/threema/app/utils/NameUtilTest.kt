/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.utils

import ch.threema.app.ThreemaApplication
import ch.threema.app.services.UserService
import ch.threema.storage.models.ContactModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NameUtilTest {
    private lateinit var userServiceMock: UserService

    @BeforeTest
    fun setUp() {
        userServiceMock = mockk<UserService> {
            every { isMe(ME_IDENTITY) } returns true
            every { isMe(OTHER_IDENTITY) } returns false
            every { identity } returns ME_IDENTITY
        }

        // TODO(ANDR-4219): We have to mock ServiceManager, as it is sneakily referenced somewhere deep down the stack. This needs to be cleaned up.
        mockkObject(ThreemaApplication)
        every { ThreemaApplication.getServiceManager() } returns null
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(ThreemaApplication)
    }

    @Test
    fun testGetQuoteNameNull() {
        val name = NameUtil.getQuoteName(null, userServiceMock)
        assertEquals("", name)
    }

    @Test
    fun testGetQuoteNameMeNickname() {
        every { userServiceMock.publicNickname } returns "Mr. Dushi"

        val contactModel = ContactModel.create(ME_IDENTITY, ByteArray(32))
        contactModel.firstName = "Moi"
        val name = NameUtil.getQuoteName(contactModel, userServiceMock)
        assertEquals("Mr. Dushi", name)
    }

    @Test
    fun testGetQuoteNameMeIdentityNickname() {
        every { userServiceMock.publicNickname } returns ME_IDENTITY

        val contactModel = ContactModel.create(ME_IDENTITY, ByteArray(32))
        contactModel.firstName = "Moi"
        val name = NameUtil.getQuoteName(contactModel, userServiceMock)
        assertEquals("Moi", name)
    }

    @Test
    fun testGetQuoteNameMeEmptyNickname() {
        every { userServiceMock.publicNickname } returns ""

        val contactModel = ContactModel.create(ME_IDENTITY, ByteArray(32))
        contactModel.firstName = "Moi"
        val name = NameUtil.getQuoteName(contactModel, userServiceMock)
        assertEquals("Moi", name)
    }

    @Test
    fun testGetQuoteNameMeNullNickname() {
        every { userServiceMock.publicNickname } returns null

        val contactModel = ContactModel.create(ME_IDENTITY, ByteArray(32))
        contactModel.firstName = "Moi"
        val name = NameUtil.getQuoteName(contactModel, userServiceMock)
        assertEquals("Moi", name)
    }

    @Test
    fun testGetQuoteNameOtherName() {
        val contactModel = ContactModel.create(OTHER_IDENTITY, ByteArray(32))
        contactModel.setPublicNickName("nickname")
        contactModel.firstName = "Joggeli"
        contactModel.lastName = "Rüdisüli"
        val name = NameUtil.getQuoteName(contactModel, userServiceMock)
        assertEquals("Joggeli Rüdisüli", name)
    }

    @Test
    fun testGetQuoteNameOtherNoName() {
        val contactModel = ContactModel.create(OTHER_IDENTITY, ByteArray(32))
        contactModel.setPublicNickName("nickname")
        contactModel.firstName = null
        contactModel.lastName = null
        val name = NameUtil.getQuoteName(contactModel, userServiceMock)
        assertEquals("~nickname", name)
    }

    @Test
    fun testGetFirstLastNameFromDisplayNameNull() {
        val firstLastName = NameUtil.getFirstLastNameFromDisplayName(null)
        assertEquals("", firstLastName.first)
        assertEquals("", firstLastName.second)
    }

    @Test
    fun testGetFirstLastNameFromDisplayNameEmpty() {
        val firstLastName = NameUtil.getFirstLastNameFromDisplayName("")
        assertEquals("", firstLastName.first)
        assertEquals("", firstLastName.second)
    }

    @Test
    fun testGetFirstLastNameFromDisplayNameOnlyFirst() {
        val firstLastName = NameUtil.getFirstLastNameFromDisplayName("joe")
        assertEquals("joe", firstLastName.first)
        assertEquals("", firstLastName.second)
    }

    @Test
    fun testGetFirstLastNameFromDisplayNameTwoParts() {
        val firstLastName = NameUtil.getFirstLastNameFromDisplayName("john doe")
        assertEquals("john", firstLastName.first)
        assertEquals("doe", firstLastName.second)
    }

    @Test
    fun testGetFirstLastNameFromDisplayNameSpanishCraziness() {
        val firstLastName = NameUtil.getFirstLastNameFromDisplayName(
            "Pablo Diego José Francisco de Paula Juan Nepomuceno María de los Remedios Cipriano de la Santísima Trinidad Ruiz y Picasso",
        )
        // Yes, this is actually wrong, but we cannot know how to properly split first and last name.
        assertEquals("Pablo", firstLastName.first)
        assertEquals(
            "Diego José Francisco de Paula Juan Nepomuceno María de los Remedios Cipriano de la Santísima Trinidad Ruiz y Picasso",
            firstLastName.second,
        )
    }

    companion object {
        private const val ME_IDENTITY = "MEMEMEME"
        private const val OTHER_IDENTITY = "OTHERRRR"
    }
}

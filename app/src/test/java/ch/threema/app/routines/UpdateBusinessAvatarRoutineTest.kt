/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.routines

import ch.threema.app.services.ApiService
import ch.threema.app.services.ContactService
import ch.threema.app.services.FileService
import ch.threema.common.Http
import ch.threema.common.plus
import ch.threema.data.models.ContactModel
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.buildResponse
import ch.threema.testhelpers.mockNoRequestOkHttpClient
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.net.URL
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.days
import okhttp3.ResponseBody.Companion.toResponseBody

class UpdateBusinessAvatarRoutineTest {

    @Test
    fun `download avatar, expiration date is updated`() {
        val timeProvider = TestTimeProvider()
        val tempFile = File.createTempFile("business-avatar", "test")
        val contactServiceMock = mockk<ContactService> {
            every {
                setUserDefinedProfilePicture(IDENTITY, tempFile, TriggerSource.LOCAL)
            } answers {
                assertEquals("avatar-data", secondArg<File>().readText())
                true
            }
        }
        val fileServiceMock = mockk<FileService> {
            every { createTempFile(any(), any()) } returns tempFile
        }
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns IDENTITY
            every { data } returns mockk {
                every { isAvatarExpired(timeProvider.get()) } returns true
            }
        }
        val okHttpClientMock = mockOkHttpClient { request ->
            assertEquals("GET", request.method)
            assertEquals("https://test.threema.com/avatar", request.url.toString())

            request.buildResponse {
                header("Expires", "Tue, 05 Aug 2025 07:28:00 GMT")
                body("avatar-data".toResponseBody())
            }
        }

        val routine = UpdateBusinessAvatarRoutine(
            okHttpClient = okHttpClientMock,
            contactService = contactServiceMock,
            fileService = fileServiceMock,
            apiService = apiServiceMock,
            timeProvider = timeProvider,
        )

        routine.run(contactModelMock, forceUpdate = false)

        verify { contactServiceMock.setUserDefinedProfilePicture(IDENTITY, tempFile, TriggerSource.LOCAL) }
        verify { contactModelMock.setLocalAvatarExpires(Instant.parse("2025-08-05T07:28:00Z")) }
        assertFalse(tempFile.exists())
    }

    @Test
    fun `expiration date is updated and profile picture removed when avatar not found`() {
        val timeProvider = TestTimeProvider()
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns IDENTITY
            every { data } returns mockk {
                every { isAvatarExpired(timeProvider.get()) } returns true
            }
        }
        val fileServiceMock = mockk<FileService>(relaxed = true)
        val okHttpClientMock = mockOkHttpClient { request ->
            request.respondWith(code = Http.StatusCode.NOT_FOUND)
        }

        val routine = UpdateBusinessAvatarRoutine(
            okHttpClient = okHttpClientMock,
            contactService = mockk(),
            fileService = fileServiceMock,
            apiService = apiServiceMock,
            timeProvider = timeProvider,
        )

        routine.run(contactModelMock, forceUpdate = false)

        verify { fileServiceMock.removeUserDefinedProfilePicture(IDENTITY) }
        verify { contactModelMock.setLocalAvatarExpires(timeProvider.get() + 1.days) }
    }

    @Test
    fun `no request made if not a gateway contact`() {
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns "TESTTEST"
        }

        val routine = UpdateBusinessAvatarRoutine(
            okHttpClient = mockNoRequestOkHttpClient(),
            contactService = mockk(),
            fileService = mockk(),
            apiService = apiServiceMock,
            timeProvider = TestTimeProvider(),
        )

        routine.run(contactModelMock, forceUpdate = false)
    }

    @Test
    fun `no request made if not expired`() {
        val timeProvider = TestTimeProvider()
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns "TESTTEST"
            every { data } returns mockk {
                every { isAvatarExpired(timeProvider.get()) } returns false
            }
        }

        val routine = UpdateBusinessAvatarRoutine(
            okHttpClient = mockNoRequestOkHttpClient(),
            contactService = mockk(),
            fileService = mockk(),
            apiService = apiServiceMock,
            timeProvider = timeProvider,
        )

        routine.run(contactModelMock, forceUpdate = false)
    }

    @Test
    fun `no request made if deleted`() {
        val timeProvider = TestTimeProvider()
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns "TESTTEST"
            every { data } returns null
        }

        val routine = UpdateBusinessAvatarRoutine(
            okHttpClient = mockNoRequestOkHttpClient(),
            contactService = mockk(),
            fileService = mockk(),
            apiService = apiServiceMock,
            timeProvider = timeProvider,
        )

        routine.run(contactModelMock, forceUpdate = false)
    }

    @Test
    fun `request is made when update is forced, regardless of expiration`() {
        val timeProvider = TestTimeProvider()
        val tempFile = File.createTempFile("business-avatar", "test")
        val contactServiceMock = mockk<ContactService>(relaxed = true)
        val fileServiceMock = mockk<FileService> {
            every { createTempFile(any(), any()) } returns tempFile
        }
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns IDENTITY
            every { data } returns mockk {
                every { isAvatarExpired(timeProvider.get()) } returns false
            }
        }
        val okHttpClientMock = mockOkHttpClient { request ->
            request.buildResponse {
                body("avatar-data".toResponseBody())
            }
        }

        val routine = UpdateBusinessAvatarRoutine(
            okHttpClient = okHttpClientMock,
            contactService = contactServiceMock,
            fileService = fileServiceMock,
            apiService = apiServiceMock,
            timeProvider = timeProvider,
        )

        routine.run(contactModelMock, forceUpdate = true)

        verify { contactServiceMock.setUserDefinedProfilePicture(IDENTITY, tempFile, TriggerSource.LOCAL) }
        verify { contactModelMock.setLocalAvatarExpires(timeProvider.get() + 1.days) }
    }

    companion object {
        private const val IDENTITY = "*TESTING"

        val apiServiceMock = mockk<ApiService> {
            every { getAvatarURL(IDENTITY) } returns URL("https://test.threema.com/avatar")
            every { getAuthToken() } returns "my-token-123"
        }
    }
}

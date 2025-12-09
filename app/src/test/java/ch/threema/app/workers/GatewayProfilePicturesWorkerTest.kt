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

package ch.threema.app.workers

import ch.threema.app.services.ApiService
import ch.threema.app.services.ContactService
import ch.threema.app.services.FileService
import ch.threema.app.test.koinTestModuleRule
import ch.threema.app.test.mockAppReady
import ch.threema.common.Http
import ch.threema.common.TimeProvider
import ch.threema.common.plus
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.buildResponse
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.net.URL
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule

class GatewayProfilePicturesWorkerTest {
    private lateinit var contactModelRepositoryMock: ContactModelRepository
    private val timeProvider = TestTimeProvider()
    private lateinit var okHttpClientMock: OkHttpClient
    private lateinit var contactServiceMock: ContactService
    private lateinit var fileServiceMock: FileService
    private val apiServiceMock = mockk<ApiService> {
        every { getAvatarURL(IDENTITY) } returns URL("https://test.threema.com/avatar")
        every { getAuthToken() } returns "my-token-123"
    }

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory { mockAppReady() }
        factory<TimeProvider> { timeProvider }
        factory<ContactModelRepository> { contactModelRepositoryMock }
        factory<OkHttpClient> { okHttpClientMock }
        factory<ContactService> { contactServiceMock }
        factory<ApiService> { apiServiceMock }
        factory<FileService> { fileServiceMock }
    }

    @Test
    fun `profile picture is updated, expiration date is updated`() = runTest {
        val profilePicture = byteArrayOf(0, 1, 2, 3)
        contactServiceMock = mockk<ContactService> {
            every {
                setUserDefinedProfilePicture(IDENTITY, profilePicture, TriggerSource.LOCAL)
            } returns true
        }
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns IDENTITY
            every { data } returns mockk {
                every { isAvatarExpired(any(), 12.hours) } returns true
                every { isGatewayContact() } returns true
            }
        }
        contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns listOf(contactModelMock)
        }
        fileServiceMock = mockk<FileService> {
            every { getUserDefinedProfilePictureStream(IDENTITY) } returns ByteArrayInputStream(byteArrayOf(42))
        }
        okHttpClientMock = mockOkHttpClient { request ->
            assertEquals("GET", request.method)
            assertEquals("https://test.threema.com/avatar", request.url.toString())

            request.buildResponse {
                header("Expires", "Tue, 05 Aug 2025 07:28:00 GMT")
                body(profilePicture.toResponseBody())
            }
        }

        val worker = GatewayProfilePicturesWorker(mockk(), mockk())
        worker.doWork()

        verify(exactly = 1) {
            contactServiceMock.setUserDefinedProfilePicture(
                IDENTITY,
                profilePicture,
                TriggerSource.LOCAL,
            )
        }
        verify(exactly = 1) { contactModelMock.setLocalAvatarExpires(Instant.parse("2025-08-05T07:28:00Z")) }
    }

    @Test
    fun `profile picture stays the same, expiration date is updated`() = runTest {
        val profilePicture = byteArrayOf(0, 1, 2, 3)
        contactServiceMock = mockk<ContactService>()
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns IDENTITY
            every { data } returns mockk {
                every { isAvatarExpired(any(), 12.hours) } returns true
                every { isGatewayContact() } returns true
            }
        }
        contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns listOf(contactModelMock)
        }
        fileServiceMock = mockk<FileService> {
            every { getUserDefinedProfilePictureStream(IDENTITY) } returns ByteArrayInputStream(profilePicture)
        }
        okHttpClientMock = mockOkHttpClient { request ->
            assertEquals("GET", request.method)
            assertEquals("https://test.threema.com/avatar", request.url.toString())

            request.buildResponse {
                header("Expires", "Tue, 05 Aug 2025 07:28:00 GMT")
                body(profilePicture.toResponseBody())
            }
        }

        val worker = GatewayProfilePicturesWorker(mockk(), mockk())
        worker.doWork()

        verify(exactly = 0) {
            contactServiceMock.setUserDefinedProfilePicture(
                IDENTITY,
                profilePicture,
                TriggerSource.LOCAL,
            )
        }
        verify(exactly = 1) { contactModelMock.setLocalAvatarExpires(Instant.parse("2025-08-05T07:28:00Z")) }
    }

    @Test
    fun `profile picture is not refreshed if profile picture is not expired`() = runTest {
        contactServiceMock = mockk<ContactService>()
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns IDENTITY
            every { data } returns mockk {
                every { isAvatarExpired(any(), 12.hours) } returns false
                every { isGatewayContact() } returns true
            }
        }
        contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns listOf(contactModelMock)
        }

        val worker = GatewayProfilePicturesWorker(mockk(), mockk())
        worker.doWork()
    }

    @Test
    fun `profile picture is not refreshed if contact is not a gateway contact`() = runTest {
        contactServiceMock = mockk<ContactService>()
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns IDENTITY
            every { data } returns mockk {
                every { isGatewayContact() } returns false
            }
        }
        contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns listOf(contactModelMock)
        }

        val worker = GatewayProfilePicturesWorker(mockk(), mockk())
        worker.doWork()
    }

    @Test
    fun `profile picture is removed and expiration date is updated if profile picture not found`() = runTest {
        contactServiceMock = mockk<ContactService> {
            every {
                removeUserDefinedProfilePicture(IDENTITY, TriggerSource.LOCAL)
            } returns true
        }
        val contactModelMock = mockk<ContactModel>(relaxed = true) {
            every { identity } returns IDENTITY
            every { data } returns mockk {
                every { isAvatarExpired(any(), 12.hours) } returns true
                every { isGatewayContact() } returns true
            }
        }
        contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns listOf(contactModelMock)
        }
        okHttpClientMock = mockOkHttpClient { request ->
            assertEquals("GET", request.method)
            assertEquals("https://test.threema.com/avatar", request.url.toString())

            request.respondWith(code = Http.StatusCode.NOT_FOUND)
        }

        val worker = GatewayProfilePicturesWorker(mockk(), mockk())
        worker.doWork()

        verify(exactly = 1) { contactServiceMock.removeUserDefinedProfilePicture(IDENTITY, TriggerSource.LOCAL) }
        verify(exactly = 1) { contactModelMock.setLocalAvatarExpires(timeProvider.get() + 1.days) }
    }

    companion object {
        private const val IDENTITY = "*TESTING"
    }
}

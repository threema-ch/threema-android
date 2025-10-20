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

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.common.plus
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.buildResponse
import ch.threema.testhelpers.mockNoRequestOkHttpClient
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import okhttp3.ResponseBody.Companion.toResponseBody

class UpdateAppLogoRoutineTest {
    @Test
    fun `no stored expiration, remote provides expiration`() {
        val lightFile = File.createTempFile("logo-light", "test")
        val darkFile = File.createTempFile("logo-dark", "test")
        val fileServiceMock = mockk<FileService> {
            every { createTempFile(any(), any()) } returnsMany listOf(lightFile, darkFile)
            every { saveAppLogo(any(), "0") } answers {
                assertEquals(LIGHT_LOGO_CONTENT, firstArg<File>().readText())
            }
            every { saveAppLogo(any(), "1") } answers {
                assertEquals(DARK_LOGO_CONTENT, firstArg<File>().readText())
            }
        }
        val preferenceServiceMock = mockk<PreferenceService> {
            every { getAppLogoExpiresAt(any()) } returns null
            every { setAppLogo(any(), any()) } just runs
            every { setAppLogoExpiresAt(any(), any()) } just runs
        }
        val updateAppLogoRoutine = UpdateAppLogoRoutine(
            fileService = fileServiceMock,
            preferenceService = preferenceServiceMock,
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("GET", request.method)
                request.buildResponse {
                    header("Expires", "Tue, 05 Aug 2025 07:28:00 GMT")
                    body(
                        when (val url = request.url.toString()) {
                            LIGHT_URL -> LIGHT_LOGO_CONTENT
                            DARK_URL -> DARK_LOGO_CONTENT
                            else -> fail("Unexpected URL $url")
                        }
                            .toResponseBody(),
                    )
                }
            },
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
            timeProvider = TestTimeProvider(),
        )

        updateAppLogoRoutine.run()

        verify { preferenceServiceMock.setAppLogo(LIGHT_URL, "0") }
        verify { preferenceServiceMock.setAppLogo(DARK_URL, "1") }
        verify { preferenceServiceMock.setAppLogoExpiresAt(Instant.parse("2025-08-05T07:28:00Z"), "0") }
        verify { preferenceServiceMock.setAppLogoExpiresAt(Instant.parse("2025-08-05T07:28:00Z"), "1") }
        verify { fileServiceMock.saveAppLogo(lightFile, "0") }
        verify { fileServiceMock.saveAppLogo(darkFile, "1") }
        assertFalse(lightFile.exists())
        assertFalse(darkFile.exists())
    }

    @Test
    fun `no stored expiration, remote provides invalid expiration, tomorrow is used as fallback`() {
        val timeProvider = TestTimeProvider()
        val fileServiceMock = mockk<FileService> {
            every { createTempFile(any(), any()) } returns File.createTempFile("logo", "test")
            every { saveAppLogo(any(), any()) } just runs
        }
        val preferenceServiceMock = mockk<PreferenceService> {
            every { getAppLogoExpiresAt(any()) } returns null
            every { setAppLogo(any(), any()) } just runs
            every { setAppLogoExpiresAt(any(), any()) } just runs
        }
        val updateAppLogoRoutine = UpdateAppLogoRoutine(
            fileService = fileServiceMock,
            preferenceService = preferenceServiceMock,
            okHttpClient = mockOkHttpClient { request ->
                request.buildResponse {
                    header("Expires", "not a valid date")
                    body(
                        when (val url = request.url.toString()) {
                            LIGHT_URL -> LIGHT_LOGO_CONTENT
                            DARK_URL -> DARK_LOGO_CONTENT
                            else -> fail("Unexpected URL $url")
                        }
                            .toResponseBody(),
                    )
                }
            },
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
            timeProvider = timeProvider,
        )

        updateAppLogoRoutine.run()

        verify { preferenceServiceMock.setAppLogoExpiresAt(timeProvider.get() + 1.days, "0") }
        verify { preferenceServiceMock.setAppLogoExpiresAt(timeProvider.get() + 1.days, "1") }
    }

    @Test
    fun `no request made if not yet expired`() {
        val timeProvider = TestTimeProvider()
        val preferenceServiceMock = mockk<PreferenceService> {
            every { getAppLogoExpiresAt(any()) } returns timeProvider.get() + 20.seconds
        }
        val updateAppLogoRoutine = UpdateAppLogoRoutine(
            fileService = mockk(),
            preferenceService = preferenceServiceMock,
            okHttpClient = mockNoRequestOkHttpClient(),
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
            timeProvider = timeProvider,
        )

        updateAppLogoRoutine.run()

        verify(exactly = 0) { preferenceServiceMock.setAppLogo(any(), any()) }
    }

    @Test
    fun `expiration is ignored if forced`() {
        val timeProvider = TestTimeProvider()
        val lightFile = File.createTempFile("logo-light", "test")
        val darkFile = File.createTempFile("logo-dark", "test")
        val fileServiceMock = mockk<FileService> {
            every { createTempFile(any(), any()) } returnsMany listOf(lightFile, darkFile)
            every { saveAppLogo(any(), "0") } answers {
                assertEquals(LIGHT_LOGO_CONTENT, firstArg<File>().readText())
            }
            every { saveAppLogo(any(), "1") } answers {
                assertEquals(DARK_LOGO_CONTENT, firstArg<File>().readText())
            }
        }
        val preferenceServiceMock = mockk<PreferenceService> {
            every { getAppLogoExpiresAt(any()) } returns timeProvider.get() + 20.seconds
            every { setAppLogo(any(), any()) } just runs
            every { setAppLogoExpiresAt(any(), any()) } just runs
        }
        val updateAppLogoRoutine = UpdateAppLogoRoutine(
            fileService = fileServiceMock,
            preferenceService = preferenceServiceMock,
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("GET", request.method)
                request.buildResponse {
                    header("Expires", "Tue, 05 Aug 2025 07:28:00 GMT")
                    body(
                        when (val url = request.url.toString()) {
                            LIGHT_URL -> LIGHT_LOGO_CONTENT
                            DARK_URL -> DARK_LOGO_CONTENT
                            else -> fail("Unexpected URL $url")
                        }
                            .toResponseBody(),
                    )
                }
            },
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = true,
            timeProvider = timeProvider,
        )

        updateAppLogoRoutine.run()

        verify { preferenceServiceMock.setAppLogo(LIGHT_URL, "0") }
        verify { preferenceServiceMock.setAppLogo(DARK_URL, "1") }
        verify { preferenceServiceMock.setAppLogoExpiresAt(Instant.parse("2025-08-05T07:28:00Z"), "0") }
        verify { preferenceServiceMock.setAppLogoExpiresAt(Instant.parse("2025-08-05T07:28:00Z"), "1") }
        verify { fileServiceMock.saveAppLogo(lightFile, "0") }
        verify { fileServiceMock.saveAppLogo(darkFile, "1") }
        assertFalse(lightFile.exists())
        assertFalse(darkFile.exists())
    }

    @Test
    fun `no request made and logos cleared if URLs are null`() {
        val fileServiceMock = mockk<FileService> {
            every { saveAppLogo(any(), any()) } just runs
        }
        val preferenceServiceMock = mockk<PreferenceService> {
            every { getAppLogoExpiresAt(any()) } returns null
            every { clearAppLogo(any()) } just runs
        }
        val updateAppLogoRoutine = UpdateAppLogoRoutine(
            fileService = fileServiceMock,
            preferenceService = preferenceServiceMock,
            okHttpClient = mockNoRequestOkHttpClient(),
            lightUrl = null,
            darkUrl = null,
            forceUpdate = false,
            timeProvider = TestTimeProvider(),
        )

        updateAppLogoRoutine.run()

        verify(exactly = 1) { preferenceServiceMock.clearAppLogo("0") }
        verify(exactly = 1) { preferenceServiceMock.clearAppLogo("1") }
        verify(exactly = 1) { fileServiceMock.saveAppLogo(null, "0") }
        verify(exactly = 1) { fileServiceMock.saveAppLogo(null, "1") }
    }

    @Test
    fun `request fails with IO exception`() {
        val fileServiceMock = mockk<FileService>()
        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true) {
            every { getAppLogoExpiresAt(any()) } returns null
        }
        val updateAppLogoRoutine = UpdateAppLogoRoutine(
            fileService = fileServiceMock,
            preferenceService = preferenceServiceMock,
            okHttpClient = mockOkHttpClient { request ->
                throw IOException()
            },
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
            timeProvider = TestTimeProvider(),
        )

        updateAppLogoRoutine.run()

        verify(exactly = 0) { preferenceServiceMock.setAppLogoExpiresAt(any(), any()) }
        verify(exactly = 0) { fileServiceMock.saveAppLogo(any(), any()) }
        verify(exactly = 0) { preferenceServiceMock.clearAppLogo(any()) }
    }

    @Test
    fun `request fails with 404 not found`() {
        val fileServiceMock = mockk<FileService>()
        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true) {
            every { getAppLogoExpiresAt(any()) } returns null
        }
        val updateAppLogoRoutine = UpdateAppLogoRoutine(
            fileService = fileServiceMock,
            preferenceService = preferenceServiceMock,
            okHttpClient = mockOkHttpClient { request ->
                request.respondWith(code = 404)
            },
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
            timeProvider = TestTimeProvider(),
        )

        updateAppLogoRoutine.run()

        verify(exactly = 0) { preferenceServiceMock.setAppLogoExpiresAt(any(), any()) }
        verify(exactly = 0) { fileServiceMock.saveAppLogo(any(), any()) }
        verify(exactly = 0) { preferenceServiceMock.clearAppLogo(any()) }
    }

    companion object {
        private const val LIGHT_URL = "http://light-url/"
        private const val DARK_URL = "http://dark-url/"

        private const val LIGHT_LOGO_CONTENT = "light-logo"
        private const val DARK_LOGO_CONTENT = "dark-logo"
    }
}

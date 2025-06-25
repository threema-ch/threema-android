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

package ch.threema.app.logging

import android.util.Log
import ch.threema.app.BuildConfig
import ch.threema.logging.backend.LogcatBackend
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.Test

class LogcatBackendTest {
    @Test
    fun testTagCleaning() {
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 1

        val backend = LogcatBackend(Log.INFO)

        backend.print(Log.WARN, "ch.threema.app.Hello", null, "hello")
        verify(exactly = 1) { Log.println(Log.WARN, BuildConfig.LOG_TAG, "Hello: hello") }

        backend.print(Log.INFO, "ch.threema.domain.Bye", null, "goodbye")
        verify(exactly = 1) { Log.println(Log.INFO, BuildConfig.LOG_TAG, "Bye: goodbye") }

        backend.print(Log.INFO, "ch.threema.app.subpackage.Abcd", null, "msg")
        verify(exactly = 1) { Log.println(Log.INFO, BuildConfig.LOG_TAG, "subpackage.Abcd: msg") }

        backend.print(Log.ERROR, "any.other.package", null, "hmmmm")
        verify(exactly = 1) { Log.println(Log.ERROR, BuildConfig.LOG_TAG, "any.other.package: hmmmm") }

        unmockkStatic(Log::class)
    }
}

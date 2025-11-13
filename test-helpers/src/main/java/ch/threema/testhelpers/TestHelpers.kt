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

package ch.threema.testhelpers

import app.cash.turbine.TurbineTestContext
import ch.threema.common.DispatcherProvider
import ch.threema.common.models.CryptographicByteArray
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Generate an array of length `length` and fill it using a non-cryptographically-secure
 * random number generator.
 *
 * (This is fine since it's only a test util.)
 */
fun nonSecureRandomArray(length: Int): ByteArray {
    return Random.nextBytes(length)
}

fun cryptographicByteArrayOf(vararg bytes: Byte) = CryptographicByteArray(bytes)

/**
 * Generate a random Threema ID using a non-cryptographically-secure random number generator.
 *
 * (This is fine since it's only a test util.)
 */
fun randomIdentity(): String {
    val allowedChars = ('A'..'Z') + ('0'..'9')
    return (1..8)
        .map { allowedChars.random() }
        .joinToString("")
}

@Suppress("FunctionName")
fun MUST_NOT_BE_CALLED(): Nothing {
    throw UnsupportedOperationException("This method must not be called")
}

fun Any.loadResource(file: String): String =
    loadResourceAsBytes(file).toString(Charsets.UTF_8)

fun Any.loadResourceAsBytes(file: String): ByteArray =
    (javaClass.classLoader.getResourceAsStream(file) ?: error("Resource file '$file' not found"))
        .use {
            it.readAllBytes()
        }

suspend fun <T> TurbineTestContext<T>.expectItem(expected: T) {
    assertEquals(expected, awaitItem())
}

fun TestScope.testDispatcherProvider(): DispatcherProvider {
    val testDispatcher: TestDispatcher = StandardTestDispatcher(testScheduler)
    return object : DispatcherProvider {
        override val worker: CoroutineDispatcher
            get() = testDispatcher
        override val io: CoroutineDispatcher
            get() = testDispatcher
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.unconfinedTestDispatcherProvider(): DispatcherProvider {
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(testScheduler)
    return object : DispatcherProvider {
        override val worker: CoroutineDispatcher
            get() = testDispatcher
        override val io: CoroutineDispatcher
            get() = testDispatcher
    }
}

fun createTempDirectory(prefix: String = "test"): File {
    val directory = File.createTempFile(prefix, "test")
    directory.delete()
    directory.mkdirs()
    return directory
}

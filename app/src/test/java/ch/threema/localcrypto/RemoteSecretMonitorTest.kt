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

package ch.threema.localcrypto

import ch.threema.localcrypto.MasterKeyTestData.WORK_URL
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.testhelpers.assertSuspendsForever
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSecretMonitorTest {
    @Test
    fun `monitor yields remote secret`() = runTest(timeout = 5.seconds) {
        class RemoteSecretLoopMock : RemoteSecretLoop {
            override val remoteSecret = CompletableDeferred<RemoteSecret>()

            override suspend fun run() {
                delay(1.seconds)
                remoteSecret.complete(remoteSecretMock)
                awaitCancellation()
            }

            override fun close() = Unit
        }
        val remoteSecretLoopMock = spyk(RemoteSecretLoopMock())
        val remoteSecretMonitor = RemoteSecretMonitor(
            remoteSecretClient = mockk {
                every { createRemoteSecretLoop(WORK_URL, remoteSecretParametersMock) } returns remoteSecretLoopMock
            },
        )

        // Start waiting for the remote secret
        val deferredRemoteSecret = async {
            remoteSecretMonitor.awaitRemoteSecretAndClear()
        }

        // The remote secret is not available before monitoring has started
        assertSuspendsForever {
            deferredRemoteSecret.await()
        }

        // Start monitoring
        val deferredMonitor = async {
            remoteSecretMonitor.monitor(WORK_URL, remoteSecretParametersMock)
        }

        // Remote secret is now available
        assertEquals(remoteSecretMock, deferredRemoteSecret.await())

        // The remote secret is forgotten once it was returned
        assertSuspendsForever {
            remoteSecretMonitor.awaitRemoteSecretAndClear()
        }

        // Monitoring keeps running after the remote secret is published
        assertFalse(deferredMonitor.isCompleted)
        assertSuspendsForever {
            deferredMonitor.await()
        }

        // Stop monitoring
        deferredMonitor.cancel()
        advanceUntilIdle()

        // The loop is closed when the monitor stops
        verify(exactly = 1) { remoteSecretLoopMock.close() }
    }

    @Test
    fun `monitor fails with error`() = runTest(timeout = 5.seconds) {
        class RemoteSecretLoopErrorMock : RemoteSecretLoop {
            override val remoteSecret = CompletableDeferred<RemoteSecret>()

            override suspend fun run() {
                remoteSecret.complete(remoteSecretMock)
                delay(1.seconds)
                throw RemoteSecretMonitorException()
            }

            override fun close() = Unit
        }
        val remoteSecretLoopMock = spyk(RemoteSecretLoopErrorMock())
        val remoteSecretMonitor = RemoteSecretMonitor(
            remoteSecretClient = mockk {
                every { createRemoteSecretLoop(WORK_URL, remoteSecretParametersMock) } returns remoteSecretLoopMock
            },
        )

        // Monitor fails with exception
        assertFailsWith<RemoteSecretMonitorException> {
            remoteSecretMonitor.monitor(WORK_URL, remoteSecretParametersMock)
        }

        // The remote secret is forgotten if the monitor stops
        assertSuspendsForever {
            remoteSecretMonitor.awaitRemoteSecretAndClear()
        }

        // The loop is closed when the monitor stops
        verify(exactly = 1) { remoteSecretLoopMock.close() }
    }

    companion object {
        private val remoteSecretParametersMock = mockk<RemoteSecretParameters>()
        private val remoteSecretMock = mockk<RemoteSecret>()
    }
}

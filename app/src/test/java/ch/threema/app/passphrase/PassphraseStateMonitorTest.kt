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

package ch.threema.app.passphrase

import android.content.Context
import ch.threema.app.services.PassphraseService
import ch.threema.localcrypto.models.PassphraseLockState
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PassphraseStateMonitorTest {

    @BeforeTest
    fun setUp() {
        mockkStatic(PassphraseService::class)
        every { PassphraseService.start(any()) } just runs
        every { PassphraseService.stop(any()) } just runs
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(PassphraseService::class)
        unmockkStatic(PassphraseService::start)
    }

    @Test
    fun `passphrase service is started and stopped when needed`() = runTest {
        val contextMock = mockk<Context>()
        val passphraseLockStateFlow = MutableStateFlow(PassphraseLockState.NO_PASSPHRASE)
        val passphraseStateMonitor = PassphraseStateMonitor(
            appContext = contextMock,
            masterKeyManager = mockk {
                every { passphraseLockState } returns passphraseLockStateFlow
            },
        )

        val job = launch {
            passphraseStateMonitor.monitorPassphraseLock()
        }

        // Initially our lock is NO_PASSPHRASE, but initially the service also isn't running, so we don't need to do anything
        advanceUntilIdle()
        verify(exactly = 0) { PassphraseService.stop(contextMock) }
        verify(exactly = 0) { PassphraseService.start(contextMock) }

        // When the passphrase state changes to LOCKED, we expect the service to remain stopped
        passphraseLockStateFlow.value = PassphraseLockState.LOCKED
        advanceUntilIdle()
        verify(exactly = 0) { PassphraseService.stop(contextMock) }
        verify(exactly = 0) { PassphraseService.start(contextMock) }

        // When the passphrase state changes to UNLOCKED, we expect the service to be started
        passphraseLockStateFlow.value = PassphraseLockState.UNLOCKED
        advanceUntilIdle()
        verify(exactly = 0) { PassphraseService.stop(contextMock) }
        verify(exactly = 1) { PassphraseService.start(contextMock) }

        // When the passphrase state changes to NO_PASSPHRASE, we expect the service to be stopped
        passphraseLockStateFlow.value = PassphraseLockState.NO_PASSPHRASE
        advanceUntilIdle()
        verify(exactly = 1) { PassphraseService.stop(contextMock) }
        verify(exactly = 1) { PassphraseService.start(contextMock) }

        job.cancel()
    }
}

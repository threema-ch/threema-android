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

import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretCreationResult
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class RemoteSecretManagerImplTest {

    @Test
    fun `remote secret protection should be activated`() {
        val remoteSecretManager = RemoteSecretManagerImpl(
            remoteSecretClient = mockk {
                coEvery { createRemoteSecret(clientParametersMock) } returns RemoteSecretCreationResult(
                    remoteSecret = remoteSecretMock,
                    parameters = remoteSecretParametersMock,
                )
            },
            shouldUseRemoteSecretProtection = { true },
            remoteSecretMonitor = remoteSecretMonitorMock,
            getWorkServerBaseUrl = { "" },
        )

        val result = remoteSecretManager.checkRemoteSecretProtection(lockData = null)

        assertEquals(
            RemoteSecretProtectionCheckResult.SHOULD_ACTIVATE,
            result,
        )
    }

    @Test
    fun `remote secret protection should be deactivated`() {
        val remoteSecretManager = RemoteSecretManagerImpl(
            remoteSecretClient = mockk {
                coEvery { createRemoteSecret(clientParametersMock) } returns RemoteSecretCreationResult(
                    remoteSecret = remoteSecretMock,
                    parameters = remoteSecretParametersMock,
                )
            },
            shouldUseRemoteSecretProtection = { false },
            remoteSecretMonitor = remoteSecretMonitorMock,
            getWorkServerBaseUrl = { "" },
        )

        val result = remoteSecretManager.checkRemoteSecretProtection(lockData = mockk())

        assertEquals(
            RemoteSecretProtectionCheckResult.SHOULD_DEACTIVATE,
            result,
        )
    }

    @Test
    fun `remote secret protection should remain activated`() {
        val remoteSecretManager = RemoteSecretManagerImpl(
            remoteSecretClient = mockk {
                coEvery { createRemoteSecret(clientParametersMock) } returns RemoteSecretCreationResult(
                    remoteSecret = remoteSecretMock,
                    parameters = remoteSecretParametersMock,
                )
            },
            shouldUseRemoteSecretProtection = { true },
            remoteSecretMonitor = remoteSecretMonitorMock,
            getWorkServerBaseUrl = { "" },
        )

        val result = remoteSecretManager.checkRemoteSecretProtection(lockData = mockk())

        assertEquals(
            RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED,
            result,
        )
    }

    @Test
    fun `remote secret protection should remain deactivated`() {
        val remoteSecretManager = RemoteSecretManagerImpl(
            remoteSecretClient = mockk {
                coEvery { createRemoteSecret(clientParametersMock) } returns RemoteSecretCreationResult(
                    remoteSecret = remoteSecretMock,
                    parameters = remoteSecretParametersMock,
                )
            },
            shouldUseRemoteSecretProtection = { false },
            remoteSecretMonitor = remoteSecretMonitorMock,
            getWorkServerBaseUrl = { "" },
        )

        val result = remoteSecretManager.checkRemoteSecretProtection(lockData = null)

        assertEquals(
            RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED,
            result,
        )
    }

    @Test
    fun `create remote secret`() = runTest {
        val remoteSecretManager = RemoteSecretManagerImpl(
            remoteSecretClient = mockk {
                coEvery { createRemoteSecret(clientParametersMock) } returns RemoteSecretCreationResult(
                    remoteSecret = remoteSecretMock,
                    parameters = remoteSecretParametersMock,
                )
            },
            shouldUseRemoteSecretProtection = { true },
            remoteSecretMonitor = remoteSecretMonitorMock,
            getWorkServerBaseUrl = { "" },
        )

        val result = remoteSecretManager.createRemoteSecret(clientParametersMock)

        assertEquals(
            RemoteSecretCreationResult(
                remoteSecret = remoteSecretMock,
                parameters = remoteSecretParametersMock,
            ),
            result,
        )
    }

    @Test
    fun `delete remote secret`() = runTest {
        val authenticationTokenMock = mockk<RemoteSecretAuthenticationToken>()
        val remoteSecretClient = mockk<RemoteSecretClient> {
            coEvery { deleteRemoteSecret(any(), any()) } just runs
        }
        val remoteSecretManager = RemoteSecretManagerImpl(
            remoteSecretClient = remoteSecretClient,
            shouldUseRemoteSecretProtection = mockk(),
            remoteSecretMonitor = remoteSecretMonitorMock,
            getWorkServerBaseUrl = { "" },
        )

        remoteSecretManager.deleteRemoteSecret(clientParametersMock, authenticationTokenMock)

        coVerify(exactly = 1) { remoteSecretClient.deleteRemoteSecret(clientParametersMock, authenticationTokenMock) }
    }

    companion object {
        private val remoteSecretMock = mockk<RemoteSecret>()
        private val remoteSecretParametersMock = mockk<RemoteSecretParameters>()
        private val remoteSecretMonitorMock = mockk<RemoteSecretMonitor> {
            coEvery { awaitRemoteSecretAndClear() } returns remoteSecretMock
        }
        private val clientParametersMock = mockk<RemoteSecretClientParameters>()
    }
}

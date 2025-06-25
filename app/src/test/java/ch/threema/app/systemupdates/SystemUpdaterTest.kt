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

package ch.threema.app.systemupdates

import android.content.SharedPreferences
import ch.threema.app.systemupdates.updates.SystemUpdate
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SystemUpdaterTest {
    @Test
    fun `init state before updates started`() {
        val systemUpdater = SystemUpdater(mockk())
        assertEquals(SystemUpdateState.INIT, systemUpdater.systemUpdateState.value)
    }

    @Test
    fun `no updates needed when initial version number is null`() = runTest {
        val (sharedPreferences, editor) = mockSharedPreferences(oldVersion = null)
        val systemUpdateProvider = mockSystemUpdateProvider(
            // value doesn't matter
            oldVersion = -1,
            newVersion = 20,
            updates = emptyList(),
        )
        val systemUpdater = SystemUpdater(sharedPreferences)

        val hasUpdates = systemUpdater.checkForUpdates(systemUpdateProvider, initialVersion = null)

        assertFalse(hasUpdates)
        verify(exactly = 0) { systemUpdateProvider.getUpdates(any()) }
        verify { editor.storeVersionNumber(20) }
        assertEquals(SystemUpdateState.READY, systemUpdater.systemUpdateState.value)
    }

    @Test
    fun `some updates needed, no version number stored yet`() = runTest {
        val mockUpdate1 = mockkUpdate(version = 14)
        val mockUpdate2 = mockkUpdate(version = 16)
        val (sharedPreferences, editor) = mockSharedPreferences(oldVersion = null)
        val systemUpdateProvider = mockSystemUpdateProvider(
            oldVersion = 10,
            newVersion = 20,
            updates = listOf(mockUpdate1, mockUpdate2),
        )
        val systemUpdater = SystemUpdater(sharedPreferences)

        val hasUpdates = systemUpdater.checkForUpdates(systemUpdateProvider, initialVersion = 10)
        systemUpdater.runUpdates()

        assertTrue(hasUpdates)
        verifyOrder {
            mockUpdate1.run()
            editor.storeVersionNumber(14)
            mockUpdate2.run()
            editor.storeVersionNumber(16)
            editor.storeVersionNumber(20)
        }
        assertEquals(SystemUpdateState.READY, systemUpdater.systemUpdateState.value)
    }

    @Test
    fun `some updates needed, version number already stored`() = runTest {
        val mockUpdate1 = mockkUpdate(version = 14)
        val mockUpdate2 = mockkUpdate(version = 16)
        val (sharedPreferences, editor) = mockSharedPreferences(oldVersion = 10)
        val systemUpdateProvider = mockSystemUpdateProvider(
            oldVersion = 10,
            newVersion = 20,
            updates = listOf(mockUpdate1, mockUpdate2),
        )
        val systemUpdater = SystemUpdater(sharedPreferences)

        val hasUpdates = systemUpdater.checkForUpdates(systemUpdateProvider, initialVersion = 0)
        systemUpdater.runUpdates()

        assertTrue(hasUpdates)
        verifyOrder {
            mockUpdate1.run()
            editor.storeVersionNumber(14)
            mockUpdate2.run()
            editor.storeVersionNumber(16)
            editor.storeVersionNumber(20)
        }
        assertEquals(SystemUpdateState.READY, systemUpdater.systemUpdateState.value)
    }

    @Test
    fun `no updates needed, already at latest version`() = runTest {
        val (sharedPreferences, editor) = mockSharedPreferences(oldVersion = 20)
        val systemUpdateProvider = mockSystemUpdateProvider(
            oldVersion = 20,
            newVersion = 20,
            updates = emptyList(),
        )
        val systemUpdater = SystemUpdater(sharedPreferences)

        val hasUpdates = systemUpdater.checkForUpdates(systemUpdateProvider, initialVersion = 0)

        assertFalse(hasUpdates)
        verify(exactly = 0) { editor.putInt(any(), any()) }
        assertEquals(SystemUpdateState.READY, systemUpdater.systemUpdateState.value)
    }

    @Test
    fun `no updates needed, not yet at latest version`() = runTest {
        val (sharedPreferences, editor) = mockSharedPreferences(oldVersion = 18)
        val systemUpdateProvider = mockSystemUpdateProvider(
            oldVersion = 18,
            newVersion = 20,
            updates = emptyList(),
        )
        val systemUpdater = SystemUpdater(sharedPreferences)

        val hasUpdates = systemUpdater.checkForUpdates(systemUpdateProvider, initialVersion = 0)

        assertFalse(hasUpdates)
        verify { editor.storeVersionNumber(20) }
        assertEquals(SystemUpdateState.READY, systemUpdater.systemUpdateState.value)
    }

    @Test
    fun `version number of last successful update is stored when an update fails`() = runTest {
        val mockUpdate1 = mockkUpdate(version = 14)
        val mockUpdate2 = mockk<SystemUpdate> {
            every { run() } answers { throw RuntimeException("oh oh") }
            every { getVersion() } returns 16
            every { getDescription() } returns null
        }
        val (sharedPreferences, editor) = mockSharedPreferences(oldVersion = 10)
        val systemUpdateProvider = mockSystemUpdateProvider(
            oldVersion = 10,
            newVersion = 20,
            updates = listOf(mockUpdate1, mockUpdate2),
        )
        val systemUpdater = SystemUpdater(sharedPreferences)

        val hasUpdates = systemUpdater.checkForUpdates(systemUpdateProvider, initialVersion = 0)
        val exception = assertFailsWith<SystemUpdateException> {
            systemUpdater.runUpdates()
        }

        assertTrue(hasUpdates)
        assertEquals(16, exception.failedSystemUpdateVersion)
        verifyOrder {
            mockUpdate1.run()
            editor.storeVersionNumber(14)
            mockUpdate2.run()
        }
        verify(exactly = 1) { editor.apply() }
        assertEquals(SystemUpdateState.PREPARING, systemUpdater.systemUpdateState.value)
    }

    private fun mockkUpdate(version: Int): SystemUpdate =
        mockk {
            every { run() } just runs
            every { getVersion() } returns version
            every { getDescription() } returns null
        }

    private fun mockSharedPreferences(oldVersion: Int?): Pair<SharedPreferences, SharedPreferences.Editor> {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val sharedPreferences = mockk<SharedPreferences> {
            every { getInt("system_update_version_number", any()) } answers { oldVersion ?: secondArg() }
            every { edit() } returns editor
        }
        return Pair(sharedPreferences, editor)
    }

    private fun mockSystemUpdateProvider(oldVersion: Int, newVersion: Int, updates: List<SystemUpdate>): SystemUpdateProvider =
        mockk<SystemUpdateProvider> {
            every { getVersion() } returns newVersion
            every { getUpdates(oldVersion = oldVersion) } returns updates
        }

    private fun SharedPreferences.Editor.storeVersionNumber(version: Int) {
        putInt("system_update_version_number", version)
        apply()
    }
}

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

package ch.threema.app.apptaskexecutor

import ch.threema.app.apptaskexecutor.tasks.RemoteSecretDeleteStepsTask
import ch.threema.storage.factories.AppTaskPersistenceFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DatabaseAppTaskPersistenceTest {

    private val validEncodedAppTaskData =
        "{\"type\":\"ch.threema.app.apptaskexecutor.tasks.RemoteSecretDeleteStepsTask.RemoteSecretDeleteStepsTaskData\"," +
            "\"authenticationToken\":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31]}"

    @Test
    fun `inserting tasks that cannot be serialized should not throw an exception`() = runTest {
        val appTaskPersistenceFactoryMock = mockk<AppTaskPersistenceFactory>()

        val databaseAppTaskPersistence = DatabaseAppTaskPersistence(appTaskPersistenceFactoryMock)

        databaseAppTaskPersistence.persistTask(mockk())

        verify(exactly = 0) { appTaskPersistenceFactoryMock.insert(any()) }
    }

    @Test
    fun `removing tasks that cannot be serialized should not throw an exception`() = runTest {
        val appTaskPersistenceFactoryMock = mockk<AppTaskPersistenceFactory>()

        val databaseAppTaskPersistence = DatabaseAppTaskPersistence(appTaskPersistenceFactoryMock)

        databaseAppTaskPersistence.removePersistedTask(mockk())

        verify(exactly = 0) { appTaskPersistenceFactoryMock.removeAll(any()) }
    }

    @Test
    fun `persisted tasks that fail to decode should just be skipped`() = runTest {
        val appTaskPersistenceFactoryMock = mockk<AppTaskPersistenceFactory>()
        every { appTaskPersistenceFactoryMock.getAll() } returns setOf(
            validEncodedAppTaskData,
            "this encoded task cannot be decoded :)",
        )

        val databaseAppTaskPersistence = DatabaseAppTaskPersistence(appTaskPersistenceFactoryMock)
        val persistedTasks = databaseAppTaskPersistence.loadAllPersistedTasks()
        assertTrue { persistedTasks.size == 1 }
        assertTrue { persistedTasks.first() is RemoteSecretDeleteStepsTask }
    }
}

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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.archive.TaskArchiverImpl
import ch.threema.app.tasks.archive.recovery.TaskRecoveryManager
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.storage.factories.TaskArchiveFactory
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class TaskArchiverTest {
    private val serviceManagerMock = mockk<ServiceManager>()

    @Test
    fun `old task encoding is recovered`() {
        val oldTaskEncoding = "oldTaskEncoding"
        // Note that the mocked persistable task represents its serializable data as SendPushTokenData as the SerializableTask interface is sealed
        val newTaskEncoding = "{\"type\":\"ch.threema.app.tasks.SendPushTokenTask.SendPushTokenData\",\"token\":\"recoveredTask\",\"tokenType\":0}"
        val recoveredTask = createPersistableTaskMock("recoveredTask")

        val taskArchiveFactoryMock = mockk<TaskArchiveFactory> {
            every { getAll() } returns listOf(oldTaskEncoding)
            every { replace(oldTaskEncoding, newTaskEncoding) } just Runs
        }

        val taskRecoveryManagerMock = mockk<TaskRecoveryManager> {
            every { recoverTask(oldTaskEncoding, serviceManagerMock) } returns recoveredTask
        }

        val taskArchiver = TaskArchiverImpl(
            taskArchiveFactory = taskArchiveFactoryMock,
            taskRecoveryManager = taskRecoveryManagerMock,
        )

        taskArchiver.setServiceManager(serviceManagerMock)

        assertContentEquals(listOf(recoveredTask), taskArchiver.loadAllTasks())
        verify(exactly = 1) { taskArchiveFactoryMock.getAll() }
        verify(exactly = 1) { taskArchiveFactoryMock.replace(oldTaskEncoding, newTaskEncoding) }
        verify(exactly = 1) { taskRecoveryManagerMock.recoverTask(oldTaskEncoding, serviceManagerMock) }
    }

    @Test
    fun `unknown task encoding does not return a task`() {
        val unknownTaskEncoding = "unknownTaskEncoding"

        val taskArchiveFactoryMock = mockk<TaskArchiveFactory> {
            every { getAll() } returns listOf(unknownTaskEncoding)
            every { removeOne(unknownTaskEncoding) } just Runs
        }

        val taskRecoveryManagerMock = mockk<TaskRecoveryManager> {
            every { recoverTask(unknownTaskEncoding, serviceManagerMock) } returns null
        }

        val taskArchiver = TaskArchiverImpl(
            taskArchiveFactory = taskArchiveFactoryMock,
            taskRecoveryManager = taskRecoveryManagerMock,
        )

        taskArchiver.setServiceManager(serviceManagerMock)

        assertTrue { taskArchiver.loadAllTasks().isEmpty() }
        verify(exactly = 1) { taskArchiveFactoryMock.getAll() }
        verify(exactly = 1) { taskRecoveryManagerMock.recoverTask(unknownTaskEncoding, serviceManagerMock) }
    }

    @Test
    fun `several task encodings can be recovered`() {
        val oldTaskEncodingA = "oldTaskEncodingA"
        // Note that the mocked persistable task represents its serializable data as SendPushTokenData as the SerializableTask interface is sealed
        val newTaskEncodingA = "{\"type\":\"ch.threema.app.tasks.SendPushTokenTask.SendPushTokenData\",\"token\":\"recoveredTaskA\",\"tokenType\":0}"
        val recoveredTaskA = createPersistableTaskMock("recoveredTaskA")

        val oldTaskEncodingB = "oldTaskEncodingB"
        // Note that the mocked persistable task represents its serializable data as SendPushTokenData as the SerializableTask interface is sealed
        val newTaskEncodingB = "{\"type\":\"ch.threema.app.tasks.SendPushTokenTask.SendPushTokenData\",\"token\":\"recoveredTaskB\",\"tokenType\":0}"
        val recoveredTaskB = createPersistableTaskMock("recoveredTaskB")

        val unknownTaskEncoding = "unknownTaskEncoding"

        val taskArchiveFactoryMock = mockk<TaskArchiveFactory> {
            every { getAll() } returns listOf(oldTaskEncodingA, unknownTaskEncoding, oldTaskEncodingB)
            every { removeOne(unknownTaskEncoding) } just Runs
            every { replace(oldTaskEncodingA, newTaskEncodingA) } just Runs
            every { replace(oldTaskEncodingB, newTaskEncodingB) } just Runs
        }

        val taskRecoveryManagerMock = mockk<TaskRecoveryManager> {
            every { recoverTask(oldTaskEncodingA, serviceManagerMock) } returns recoveredTaskA
            every { recoverTask(oldTaskEncodingB, serviceManagerMock) } returns recoveredTaskB
            every { recoverTask(unknownTaskEncoding, serviceManagerMock) } returns null
        }

        val taskArchiver = TaskArchiverImpl(
            taskArchiveFactory = taskArchiveFactoryMock,
            taskRecoveryManager = taskRecoveryManagerMock,
        )

        taskArchiver.setServiceManager(serviceManagerMock)

        assertContentEquals(listOf(recoveredTaskA, recoveredTaskB), taskArchiver.loadAllTasks())
        verify(exactly = 1) { taskArchiveFactoryMock.getAll() }
        verify(exactly = 1) { taskArchiveFactoryMock.replace(oldTaskEncodingA, newTaskEncodingA) }
        verify(exactly = 1) { taskArchiveFactoryMock.replace(oldTaskEncodingB, newTaskEncodingB) }
        verify(exactly = 3) { taskRecoveryManagerMock.recoverTask(any(), serviceManagerMock) }
    }

    private fun createPersistableTaskMock(name: String): Task<*, TaskCodec> {
        val persistableTaskMock = mockk<Task<*, TaskCodec>>(moreInterfaces = arrayOf(PersistableTask::class)) {
            every { type } returns name
        }

        every { (persistableTaskMock as PersistableTask).serialize() } returns SendPushTokenTask.SendPushTokenData(name, 0)

        return persistableTaskMock
    }
}

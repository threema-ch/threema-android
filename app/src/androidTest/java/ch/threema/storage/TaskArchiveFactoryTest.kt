/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.storage

import ch.threema.app.ThreemaApplication
import ch.threema.storage.factories.TaskArchiveFactory
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class TaskArchiveFactoryTest {
    private lateinit var taskArchiveFactory: TaskArchiveFactory

    @Before
    fun setup() {
        taskArchiveFactory = ThreemaApplication.requireServiceManager().databaseServiceNew.taskArchiveFactory
        taskArchiveFactory.deleteAll()
    }

    @After
    fun tearDown() {
        taskArchiveFactory.deleteAll()
    }

    @Test
    fun testAdd() {
        val encodedTasks = listOf(
            "encoded task 1",
            "encoded task 2",
            "encoded task 3",
        )
        encodedTasks.forEach { taskArchiveFactory.insert(it) }
        assertEquals(encodedTasks, taskArchiveFactory.getAll())
    }

    @Test
    fun testRemove() {
        val encodedTasks = listOf(
            "oldestTask",
            "firstRemoved",
            "task1",
            "task",
            "task2",
            "task",
        )
        encodedTasks.forEach { taskArchiveFactory.insert(it) }
        assertEquals(encodedTasks, taskArchiveFactory.getAll())

        taskArchiveFactory.remove("does not exist, so it should not have an effect")
        assertEquals(encodedTasks, taskArchiveFactory.getAll())

        taskArchiveFactory.remove("firstRemoved")
        assertEquals(listOf("task1", "task", "task2", "task"), taskArchiveFactory.getAll())

        taskArchiveFactory.removeOne("task")
        assertEquals(listOf("task1", "task2", "task"), taskArchiveFactory.getAll())

        taskArchiveFactory.removeOne("task2")
        assertEquals(listOf("task1", "task"), taskArchiveFactory.getAll())

        taskArchiveFactory.remove("task1")
        assertEquals(listOf("task"), taskArchiveFactory.getAll())

        taskArchiveFactory.remove("task")
        assertEquals(emptyList<String>(), taskArchiveFactory.getAll())

        taskArchiveFactory.remove("does not exist anymore, so it should not have an effect")
        assertEquals(emptyList<String>(), taskArchiveFactory.getAll())
    }

    @Test
    fun testTrim() {
        val encodedTasks = listOf(
            "encoded task 1",
            "encoded task 2",
            "encoded task 3",
        )
        encodedTasks.forEach { taskArchiveFactory.insert(" $it\n\n") }
        assertEquals(encodedTasks, taskArchiveFactory.getAll())
    }
}

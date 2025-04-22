/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.domain.taskmanager

/**
 * This interface defines the functionality of a task archiver to achieve persistence.
 */
interface TaskArchiver {
    /**
     * Add another task to the archive.
     */
    fun addTask(task: Task<*, TaskCodec>)

    /**
     * Remove the given task from the archive. Note that if we have several archived tasks that
     * cannot be distinguished from each other, then we should only delete the oldest archived task.
     */
    fun removeTask(task: Task<*, TaskCodec>)

    /**
     * Load all tasks in the same order they have been archived.
     */
    fun loadAllTasks(): List<Task<*, TaskCodec>>
}

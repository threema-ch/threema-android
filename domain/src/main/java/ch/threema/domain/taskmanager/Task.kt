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
 * A Task that executes a specific application action according to a complex execution plan.
 */
interface Task<out R, in TaskCodecType> {
    /**
     * Run the task.
     */
    suspend fun invoke(handle: TaskCodecType): R

    /**
     * The type of the task. This is only used for logging purposes.
     */
    val type: String
}

/**
 * Get the task as debug string. This is only used for debugging.
 */
fun <R, T> Task<R, T>.getDebugString(): String = "$type@${hashCode()}"

/**
 * An active task may send messages.
 */
interface ActiveTask<out R> : Task<R, ActiveTaskCodec>

/**
 * A passive task can only retrieve messages.
 */
interface PassiveTask<out R> : Task<R, PassiveTaskCodec>

interface TransactionTask<out R> : Task<R, ActiveTaskCodec>

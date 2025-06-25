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

package ch.threema.app.preference.service

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.PreferenceStoreInterface
import ch.threema.app.tasks.PersistableTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val logger = LoggingUtil.getThreemaLogger("SynchronizedSetting")

/**
 * This represents a setting that will be synchronized with MD.
 */
sealed class SynchronizedSetting<T>(
    @JvmField
    val preferenceKey: String,
    private val preferenceStore: PreferenceStoreInterface,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskManager: TaskManager,
    private val getValue: PreferenceStoreInterface.(preferenceKey: String) -> T,
    private val setValue: PreferenceStoreInterface.(preferenceKey: String, value: T) -> Unit,
) {
    private val stateFlow: MutableStateFlow<T> = MutableStateFlow(preferenceStore.getValue(preferenceKey))

    abstract fun instantiateReflectionTask(): Task<*, TaskCodec>

    fun get(): T = stateFlow.value

    /**
     * Set the value from local. A persistent task will be scheduled to reflect the change after persisting it if multi device is enabled.
     */
    @Synchronized
    fun setFromLocal(value: T) {
        persist(value)
        if (multiDeviceManager.isMultiDeviceActive) {
            val task = instantiateReflectionTask()
            taskManager.schedule(task)
            if (task !is PersistableTask) {
                logger.warn("To prevent de-syncs, the current implementation requires the sync task to be persistent")
            }
        }
    }

    /**
     * Set the value from sync. This means that the update is *not* reflected.
     */
    @Synchronized
    protected fun setFromSync(value: T) {
        persist(value)
    }

    /**
     * Reload the value from the preference store.
     *
     * TODO(PRD-152): Note that this method may be removed once the logic regarding mdm handling is refactored.
     */
    @JvmName("reload")
    internal fun reload() {
        stateFlow.tryEmit(preferenceStore.getValue(preferenceKey))
    }

    private fun persist(value: T) {
        preferenceStore.setValue(preferenceKey, value)
        stateFlow.tryEmit(value)
    }

    fun asStateFlow(): StateFlow<T> = stateFlow
}

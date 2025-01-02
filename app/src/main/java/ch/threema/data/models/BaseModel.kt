/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.data.models

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ModelDataFactory<TDataType, TDbType> {
    /**
     * Create the corresponding database type for this model data.
     */
    fun toDbType(value: TDataType): TDbType

    /**
     * Create the corresponding model for this database type.
     */
    fun toDataType(value: TDbType): TDataType
}

/**
 * This exception is thrown when a model, which was deleted, is being mutated.
 */
class ModelDeletedException(modelName: String, methodName: String)
    : RuntimeException("Cannot call method $methodName: $modelName was deleted")

/**
 * The base model is extended by every model.
 *
 * It handles reactivity and provides common APIs shared by all models.
 */
abstract class BaseModel<TData, TReflectionTask : Task<*, TaskCodec>?>(
    /**
     * Mutable state flow that holds the model data.
     *
     * The field is protected, and is only exposed through the [data] property.
     *
     * Initially, the data is present. If the model is deleted, the data is set to `null`.
     * From that point on, the model must not be modified anymore, and all methods that mutate
     * model state must throw [ModelDeletedException].
     *
     * NOTE: Access to [mutableData] should always be protected by a synchronized(this) block!
     */
    protected val mutableData: MutableStateFlow<TData?>,

    /**
     * The name of this model. Used for debugging purposes.
     */
    protected val modelName: String,

    /**
     * The multi device manager is needed to determine whether to reflect a change or not.
     */
    protected val multiDeviceManager: MultiDeviceManager,

    /**
     * The task manager is needed to schedule a task that reflects the changes.
     */
    protected val taskManager: TaskManager,
) {
    /**
     * State flow that holds
     */
    val data: StateFlow<TData?> = mutableData

    /**
     * Ensure that [data] is not null. Throw [ModelDeletedException] otherwise.
     */
    protected fun ensureNotDeleted(data: TData?, methodName: String): TData {
        if (data == null) {
            throw ModelDeletedException(modelName, methodName)
        }
        return data
    }

    /**
     * Helper function to update data in the model.
     *
     * @param methodName The name of the method using this helper.
     * @param detectChanges A function that determines whether or not data was modified.
     * @param updateData A function that receives the original data and returns the updated data.
     * @param updateDatabase A function that updates the database with the updated data.
     * @param onUpdated An optional function that is invoked at the end if data was updated.
     * @param reflectUpdateTask The task that should be executed after the fields have been updated.
     * Note that the [reflectUpdateTask] is only executed when MD is active.
     */
    protected fun updateFields(
        methodName: String,
        detectChanges: (originalData: TData) -> Boolean,
        updateData: (originalData: TData) -> TData,
        updateDatabase: (updatedData: TData) -> Unit,
        onUpdated: ((updatedData: TData) -> Unit)?,
        reflectUpdateTask: TReflectionTask? = null,
    ) {
        val updatedData = synchronized(this) {
            val originalData = ensureNotDeleted(mutableData.value, methodName)
            val dataChanged = detectChanges(originalData)
            if (dataChanged) {
                val updatedData = updateData(originalData)
                mutableData.value = updatedData
                updateDatabase(updatedData)
                if (reflectUpdateTask != null && multiDeviceManager.isMultiDeviceActive) {
                    taskManager.schedule(reflectUpdateTask)
                }
                updatedData
            } else {
                null
            }
        }
        if (updatedData != null && onUpdated != null) {
            onUpdated(updatedData)
        }
    }
}

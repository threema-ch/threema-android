package ch.threema.app.preference.service

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.PreferenceStore
import ch.threema.domain.taskmanager.TaskManager

sealed class SynchronizedBooleanSetting(
    preferenceKey: String,
    preferenceStore: PreferenceStore,
    multiDeviceManager: MultiDeviceManager,
    taskManager: TaskManager,
) : SynchronizedSetting<Boolean>(
    preferenceKey = preferenceKey,
    preferenceStore = preferenceStore,
    multiDeviceManager = multiDeviceManager,
    taskManager = taskManager,
    getValue = { key -> getBoolean(key) },
    setValue = { key, value: Boolean -> save(key, value) },
)

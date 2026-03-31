package ch.threema.app.preference.service

import android.content.Context
import ch.threema.app.R
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.tasks.ReflectSettingsSyncTask
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.KeyboardDataCollectionPolicy

private val logger = getThreemaLogger("KeyboardDataCollectionPolicySetting")

/**
 * The setting whether the keyboard should be requested to disable data collection. Stores true if the collection should be prevented.
 */
class KeyboardDataCollectionPolicySetting internal constructor(
    multiDeviceManager: MultiDeviceManager,
    taskManager: TaskManager,
    preferenceStore: PreferenceStore,
    context: Context,
) : SynchronizedBooleanSetting(
    preferenceKey = context.getString(preferenceKeyStringRes),
    preferenceStore = preferenceStore,
    multiDeviceManager = multiDeviceManager,
    taskManager = taskManager,
) {
    override fun instantiateReflectionTask(): Task<*, TaskCodec> =
        ReflectSettingsSyncTask.ReflectKeyboardDataCollectionPolicySyncUpdate()

    fun getKeyboardDataCollectionPolicy(): KeyboardDataCollectionPolicy =
        when (get()) {
            true -> KeyboardDataCollectionPolicy.DENY_DATA_COLLECTION
            false -> KeyboardDataCollectionPolicy.ALLOW_DATA_COLLECTION
        }

    fun setFromSync(keyboardDataCollectionPolicy: KeyboardDataCollectionPolicy) {
        val value = when (keyboardDataCollectionPolicy) {
            KeyboardDataCollectionPolicy.DENY_DATA_COLLECTION -> true
            KeyboardDataCollectionPolicy.ALLOW_DATA_COLLECTION -> false
            KeyboardDataCollectionPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized keyboard data collection policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__incognito_keyboard
    }
}

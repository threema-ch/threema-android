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
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.UnknownContactPolicy

private val logger = getThreemaLogger("UnknownContactPolicySetting")

/**
 * The setting whether unknown contacts should be blocked. Stores true if unknown contacts should be blocked.
 */
class UnknownContactPolicySetting internal constructor(
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
        ReflectSettingsSyncTask.ReflectUnknownContactPolicySyncUpdate()

    fun getUnknownContactPolicy(): UnknownContactPolicy =
        when (get()) {
            true -> UnknownContactPolicy.BLOCK_UNKNOWN
            false -> UnknownContactPolicy.ALLOW_UNKNOWN
        }

    fun setFromSync(unknownContactPolicy: UnknownContactPolicy) {
        val value = when (unknownContactPolicy) {
            UnknownContactPolicy.ALLOW_UNKNOWN -> false
            UnknownContactPolicy.BLOCK_UNKNOWN -> true
            UnknownContactPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized unknown contact policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__block_unknown
    }
}

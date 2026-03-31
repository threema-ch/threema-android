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
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.GroupCallPolicy

private val logger = getThreemaLogger("GroupCallPolicySetting")

/**
 * The setting whether group calls should be enabled. Stores true if they should be enabled.
 */
class GroupCallPolicySetting internal constructor(
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
        ReflectSettingsSyncTask.ReflectGroupCallPolicySyncUpdate()

    fun getGroupCallPolicy(): GroupCallPolicy =
        when (get()) {
            true -> GroupCallPolicy.ALLOW_GROUP_CALL
            false -> GroupCallPolicy.DENY_GROUP_CALL
        }

    fun setFromSync(groupCallPolicy: GroupCallPolicy) {
        val value = when (groupCallPolicy) {
            GroupCallPolicy.ALLOW_GROUP_CALL -> true
            GroupCallPolicy.DENY_GROUP_CALL -> false
            GroupCallPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized group call policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__group_calls_enable
    }
}

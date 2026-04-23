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
import ch.threema.protobuf.d2d.sync.Settings

private val logger = getThreemaLogger("O2oCallPolicySetting")

/**
 * The setting whether 1:1 calls should be enabled. Stores true if they should be enabled.
 */
class O2oCallPolicySetting internal constructor(
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
    override fun instantiateReflectionTask(): Task<*, TaskCodec> {
        return ReflectSettingsSyncTask.ReflectO2oCallPolicySyncUpdate()
    }

    fun getO2oCallPolicy(): Settings.O2oCallPolicy =
        when (get()) {
            true -> Settings.O2oCallPolicy.ALLOW_O2O_CALL
            false -> Settings.O2oCallPolicy.DENY_O2O_CALL
        }

    fun setFromSync(o2oCallPolicy: Settings.O2oCallPolicy) {
        val value = when (o2oCallPolicy) {
            Settings.O2oCallPolicy.ALLOW_O2O_CALL -> true
            Settings.O2oCallPolicy.DENY_O2O_CALL -> false
            Settings.O2oCallPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized 1:1 call policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__voip_enable
    }
}

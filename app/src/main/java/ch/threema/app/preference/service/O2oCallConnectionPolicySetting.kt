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
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.O2oCallConnectionPolicy

private val logger = getThreemaLogger("O2oCallConnectionPolicySetting")

/**
 * The setting whether 1:1 calls should be relayed through Threema servers. Stores true if they should be relayed through Threema servers.
 */
class O2oCallConnectionPolicySetting internal constructor(
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
        ReflectSettingsSyncTask.ReflectO2oCallConnectionPolicySyncUpdate()

    fun getO2oCallConnectionPolicy(): O2oCallConnectionPolicy =
        when (get()) {
            true -> O2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION
            false -> O2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION
        }

    fun setFromSync(o2oCallConnectionPolicy: O2oCallConnectionPolicy) {
        val value = when (o2oCallConnectionPolicy) {
            O2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION -> true
            O2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION -> false
            O2oCallConnectionPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized 1:1 call connection policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__voip_force_turn
    }
}

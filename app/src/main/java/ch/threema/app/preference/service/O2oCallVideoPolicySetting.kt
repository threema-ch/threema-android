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

private val logger = getThreemaLogger("O2oCallVideoPolicySetting")

/**
 * The setting whether 1:1 calls should be allowed to use video. Stores true if video is allowed.
 */
class O2oCallVideoPolicySetting internal constructor(
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
        ReflectSettingsSyncTask.ReflectO2oCallVideoPolicySyncUpdate()

    fun getO2oCallVideoPolicy(): Settings.O2oCallVideoPolicy =
        when (get()) {
            true -> Settings.O2oCallVideoPolicy.ALLOW_VIDEO
            false -> Settings.O2oCallVideoPolicy.DENY_VIDEO
        }

    fun setFromSync(o2oCallVideoPolicy: Settings.O2oCallVideoPolicy) {
        val value = when (o2oCallVideoPolicy) {
            Settings.O2oCallVideoPolicy.ALLOW_VIDEO -> true
            Settings.O2oCallVideoPolicy.DENY_VIDEO -> false
            Settings.O2oCallVideoPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized 1:1 call video policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__voip_video_enable
    }
}

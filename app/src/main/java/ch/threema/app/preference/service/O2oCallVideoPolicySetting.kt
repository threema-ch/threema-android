package ch.threema.app.preference.service

import android.content.Context
import ch.threema.app.R
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.tasks.ReflectSettingsSyncTask
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.O2oCallVideoPolicy

private val logger = getThreemaLogger("O2oCallVideoPolicySetting")

/**
 * The setting whether 1:1 calls should be allowed to use video. Stores true if video is allowed.
 */
class O2oCallVideoPolicySetting internal constructor(
    private val preferenceService: PreferenceService,
    private val multiDeviceManager: MultiDeviceManager,
    private val nonceFactory: NonceFactory,
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
        return ReflectSettingsSyncTask.ReflectO2oCallVideoPolicySyncUpdate(
            multiDeviceManager,
            nonceFactory,
            preferenceService,
        )
    }

    fun getO2oCallVideoPolicy(): O2oCallVideoPolicy =
        when (get()) {
            true -> O2oCallVideoPolicy.ALLOW_VIDEO
            false -> O2oCallVideoPolicy.DENY_VIDEO
        }

    fun setFromSync(o2oCallVideoPolicy: O2oCallVideoPolicy) {
        val value = when (o2oCallVideoPolicy) {
            O2oCallVideoPolicy.ALLOW_VIDEO -> true
            O2oCallVideoPolicy.DENY_VIDEO -> false
            O2oCallVideoPolicy.UNRECOGNIZED -> {
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

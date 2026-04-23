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

private val logger = getThreemaLogger("ScreenshotPolicySetting")

/**
 * The setting whether screenshots should be denied. Stores true if they should be denied.
 */
class ScreenshotPolicySetting internal constructor(
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
        ReflectSettingsSyncTask.ReflectScreenshotPolicySyncUpdate()

    fun getScreenshotPolicy(): Settings.ScreenshotPolicy =
        when (get()) {
            true -> Settings.ScreenshotPolicy.DENY_SCREENSHOT
            false -> Settings.ScreenshotPolicy.ALLOW_SCREENSHOT
        }

    fun setFromSync(screenshotPolicy: Settings.ScreenshotPolicy) {
        val value = when (screenshotPolicy) {
            Settings.ScreenshotPolicy.DENY_SCREENSHOT -> true
            Settings.ScreenshotPolicy.ALLOW_SCREENSHOT -> false
            Settings.ScreenshotPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized screenshot policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__hide_screenshots
    }
}

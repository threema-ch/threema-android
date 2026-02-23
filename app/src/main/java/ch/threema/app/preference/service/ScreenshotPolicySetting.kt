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
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.ScreenshotPolicy

private val logger = getThreemaLogger("ScreenshotPolicySetting")

/**
 * The setting whether screenshots should be denied. Stores true if they should be denied.
 */
class ScreenshotPolicySetting internal constructor(
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
        return ReflectSettingsSyncTask.ReflectScreenshotPolicySyncUpdate(
            multiDeviceManager,
            nonceFactory,
            preferenceService,
        )
    }

    fun getScreenshotPolicy(): ScreenshotPolicy =
        when (get()) {
            true -> ScreenshotPolicy.DENY_SCREENSHOT
            false -> ScreenshotPolicy.ALLOW_SCREENSHOT
        }

    fun setFromSync(screenshotPolicy: ScreenshotPolicy) {
        val value = when (screenshotPolicy) {
            ScreenshotPolicy.DENY_SCREENSHOT -> true
            ScreenshotPolicy.ALLOW_SCREENSHOT -> false
            ScreenshotPolicy.UNRECOGNIZED -> {
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

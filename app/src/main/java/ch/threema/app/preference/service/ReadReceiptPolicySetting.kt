package ch.threema.app.preference.service

import android.content.Context
import ch.threema.app.R
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectReadReceiptPolicySyncUpdate
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.protobuf.d2d.sync.ReadReceiptPolicy

private val logger = getThreemaLogger("ReadReceiptPolicySetting")

/**
 * The (global) setting whether read receipts should be sent. Stores true if they should be sent.
 */
class ReadReceiptPolicySetting internal constructor(
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
        ReflectReadReceiptPolicySyncUpdate()

    fun getReadReceiptPolicy(): ReadReceiptPolicy =
        when (get()) {
            true -> ReadReceiptPolicy.SEND_READ_RECEIPT
            false -> ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
        }

    fun setFromSync(readReceiptPolicy: ReadReceiptPolicy) {
        val value = when (readReceiptPolicy) {
            ReadReceiptPolicy.SEND_READ_RECEIPT -> true
            ReadReceiptPolicy.DONT_SEND_READ_RECEIPT -> false
            ReadReceiptPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized read receipt policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__read_receipts
    }
}

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
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.ContactSyncPolicy

private val logger = getThreemaLogger("ContactSyncPolicySetting")

/**
 * The setting whether address book synchronization should be enabled. Stores true if it should be enabled.
 */
class ContactSyncPolicySetting internal constructor(
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
        ReflectSettingsSyncTask.ReflectContactSyncPolicySyncUpdate()

    fun getContactSyncPolicy(): ContactSyncPolicy =
        when (get()) {
            true -> ContactSyncPolicy.SYNC
            false -> ContactSyncPolicy.NOT_SYNCED
        }

    fun setFromSync(contactSyncPolicy: ContactSyncPolicy) {
        val value = when (contactSyncPolicy) {
            ContactSyncPolicy.SYNC -> true
            ContactSyncPolicy.NOT_SYNCED -> false
            ContactSyncPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized contact sync policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__sync_contacts
    }
}

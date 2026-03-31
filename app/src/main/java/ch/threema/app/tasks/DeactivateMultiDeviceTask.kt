package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.unlinking.DropDevicesIntent
import ch.threema.app.multidevice.unlinking.runDropDevicesSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("DeactivateMultiDeviceTask")

/**
 * This task must be run when multi device should be deactivated. It will drop all other devices, followed by this device. Afterwards, the feature
 * mask is updated to support fs and this is communicated with the contacts.
 */
class DeactivateMultiDeviceTask() : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val serviceManager: ServiceManager by inject()

    override val type = "DeactivateMultiDeviceTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!serviceManager.multiDeviceManager.isMultiDeviceActive) {
            logger.error("Multi device is already deactivated")
            return
        }

        runDropDevicesSteps(
            intent = DropDevicesIntent.Deactivate,
            serviceManager = serviceManager,
            handle = handle,
        )
    }

    @Serializable
    data object DeactivateMultiDeviceTaskData : SerializableTaskData {
        override fun createTask() = DeactivateMultiDeviceTask()
    }

    override fun serialize() = DeactivateMultiDeviceTaskData
}

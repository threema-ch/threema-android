package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.unlinking.DropDevicesIntent
import ch.threema.app.multidevice.unlinking.runDropDevicesSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("DeactivateMultiDeviceIfAloneTask")

/**
 * This task should be run periodically to check whether this is the only device in the device group. If this is the case, this task will deactivate
 * multi device.
 */
class DeactivateMultiDeviceIfAloneTask(private val serviceManager: ServiceManager) : ActiveTask<Unit>, PersistableTask {
    override val type = "DeactivateMultiDeviceIfAloneTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!serviceManager.multiDeviceManager.isMultiDeviceActive) {
            logger.error("Multi device is already deactivated")
            return
        }

        runDropDevicesSteps(
            intent = DropDevicesIntent.DeactivateIfAlone,
            serviceManager = serviceManager,
            handle = handle,
        )
    }

    @Serializable
    data object DeactivateMultiDeviceIfAloneTaskData : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager) = DeactivateMultiDeviceIfAloneTask(serviceManager)
    }

    override fun serialize() = DeactivateMultiDeviceIfAloneTaskData
}

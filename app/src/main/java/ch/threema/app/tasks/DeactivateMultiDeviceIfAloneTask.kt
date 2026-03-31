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

private val logger = getThreemaLogger("DeactivateMultiDeviceIfAloneTask")

/**
 * This task should be run periodically to check whether this is the only device in the device group. If this is the case, this task will deactivate
 * multi device.
 */
class DeactivateMultiDeviceIfAloneTask() : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val serviceManager: ServiceManager by inject()

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
        override fun createTask() = DeactivateMultiDeviceIfAloneTask()
    }

    override fun serialize() = DeactivateMultiDeviceIfAloneTaskData
}

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.unlinking.DropDeviceResult
import ch.threema.app.multidevice.unlinking.DropDevicesIntent
import ch.threema.app.multidevice.unlinking.runDropDevicesSteps
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.DropOnDisconnectTask

/**
 * Runs the drop devices steps with the given [intent].
 */
class DropDevicesStepsTask(
    private val intent: DropDevicesIntent,
    private val serviceManager: ServiceManager,
) : DropOnDisconnectTask<DropDeviceResult> {
    override val type: String = "DropDevicesStepsTask"

    override suspend fun invoke(handle: ActiveTaskCodec): DropDeviceResult = runDropDevicesSteps(
        intent = intent,
        serviceManager = serviceManager,
        handle = handle,
    )
}

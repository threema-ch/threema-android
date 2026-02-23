package ch.threema.app.systemupdates.updates

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.SyncFormerlyOrphanedGroupsTask

class SystemUpdateToVersion110(
    private val serviceManager: ServiceManager,
) : SystemUpdate {
    override fun run() {
        if (!serviceManager.multiDeviceManager.isMultiDeviceActive) {
            return
        }

        serviceManager.taskManager.schedule(
            SyncFormerlyOrphanedGroupsTask(
                serviceManager.multiDeviceManager,
                serviceManager.modelRepositories.groups,
                serviceManager.nonceFactory,
            ),
        )
    }

    override fun getVersion() = VERSION

    override fun getDescription() =
        "schedule a task that syncs user state for all groups, in particular formerly orphaned ones"

    companion object {
        const val VERSION = 110
    }
}

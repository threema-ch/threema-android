package ch.threema.app.systemupdates.updates

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.ConvertGroupProfilePictureTask
import ch.threema.data.models.GroupModel

class SystemUpdateToVersion113(private val serviceManager: ServiceManager) : SystemUpdate {

    override fun run() {
        if (!serviceManager.userService.hasIdentity()) {
            return
        }
        serviceManager.modelRepositories.groups.getAll()
            .filter(GroupModel::isCreator)
            .forEach(::scheduleGroupUpdate)
    }

    private fun scheduleGroupUpdate(groupModel: GroupModel) {
        serviceManager.taskManager.schedule(
            task = ConvertGroupProfilePictureTask.createFromServiceManager(
                groupIdentity = groupModel.groupIdentity,
                serviceManager = serviceManager,
            ),
        )
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "schedule converting group profile pictures to jpeg"

    companion object {
        const val VERSION = 113
    }
}

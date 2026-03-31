package ch.threema.app.systemupdates.updates

import ch.threema.app.services.UserService
import ch.threema.app.tasks.ConvertGroupProfilePictureTask
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.TaskManager
import org.koin.core.component.inject

class SystemUpdateToVersion113 : SystemUpdate {

    private val userService: UserService by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val taskManager: TaskManager by inject()

    override fun run() {
        if (!userService.hasIdentity()) {
            return
        }
        groupModelRepository.getAll()
            .filter(GroupModel::isCreator)
            .forEach(::scheduleGroupUpdate)
    }

    private fun scheduleGroupUpdate(groupModel: GroupModel) {
        taskManager.schedule(
            task = ConvertGroupProfilePictureTask(
                groupIdentity = groupModel.groupIdentity,
            ),
        )
    }

    override val version = 113

    override fun getDescription() = "schedule converting group profile pictures to jpeg"
}

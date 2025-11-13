/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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

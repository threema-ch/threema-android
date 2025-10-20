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

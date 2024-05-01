/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.processors.groupcontrol

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.IncomingCspMessageSubTask
import ch.threema.app.processors.ReceiveStepsResult
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupDeleteProfilePictureTask")

class IncomingGroupDeleteProfilePictureTask(
    private val deleteProfilePictureMessage: GroupDeleteProfilePictureMessage,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask(serviceManager) {
    private val fileService = serviceManager.fileService
    private val avatarCacheService = serviceManager.avatarCacheService

    override suspend fun run(handle: ActiveTaskCodec): ReceiveStepsResult {
        // 1. Run the common group receive steps
        val groupModel =
            runCommonGroupReceiveSteps(deleteProfilePictureMessage, handle, serviceManager)
        if (groupModel == null) {
            logger.warn("Discarding group delete profile picture message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        // 2. Remove the profile picture of the group
        if (fileService.hasGroupAvatarFile(groupModel)) {
            fileService.removeGroupAvatar(groupModel)

            avatarCacheService.reset(groupModel)

            ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel) }
        }

        return ReceiveStepsResult.SUCCESS
    }
}

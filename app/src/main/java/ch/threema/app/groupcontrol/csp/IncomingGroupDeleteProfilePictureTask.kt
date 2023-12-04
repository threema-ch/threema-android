/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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

package ch.threema.app.groupcontrol.csp

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.MessageProcessor.ProcessingResult
import ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.DISCARD_MESSAGE
import ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.SYNC_REQUEST_SENT
import ch.threema.app.groupcontrol.IncomingGroupControlTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupDeletePhotoMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupDeleteProfilePictureTask")

class IncomingGroupDeleteProfilePictureTask(
    private val deleteProfilePictureMessage: GroupDeletePhotoMessage,
    serviceManager: ServiceManager,
) : IncomingGroupControlTask {
    private val groupService = serviceManager.groupService
    private val fileService = serviceManager.fileService
    private val avatarCacheService = serviceManager.avatarCacheService

    override fun run(): ProcessingResult {
        return executeTask()
    }

    override suspend fun invoke(scope: CoroutineScope): ProcessingResult {
        return withContext(scope.coroutineContext) { executeTask() }
    }

    private fun executeTask(): ProcessingResult {
        // 1. Run the common group receive steps
        val stepsResult = groupService.runCommonGroupReceiveSteps(deleteProfilePictureMessage)
        if (stepsResult == DISCARD_MESSAGE || stepsResult == SYNC_REQUEST_SENT) {
            return ProcessingResult.IGNORED
        }

        // 2. Remove the profile picture of the group
        val groupModel = groupService.getByGroupMessage(deleteProfilePictureMessage)
        if (groupModel == null) {
            logger.warn("Discarding group delete profile picture message because group could not be found")
            return ProcessingResult.IGNORED
        }

        if (fileService.hasGroupAvatarFile(groupModel)) {
            fileService.removeGroupAvatar(groupModel)

            avatarCacheService.reset(groupModel)

            ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel) }
        }

        return ProcessingResult.SUCCESS
    }
}

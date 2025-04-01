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

package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupDeleteProfilePictureTask")

class IncomingGroupDeleteProfilePictureTask(
    message: GroupDeleteProfilePictureMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupDeleteProfilePictureMessage>(
    message,
    triggerSource,
    serviceManager
) {
    private val fileService by lazy { serviceManager.fileService }
    private val avatarCacheService by lazy { serviceManager.avatarCacheService }
    private val groupService by lazy { serviceManager.groupService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        // 1. Run the common group receive steps
        val groupModel =
            runCommonGroupReceiveSteps(message, handle, serviceManager)
        if (groupModel == null) {
            logger.warn("Discarding group delete profile picture message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        // 2. Remove the profile picture of the group
        if (fileService.hasGroupAvatarFile(groupModel)) {
            fileService.removeGroupAvatar(groupModel)

            avatarCacheService.reset(groupModel)

            ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel) }

            ShortcutUtil.updateShareTargetShortcut(groupService.createReceiver(groupModel))
        }

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        // TODO(ANDR-2741): Support group synchronization
        return ReceiveStepsResult.DISCARD
    }
}

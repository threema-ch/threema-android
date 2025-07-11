/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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
import ch.threema.app.tasks.ReflectGroupSyncUpdateImmediateTask
import ch.threema.app.tasks.ReflectionResult
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
    serviceManager,
) {
    private val fileService by lazy { serviceManager.fileService }
    private val groupService by lazy { serviceManager.groupService }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val nonceFactory by lazy { serviceManager.nonceFactory }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        // Run the common group receive steps
        val groupModel = runCommonGroupReceiveSteps(message, handle, serviceManager)
        if (groupModel == null) {
            logger.warn("Discarding group delete profile picture message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        // If the group does not have a profile picture, discard this message
        if (!fileService.hasGroupAvatarFile(groupModel)) {
            logger.info("Discarding this message as group has no profile picture")
            return ReceiveStepsResult.DISCARD
        }

        if (multiDeviceManager.isMultiDeviceActive) {
            val reflectionResult =
                ReflectGroupSyncUpdateImmediateTask.ReflectGroupDeleteProfilePicture(
                    groupModel,
                    fileService,
                    nonceFactory,
                    multiDeviceManager,
                ).reflect(handle)

            when (reflectionResult) {
                is ReflectionResult.Success -> logger.info("Reflected removed group profile picture")
                is ReflectionResult.Failed -> {
                    logger.error(
                        "Could not reflect removed group profile picture",
                        reflectionResult.exception,
                    )
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.PreconditionFailed -> {
                    logger.error(
                        "Group sync race occurred: Profile picture could not be removed",
                        reflectionResult.transactionException,
                    )
                }

                is ReflectionResult.MultiDeviceNotActive -> {
                    // Note that this is an edge case that should never happen as deactivating md and processing incoming messages is both running in
                    // tasks. However, if it happens nevertheless, we can simply log a warning and continue processing the message.
                    logger.warn("Reflection failed because multi device is not active")
                }
            }
        }

        fileService.removeGroupAvatar(groupModel)

        ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel.groupIdentity) }

        ShortcutUtil.updateShareTargetShortcut(groupService.createReceiver(groupModel))

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.info("Discarding group delete profile picture from sync")
        return ReceiveStepsResult.DISCARD
    }
}

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

package ch.threema.app.processors.incomingcspmessage.contactcontrol

import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.ReflectContactSyncUpdateImmediateTask.ReflectContactProfilePicture
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = LoggingUtil.getThreemaLogger("IncomingDeleteProfilePictureTask")

class IncomingDeleteProfilePictureTask(
    message: DeleteProfilePictureMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<DeleteProfilePictureMessage>(message, triggerSource, serviceManager) {
    private val fileService by lazy { serviceManager.fileService }
    private val contactService by lazy { serviceManager.contactService }
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }

    private val identity = message.fromIdentity

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        val contactModel = contactModelRepository.getByIdentity(identity) ?: run {
            logger.warn("Delete profile picture message received from unknown contact")
            return ReceiveStepsResult.DISCARD
        }

        reflectProfilePictureRemoved(handle)

        fileService.removeContactDefinedProfilePicture(identity)
        ListenerManager.contactListeners.handle { listener: ContactListener ->
            listener.onAvatarChanged(identity)
        }

        ShortcutUtil.updateShareTargetShortcut(contactService.createReceiver(contactModel))

        contactModel.setIsRestored(false)

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        val contactModel = contactModelRepository.getByIdentity(identity) ?: run {
            logger.error("Reflected delete profile picture message received from unknown contact")
            return ReceiveStepsResult.DISCARD
        }
        contactModel.setIsRestored(false)
        return ReceiveStepsResult.SUCCESS
    }

    private suspend fun reflectProfilePictureRemoved(handle: ActiveTaskCodec) {
        if (multiDeviceManager.isMultiDeviceActive) {
            ReflectContactProfilePicture(
                contactIdentity = identity,
                profilePictureUpdate = ReflectContactProfilePicture.RemovedProfilePicture,
                contactModelRepository = contactModelRepository,
                multiDeviceManager = multiDeviceManager,
                nonceFactory = nonceFactory,
            ).reflect(handle)
        }
    }
}

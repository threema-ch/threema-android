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

package ch.threema.app.processors.incomingcspmessage.contactcontrol

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = LoggingUtil.getThreemaLogger("IncomingContactRequestProfilePictureTask")

class IncomingContactRequestProfilePictureTask(
    message: ContactRequestProfilePictureMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<ContactRequestProfilePictureMessage>(
    message,
    triggerSource,
    serviceManager,
) {
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        return processIncomingContactRequestProfilePictureMessage()
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        return processIncomingContactRequestProfilePictureMessage()
    }

    private fun processIncomingContactRequestProfilePictureMessage(): ReceiveStepsResult {
        val contactModel = contactModelRepository.getByIdentity(message.fromIdentity)
        if (contactModel == null) {
            logger.warn("Received incoming contact request profile picture message from unknown contact")
            return ReceiveStepsResult.DISCARD
        }

        contactModel.setProfilePictureBlobId(null)

        return ReceiveStepsResult.SUCCESS
    }
}

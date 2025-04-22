/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OutgoingContactRequestProfilePictureTask")

/**
 * Sends a request-profile-picture message to the given contact. Note that it is only sent, if the
 * contact has been restored. After sending the profile picture request, the restored flag is
 * cleared from the contact.
 */
class OutgoingContactRequestProfilePictureTask(
    private val toIdentity: String,
    serviceManager: ServiceManager,
) : OutgoingProfilePictureTask(serviceManager) {
    override val type = "OutgoingContactRequestProfilePictureTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        // Get contact and check that sending a profile picture request is necessary
        val contactModel = contactModelRepository.getByIdentity(toIdentity)
        if (contactModel == null) {
            logger.warn(
                "Contact {} is unknown, even though a profile picture request should be sent",
                toIdentity,
            )
            return
        }

        val contactModelData = contactModel.data.value
        if (contactModelData == null) {
            logger.warn(
                "Contact model data for identity {} is null, even though a profile picture request should be sent",
                toIdentity,
            )
            return
        }

        if (!contactModelData.isRestored) {
            logger.warn(
                "Contact {} is not restored; sending profile picture request is skipped",
                toIdentity,
            )
            return
        }

        // Send the profile picture request message
        sendRequestProfilePictureMessage(toIdentity, handle)

        contactModel.setIsRestored(false)
    }

    override fun serialize() = OutgoingContactRequestProfilePictureData(toIdentity)

    @Serializable
    data class OutgoingContactRequestProfilePictureData(
        private val toIdentity: String,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingContactRequestProfilePictureTask(toIdentity, serviceManager)
    }
}

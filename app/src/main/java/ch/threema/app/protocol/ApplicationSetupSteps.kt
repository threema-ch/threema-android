/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.protocol

import androidx.annotation.WorkerThread
import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.FSRefreshStepsTask
import ch.threema.app.tasks.OutgoingContactRequestProfilePictureTask
import ch.threema.app.workers.ContactUpdateWorker
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.IdentityState

private val logger = LoggingUtil.getThreemaLogger("ApplicationSetupSteps")

/**
 * Run the _Application Setup Steps_ as defined in the protocol.
 */
@WorkerThread
fun runApplicationSetupSteps(serviceManager: ServiceManager): Boolean {
    logger.info("Running application setup steps")

    val groupService = serviceManager.groupService

    // Send the feature mask to the server and update the contacts. It is important that the feature
    // masks of the contacts are updated to check whether the contacts support FS or not.
    if (!ContactUpdateWorker.sendFeatureMaskAndUpdateContacts(serviceManager)) {
        logger.warn("Aborting application setup steps as identity state update did not work")
        return false
    }

    // Get all groups where the user is a member (or creator). Only include groups that are not
    // deleted.
    val groups = groupService.all.filter { !it.isDeleted && groupService.isGroupMember(it) }

    // Find group contacts of groups that are not left
    val groupContacts = groups.flatMap { groupService.getMembers(it) }.toSet()

    // Find valid contacts with defined last-update flag
    val contactsWithConversation = serviceManager.contactService.all
        .filter { it.lastUpdate != null }
        .toSet()

    val myIdentity = serviceManager.contactService.me.identity

    // Determine the solicited contacts defined by group contacts and conversation contacts and
    // remove invalid contacts
    val solicitedContacts = (groupContacts + contactsWithConversation)
        .filter { it.state != IdentityState.INVALID }
        .filter { it.identity != myIdentity }
        .toSet()

    // If forward security is supported, run the FS Refresh Steps
    // TODO(ANDR-2519): Remove the check when md allows fs (but keep running the FS Refresh Steps)
    if (serviceManager.multiDeviceManager.isMdDisabledOrSupportsFs) {
        serviceManager.taskManager.schedule(FSRefreshStepsTask(solicitedContacts, serviceManager))
    }

    // Send a contact-request-profile-picture message to each solicited contact
    solicitedContacts.forEach {
        serviceManager.taskManager.schedule(
            OutgoingContactRequestProfilePictureTask(
                it.identity,
                serviceManager
            )
        )
    }

    // Send a group sync or group sync request for groups where the user is the creator or a member
    groups.forEach { group ->
        if (groupService.isGroupCreator(group)) {
            groupService.scheduleSync(group)
        } else if (groupService.isGroupMember(group)) {
            groupService.scheduleSyncRequest(group.creatorIdentity, group.apiGroupId)
        }
    }

    logger.info("Application setup steps completed successfully")

    return true
}

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

package ch.threema.app.groupflows

import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.services.FileService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.ActiveGroupStateResyncTask
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.DatabaseService
import kotlinx.coroutines.runBlocking

private val logger = getThreemaLogger("GroupResyncFlow")

class GroupResyncFlow(
    private val groupModel: GroupModel,
    private val taskManager: TaskManager,
    private val contactModelRepository: ContactModelRepository,
    private val contactStore: ContactStore,
    private val apiConnector: APIConnector,
    private val userService: UserService,
    private val groupProfilePictureUploader: GroupProfilePictureUploader,
    private val fileService: FileService,
    private val groupCallManager: GroupCallManager,
    private val databaseService: DatabaseService,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
) : BackgroundTask<GroupFlowResult> {
    override fun runInBackground(): GroupFlowResult {
        if (!userService.identity.equals(groupModel.groupIdentity.creatorIdentity)) {
            logger.error("Cannot resync group: the user is not the creator")
            return GroupFlowResult.Failure.Other
        }

        if (groupModel.data?.isMember != true) {
            logger.error("Cannot resync group: the group is deleted or disbanded")
            return GroupFlowResult.Failure.Other
        }

        val taskSucceeded = runBlocking {
            taskManager.schedule(
                ActiveGroupStateResyncTask(
                    groupModel,
                    contactModelRepository,
                    contactStore,
                    apiConnector,
                    userService,
                    groupProfilePictureUploader,
                    fileService,
                    groupCallManager,
                    databaseService,
                    outgoingCspMessageServices,
                ),
            ).await()
        }
        return when (taskSucceeded) {
            true -> GroupFlowResult.Success(groupModel)
            false -> GroupFlowResult.Failure.Other
        }
    }
}

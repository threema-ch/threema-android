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

import ch.threema.app.protocol.PreGeneratedMessageIds
import ch.threema.app.protocol.runActiveGroupStateResyncSteps
import ch.threema.app.services.ApiService
import ch.threema.app.services.FileService
import ch.threema.app.services.UserService
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.toBasicContacts
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.storage.DatabaseService

private val logger = LoggingUtil.getThreemaLogger("ActiveGroupStateResyncTask")

class ActiveGroupStateResyncTask(
    private val groupModel: GroupModel,
    private val contactModelRepository: ContactModelRepository,
    private val contactStore: ContactStore,
    private val apiConnector: APIConnector,
    private val userService: UserService,
    private val apiService: ApiService,
    private val fileService: FileService,
    private val groupCallManager: GroupCallManager,
    private val databaseService: DatabaseService,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
) : ActiveTask<Boolean> {
    override val type = "ActiveGroupStateResyncTask"

    override suspend fun invoke(handle: ActiveTaskCodec): Boolean {
        val multiDeviceManager = outgoingCspMessageServices.multiDeviceManager

        return if (multiDeviceManager.isMultiDeviceActive) {
            val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

            handle.createTransaction(
                multiDeviceProperties.keys,
                MdD2D.TransactionScope.Scope.GROUP_SYNC,
                TRANSACTION_TTL_MAX,
            ) {
                getGroupModelData()?.isMember == true
            }.execute {
                runActiveGroupStateResyncSteps(handle)
            }
        } else {
            runActiveGroupStateResyncSteps(handle)
        }
    }

    private fun getGroupModelData(): GroupModelData? {
        val groupModelData = groupModel.data.value ?: run {
            logger.warn("Group model data is null: cannot resync group")
            null
        }

        return groupModelData
    }

    private suspend fun runActiveGroupStateResyncSteps(handle: ActiveTaskCodec): Boolean {
        val groupModelData = getGroupModelData() ?: return false

        runActiveGroupStateResyncSteps(
            groupModel,
            groupModelData.otherMembers.toBasicContacts(
                contactModelRepository,
                contactStore,
                apiConnector,
            ).toSet(),
            PreGeneratedMessageIds(
                firstMessageId = MessageId.random(),
                secondMessageId = MessageId.random(),
                thirdMessageId = MessageId.random(),
                fourthMessageId = MessageId.random(),
            ),
            userService,
            apiService,
            fileService,
            groupCallManager,
            databaseService,
            outgoingCspMessageServices,
            handle,
        )

        return true
    }
}

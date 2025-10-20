/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.systemupdates.updates.SystemUpdateToVersion110
import ch.threema.base.crypto.NonceFactory
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.awaitReflectAck
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedGroupSyncUpdate
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group
import ch.threema.protobuf.d2d.sync.group
import kotlinx.serialization.Serializable

/**
 * This task is needed for [SystemUpdateToVersion110], to ensure that the state changes made by the system update
 * are reflected if needed. It should not be used for any other purposes.
 */
class SyncFormerlyOrphanedGroupsTask(
    private val multiDeviceManager: MultiDeviceManager,
    private val groupModelRepository: GroupModelRepository,
    private val nonceFactory: NonceFactory,
) : ActiveTask<Unit>, PersistableTask {
    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            return
        }

        val groups = groupModelRepository.getAll()
            .filter { groupModel -> !groupModel.isCreator() && groupModel.isKicked() }
        if (groups.isEmpty()) {
            return
        }

        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()
        handle.createTransaction(
            multiDeviceProperties.keys,
            MdD2D.TransactionScope.Scope.GROUP_SYNC,
            TRANSACTION_TTL_MAX,
        )
            .execute {
                groups.map { groupModel ->
                    val encryptedEnvelopeResult = getEncryptedGroupSyncUpdate(
                        group = group {
                            groupIdentity = groupModel.groupIdentity.toProtobuf()
                            userState = Group.UserState.KICKED
                        },
                        memberStateChanges = emptyMap(),
                        multiDeviceProperties = multiDeviceProperties,
                    )

                    handle.reflect(encryptedEnvelopeResult)
                }
                    .forEach { reflectId ->
                        handle.awaitReflectAck(reflectId)
                    }
            }
    }

    override val type = "SyncFormerlyOrphanedGroupsTask"

    override fun serialize(): SerializableTaskData = SyncFormerlyOrphanedGroupsTaskData

    @Serializable
    data object SyncFormerlyOrphanedGroupsTaskData : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            SyncFormerlyOrphanedGroupsTask(
                serviceManager.multiDeviceManager,
                serviceManager.modelRepositories.groups,
                serviceManager.nonceFactory,
            )
    }
}

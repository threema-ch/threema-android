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

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.protocol.ProfilePictureChange
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.getEncryptedGroupSyncCreate
import ch.threema.protobuf.d2d.sync.MdD2DSync

private val logger = LoggingUtil.getThreemaLogger("ReflectGroupSyncCreate")

class ReflectGroupSyncCreateTask(
    private val groupModelData: GroupModelData,
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val nonceFactory: NonceFactory,
    private val uploadGroupPhoto: () -> ProfilePictureChange?,
    private val finishGroupCreation: (ProfilePictureChange?) -> GroupModel?,
    multiDeviceManager: MultiDeviceManager,
) : ReflectGroupSyncTask<ProfilePictureChange?, GroupModel?>(multiDeviceManager),
    ActiveTask<ReflectionResult<GroupModel?>> {
    override val type = "ReflectGroupSyncCreate"

    override suspend fun invoke(handle: ActiveTaskCodec): ReflectionResult<GroupModel?> =
        runTransaction(handle)

    override val runPrecondition: () -> Boolean = {
        // Precondition: Group must not exist and members must exist as contacts
        groupModelData.notExists() && groupModelData.otherMembers.existAsContacts()
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> ProfilePictureChange? =
        { handle ->
            val profilePictureChange = uploadGroupPhoto()

            logger.info("Reflecting group sync create for group {}", groupModelData.name)

            val encryptedEnvelopeResult = getEncryptedGroupSyncCreate(
                groupModelData.toGroupSync(
                    isPrivateChat = false,
                    conversationVisibility = MdD2DSync.ConversationVisibility.NORMAL,
                    profilePictureChange = profilePictureChange,
                ),
                multiDeviceManager.propertiesProvider.get(),
            )

            handle.reflectAndAwaitAck(
                encryptedEnvelopeResult,
                true,
                nonceFactory,
            )

            profilePictureChange
        }

    override val runAfterSuccessfulTransaction: (transactionResult: ProfilePictureChange?) -> GroupModel? =
        { result ->
            finishGroupCreation(result)
        }

    private fun GroupModelData.notExists() =
        groupModelRepository.getByGroupIdentity(groupIdentity) == null

    private fun Iterable<String>.existAsContacts() =
        this.map { contactModelRepository.getByIdentity(it) }.all { it != null }
}

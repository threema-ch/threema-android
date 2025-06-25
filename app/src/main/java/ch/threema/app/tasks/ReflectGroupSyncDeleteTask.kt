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
import ch.threema.base.crypto.NonceFactory
import ch.threema.data.models.GroupModel
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.getEncryptedGroupSyncDelete
import ch.threema.storage.models.GroupModel.UserState

enum class ReflectGroupSyncDeletePrecondition {
    USER_IS_NO_MEMBER,
    USER_IS_MEMBER,
}

/**
 * Reflects a group delete message.
 */
class ReflectGroupSyncDeleteTask(
    private val groupModel: GroupModel,
    private val precondition: ReflectGroupSyncDeletePrecondition,
    private val nonceFactory: NonceFactory,
    multiDeviceManager: MultiDeviceManager,
) : ReflectGroupSyncTask<Unit, Unit>(multiDeviceManager), ActiveTask<ReflectionResult<Unit>> {
    override val type = "ReflectGroupSyncDeleteTask"

    override val runPrecondition: () -> Boolean = {
        when (precondition) {
            ReflectGroupSyncDeletePrecondition.USER_IS_NO_MEMBER ->
                groupModel.data.value?.userState != UserState.MEMBER

            ReflectGroupSyncDeletePrecondition.USER_IS_MEMBER ->
                groupModel.data.value?.userState == UserState.MEMBER
        }
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        val encryptedEnvelopeResult = getEncryptedGroupSyncDelete(
            groupModel.getProtoGroupIdentity(),
            multiDeviceManager.propertiesProvider.get(),
        )

        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult,
            true,
            nonceFactory,
        )
    }

    override val runAfterSuccessfulTransaction: (transactionResult: Unit) -> Unit = { }

    override suspend fun invoke(handle: ActiveTaskCodec): ReflectionResult<Unit> {
        if (!multiDeviceManager.isMultiDeviceActive) {
            return ReflectionResult.MultiDeviceNotActive()
        }

        return runTransaction(handle)
    }
}

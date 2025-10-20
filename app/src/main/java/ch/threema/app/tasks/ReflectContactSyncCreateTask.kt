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
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.getEncryptedContactSyncCreate

private val logger = LoggingUtil.getThreemaLogger("ReflectContactSyncCreateTask")

class ReflectContactSyncCreateTask(
    private val contactModelData: ContactModelData,
    private val contactModelRepository: ContactModelRepository,
    private val nonceFactory: NonceFactory,
    private val createLocally: () -> ContactModel,
    multiDeviceManager: MultiDeviceManager,
) : ReflectContactSyncTask<Unit, ContactModel>(multiDeviceManager), ActiveTask<ContactModel> {
    override val type = "ReflectContactSyncCreate"

    override suspend fun invoke(handle: ActiveTaskCodec): ContactModel = reflectContactSync(handle)

    override val runPrecondition: () -> Boolean = {
        // Precondition: Contact must not exist
        contactModelRepository.getByIdentity(contactModelData.identity) == null
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        logger.info("Reflecting contact sync create for identity {}", contactModelData.identity)

        val encryptedEnvelopeResult = getEncryptedContactSyncCreate(
            contactModelData.toFullSyncContact(),
            mdProperties,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    override val runAfterSuccessfulTransaction: (transactionResult: Unit) -> ContactModel = {
        createLocally()
    }
}

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

package ch.threema.app.tasks

import ch.threema.app.services.ContactService
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.storage.models.ContactModel

private val logger = LoggingUtil.getThreemaLogger("ForwardSecurityStateLogTask")

/**
 * This task just logs the forward security state. This needs to be a task, because accessing the
 * forward security state may require to send a terminate.
 */
class ForwardSecurityStateLogTask(
    private val contactService: ContactService,
    private val contactModel: ContactModel,
) : ActiveTask<Unit> {
    override val type: String = "ForwardSecurityStateLogTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val state = contactService.getForwardSecurityState(contactModel, handle)

        logger.info("DH session state with contact {}: {}", contactModel.identity, state)
    }
}

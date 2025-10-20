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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import ch.threema.protobuf.csp.e2e.fs.Terminate.Cause
import kotlinx.serialization.Serializable

class DeleteAndTerminateFSSessionsTask(
    private val fsmp: ForwardSecurityMessageProcessor,
    private val contact: Contact,
    private val cause: Cause,
) : ActiveTask<Unit>, PersistableTask {
    override val type: String = "DeleteAndTerminateFSSessionsTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        fsmp.clearAndTerminateAllSessions(contact, cause, handle)
    }

    override fun serialize(): SerializableTaskData =
        DeleteAndTerminateFSSessionsTaskData(contact.identity, cause)

    @Serializable
    class DeleteAndTerminateFSSessionsTaskData(
        private val identity: Identity,
        private val cause: Cause,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
            val contact = serviceManager.contactStore.getContactForIdentityIncludingCache(identity)
                ?: throw IllegalStateException("Cannot re-create fs termination task for identity where no contact is known")

            return DeleteAndTerminateFSSessionsTask(
                serviceManager.forwardSecurityMessageProcessor,
                contact,
                cause,
            )
        }
    }
}

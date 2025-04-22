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

import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ServiceManager
import ch.threema.app.workers.ContactUpdateWorker
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import kotlinx.serialization.Serializable

/**
 * This task runs the _Application Update Steps_ as defined in the protocol.
 */
class ApplicationUpdateStepsTask(serviceManager: ServiceManager) :
    ActiveTask<Unit>,
    PersistableTask {
    private val contactService by lazy { serviceManager.contactService }
    private val forwardSecurityMessageProcessor by lazy { serviceManager.forwardSecurityMessageProcessor }

    override val type = "ApplicationUpdateStepsTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        ContactUpdateWorker.performOneTimeSync(ThreemaApplication.getAppContext())

        // Remove all sessions with contacts where the version is not known
        contactService.all.forEach {
            forwardSecurityMessageProcessor.terminateAllInvalidSessions(it, handle)
        }
    }

    override fun serialize() = ApplicationUpdateStepsData

    @Serializable
    object ApplicationUpdateStepsData : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager) =
            ApplicationUpdateStepsTask(serviceManager)
    }
}

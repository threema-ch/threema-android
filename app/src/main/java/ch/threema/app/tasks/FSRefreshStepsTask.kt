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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.storage.models.ContactModel
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("FSRefreshStepsTask")

class FSRefreshStepsTask(
    private val contacts: Set<ContactModel>,
    serviceManager: ServiceManager,
) : ActiveTask<Unit>, PersistableTask {
    private val forwardSecurityMessageProcessor by lazy { serviceManager.forwardSecurityMessageProcessor }

    override val type = "FSRefreshStepsTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        logger.info("Running fs refresh steps")

        contacts.filter { contact ->
            ThreemaFeature.canForwardSecurity(contact.featureMask).also { hasFsSupport ->
                if (!hasFsSupport) {
                    logger.info("Skipping contact {} due to missing fs support", contact.identity)
                }
            }
        }.forEach {
            logger.info("Refreshing fs session with contact {}", it.identity)
            forwardSecurityMessageProcessor.runFsRefreshSteps(it, handle)
        }
    }

    override fun serialize() = FSRefreshStepsTaskData(contacts.map { it.identity })

    @Serializable
    class FSRefreshStepsTaskData(
        private val contactIdentities: Collection<String>,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
            val contactService = serviceManager.contactService
            val contacts = contactIdentities.mapNotNull { contactService.getByIdentity(it) }.toSet()
            return FSRefreshStepsTask(contacts, serviceManager)
        }
    }
}

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
import ch.threema.app.services.ContactService
import ch.threema.app.services.MessageService
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.ContactModel
import ch.threema.domain.fs.DHSession
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.stores.DHSessionStoreException
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.protobuf.csp.e2e.fs.Terminate
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OnFSFeatureMaskDowngradedTask")

/**
 * Performs the required steps if a contact does not support forward security anymore due to a
 * change of its feature mask. This includes creating a status message in the conversation with that
 * contact to warn the user that forward security has been disabled for this contact. This task also
 * terminates all existing sessions with the contact by invoking [DeleteAndTerminateFSSessionsTask].
 * <p>
 * Note that the status message is only created if a forward security session currently exists.
 * <p>
 * Note that this task must only be scheduled if the feature mask of a contact is changed from a
 * feature mask that indicates forward security support to a feature mask without forward
 * security support.
 *
 * @param contactModel the affected contact
 */
class OnFSFeatureMaskDowngradedTask(
    private val contactModel: ContactModel,
    private val contactService: ContactService,
    private val messageService: MessageService,
    private val dhSessionStore: DHSessionStoreInterface,
    private val identityStore: IdentityStoreInterface,
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
) : ActiveTask<Unit>, PersistableTask {

    override val type = "FSFeatureMaskDowngraded"

    private val contactModelData by lazy { contactModel.data.value }

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val data = contactModelData ?: return

        if (ThreemaFeature.canForwardSecurity(data.featureMaskLong())) {
            logger.warn("Forward security is supported by this contact")
            return
        }

        if (hasForwardSecuritySession(handle)) {
            terminateAllSessions(handle)
            createForwardSecurityDowngradedStatus()
        }
    }

    private fun hasForwardSecuritySession(handle: ActiveTaskCodec): Boolean {
        // Get the best forward security session with that contact.
        var fsSession: DHSession? = null
        try {
            fsSession = dhSessionStore.getBestDHSession(
                identityStore.identity,
                contactModel.identity,
                handle,
            )
        } catch (exception: DHSessionStoreException) {
            logger.error("Unable to determine best DH session", exception)
        } catch (exception: NullPointerException) {
            logger.error("Unable to determine best DH session", exception)
        }

        return fsSession != null
    }

    private suspend fun terminateAllSessions(handle: ActiveTaskCodec) {
        val data = contactModelData ?: return

        logger.info(
            "Forward security feature has been downgraded for contact {}",
            contactModel.identity
        )

        // Clear and terminate all sessions with that contact
        DeleteAndTerminateFSSessionsTask(
            forwardSecurityMessageProcessor,
            Contact(data.identity, data.publicKey, data.verificationLevel),
            Terminate.Cause.DISABLED_BY_REMOTE
        ).invoke(handle)
    }

    private fun createForwardSecurityDowngradedStatus() {
        val receiver = contactService.createReceiver(contactModel) ?: run {
            logger.error("Contact message receiver is null")
            return
        }
        messageService.createForwardSecurityStatus(
            receiver,
            ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE,
            0,
            null
        )
    }

    override fun serialize(): SerializableTaskData =
        OnFSFeatureMaskDowngradedData(contactModel.identity)

    @Serializable
    class OnFSFeatureMaskDowngradedData(
        private val identity: String,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
            val contactModel = serviceManager.modelRepositories.contacts.getByIdentity(identity)
            if (contactModel == null) {
                logger.warn("Contact with identity {} does not exist anymore", identity)
                throw IllegalStateException("Can not create task for deleted contact")
            }
            return OnFSFeatureMaskDowngradedTask(
                contactModel,
                serviceManager.contactService,
                serviceManager.messageService,
                serviceManager.dhSessionStore,
                serviceManager.identityStore,
                serviceManager.forwardSecurityMessageProcessor,
            )
        }
    }
}

package ch.threema.app.tasks

import ch.threema.app.services.ContactService
import ch.threema.app.services.MessageService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.fs.DHSession
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.stores.DHSessionStoreException
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.csp.e2e.fs.Terminate
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("OnFSFeatureMaskDowngradedTask")

/**
 * Performs the required steps if a contact does not support forward security anymore due to a
 * change of its feature mask. This includes creating a status message in the conversation with that
 * contact to warn the user that forward security has been disabled for this contact. This task also
 * terminates all existing sessions with the contact by invoking [DeleteAndTerminateFSSessionsTask].
 *
 * Note that the status message is only created if a forward security session currently exists.
 *
 * Note that this task must only be scheduled if the feature mask of a contact is changed from a
 * feature mask that indicates forward security support to a feature mask without forward
 * security support.
 *
 * @param identity the identity of the affected contact
 */
class OnFSFeatureMaskDowngradedTask(
    private val identity: IdentityString,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val contactService: ContactService by inject()
    private val contactModelRepository: ContactModelRepository by inject()
    private val messageService: MessageService by inject()
    private val dhSessionStore: DHSessionStoreInterface by inject()
    private val identityStore: IdentityStore by inject()

    override val type = "FSFeatureMaskDowngraded"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val contactModel = contactModelRepository.getByIdentity(identity) ?: return
        val contactModelData = contactModel.data ?: return

        if (ThreemaFeature.canForwardSecurity(contactModelData.featureMaskLong())) {
            logger.warn("Forward security is supported by this contact")
            return
        }

        if (hasForwardSecuritySession(handle)) {
            terminateAllSessions(contactModelData, handle)
            createForwardSecurityDowngradedStatus(contactModel)
        }
    }

    private fun hasForwardSecuritySession(handle: ActiveTaskCodec): Boolean {
        // Get the best forward security session with that contact.
        var fsSession: DHSession? = null
        try {
            fsSession = dhSessionStore.getBestDHSession(
                /* myIdentity = */
                identityStore.getIdentityString(),
                /* peerIdentity = */
                identity,
                /* handle = */
                handle,
            )
        } catch (exception: DHSessionStoreException) {
            logger.error("Unable to determine best DH session", exception)
        } catch (exception: NullPointerException) {
            logger.error("Unable to determine best DH session", exception)
        }

        return fsSession != null
    }

    private suspend fun terminateAllSessions(contactModelData: ContactModelData, handle: ActiveTaskCodec) {
        logger.info(
            "Forward security feature has been downgraded for contact {}",
            contactModelData.identity,
        )

        // Clear and terminate all sessions with that contact
        DeleteAndTerminateFSSessionsTask(
            identity = identity,
            cause = Terminate.Cause.DISABLED_BY_REMOTE,
        ).invoke(handle)
    }

    private fun createForwardSecurityDowngradedStatus(contactModel: ContactModel) {
        val receiver = contactService.createReceiver(contactModel) ?: run {
            logger.error("Contact message receiver is null")
            return
        }
        messageService.createForwardSecurityStatus(
            receiver,
            ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE,
            0,
            null,
        )
    }

    override fun serialize(): SerializableTaskData =
        OnFSFeatureMaskDowngradedData(identity = identity)

    @Serializable
    class OnFSFeatureMaskDowngradedData(
        private val identity: IdentityString,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OnFSFeatureMaskDowngradedTask(
                identity = identity,
            )
    }
}

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

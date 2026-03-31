package ch.threema.app.tasks

import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.csp.e2e.fs.Terminate.Cause
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DeleteAndTerminateFSSessionsTask(
    private val identity: IdentityString,
    private val cause: Cause,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val contactStore: ContactStore by inject()
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor by inject()

    override val type: String = "DeleteAndTerminateFSSessionsTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val contact = contactStore.getContactForIdentityIncludingCache(identity)
            ?: throw IllegalStateException("Cannot delete and terminate fs sessions task for identity where no contact is known")

        forwardSecurityMessageProcessor.clearAndTerminateAllSessions(contact, cause, handle)
    }

    override fun serialize(): SerializableTaskData =
        DeleteAndTerminateFSSessionsTaskData(
            identity = identity,
            cause = cause,
        )

    @Serializable
    class DeleteAndTerminateFSSessionsTaskData(
        private val identity: IdentityString,
        private val cause: Cause,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            DeleteAndTerminateFSSessionsTask(
                identity = identity,
                cause = cause,
            )
    }
}

package ch.threema.app.tasks

import ch.threema.app.ThreemaApplication
import ch.threema.app.services.ContactService
import ch.threema.app.workers.ContactUpdateWorker
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * This task runs the _Application Update Steps_ as defined in the protocol.
 */
class ApplicationUpdateStepsTask() :
    ActiveTask<Unit>,
    PersistableTask,
    KoinComponent {
    private val contactService: ContactService by inject()
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor by inject()

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
        override fun createTask() = ApplicationUpdateStepsTask()
    }
}

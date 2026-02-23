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

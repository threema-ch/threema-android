package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("FSRefreshStepsTask")

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
        private val contactIdentities: Collection<Identity>,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
            val contactService = serviceManager.contactService
            val contacts = contactIdentities.mapNotNull { contactService.getByIdentity(it) }.toSet()
            return FSRefreshStepsTask(contacts, serviceManager)
        }
    }
}

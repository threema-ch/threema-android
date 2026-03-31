package ch.threema.app.tasks

import ch.threema.app.services.ContactService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("FSRefreshStepsTask")

class FSRefreshStepsTask(
    private val contactIdentities: Set<IdentityString>,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val contactService: ContactService by inject()
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor by inject()

    override val type = "FSRefreshStepsTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        logger.info("Running fs refresh steps")

        contactIdentities
            .mapNotNull(contactService::getByIdentity)
            .filter { contactModel ->
                ThreemaFeature.canForwardSecurity(contactModel.featureMask).also { hasFsSupport ->
                    if (!hasFsSupport) {
                        logger.info("Skipping contact {} due to missing fs support", contactModel.identity)
                    }
                }
            }.forEach {
                logger.info("Refreshing fs session with contact {}", it.identity)
                forwardSecurityMessageProcessor.runFsRefreshSteps(it, handle)
            }
    }

    override fun serialize() = FSRefreshStepsTaskData(
        contactIdentities = contactIdentities,
    )

    @Serializable
    class FSRefreshStepsTaskData(
        private val contactIdentities: Collection<IdentityString>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> = FSRefreshStepsTask(
            contactIdentities = contactIdentities.toSet(),
        )
    }
}

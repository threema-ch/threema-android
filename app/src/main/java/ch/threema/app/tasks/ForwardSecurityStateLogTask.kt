package ch.threema.app.tasks

import ch.threema.app.services.ContactService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.storage.models.ContactModel

private val logger = getThreemaLogger("ForwardSecurityStateLogTask")

/**
 * This task just logs the forward security state. This needs to be a task, because accessing the
 * forward security state may require to send a terminate.
 */
class ForwardSecurityStateLogTask(
    private val contactService: ContactService,
    private val contactModel: ContactModel,
) : ActiveTask<Unit> {
    override val type: String = "ForwardSecurityStateLogTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val state = contactService.getForwardSecurityState(contactModel, handle)

        logger.info("DH session state with contact {}: {}", contactModel.identity, state)
    }
}

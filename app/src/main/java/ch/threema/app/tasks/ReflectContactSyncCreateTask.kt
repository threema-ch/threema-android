package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.getEncryptedContactSyncCreate

private val logger = getThreemaLogger("ReflectContactSyncCreateTask")

class ReflectContactSyncCreateTask(
    private val contactModelData: ContactModelData,
    private val contactModelRepository: ContactModelRepository,
    private val nonceFactory: NonceFactory,
    private val createLocally: () -> ContactModel,
    multiDeviceManager: MultiDeviceManager,
) : ReflectContactSyncTask<Unit, ContactModel>(multiDeviceManager), ActiveTask<ContactModel> {
    override val type = "ReflectContactSyncCreate"

    override suspend fun invoke(handle: ActiveTaskCodec): ContactModel = reflectContactSync(handle)

    override val runPrecondition: () -> Boolean = {
        // Precondition: Contact must not exist
        contactModelRepository.getByIdentity(contactModelData.identity) == null
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        logger.info("Reflecting contact sync create for identity {}", contactModelData.identity)

        val encryptedEnvelopeResult = getEncryptedContactSyncCreate(
            contactModelData.toFullSyncContact(),
            mdProperties,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    override val runAfterSuccessfulTransaction: (transactionResult: Unit) -> ContactModel = {
        createLocally()
    }
}

package ch.threema.app.asynctasks

import ch.threema.app.AppConstants.THREEMA_SUPPORT_IDENTITY
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ContactServiceImpl
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.ContactModel.AcquaintanceLevel

/**
 * The result of sending some messages to the support.
 */
enum class SendToSupportResult {
    SUCCESS,
    FAILED,
}

/**
 * This class can be used to send messages to the support. It creates the support contact if not
 * already available.
 */
abstract class SendToSupportBackgroundTask(
    myIdentity: IdentityString,
    apiConnector: APIConnector,
    contactModelRepository: ContactModelRepository,
    appRestrictions: AppRestrictions,
) : AddOrUpdateContactBackgroundTask<SendToSupportResult>(
    identity = THREEMA_SUPPORT_IDENTITY,
    acquaintanceLevel = AcquaintanceLevel.DIRECT,
    myIdentity = myIdentity,
    apiConnector = apiConnector,
    contactModelRepository = contactModelRepository,
    addContactRestrictionPolicy = AddContactRestrictionPolicy.IGNORE,
    appRestrictions = appRestrictions,
    expectedPublicKey = ContactServiceImpl.SUPPORT_PUBLIC_KEY,
) {
    final override fun onContactResult(result: ContactResult): SendToSupportResult =
        when (result) {
            is ContactAvailable -> onSupportAvailable(result.contactModel)
            else -> SendToSupportResult.FAILED
        }

    /**
     * This method is called when the support contact is available. It is run on a background
     * thread.
     */
    abstract fun onSupportAvailable(contactModel: ContactModel): SendToSupportResult
}

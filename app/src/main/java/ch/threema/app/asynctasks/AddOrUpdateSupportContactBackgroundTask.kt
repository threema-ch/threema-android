package ch.threema.app.asynctasks

import ch.threema.app.AppConstants.THREEMA_SUPPORT_IDENTITY
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ContactServiceImpl
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.ContactModel.AcquaintanceLevel

/**
 * This class can be used to add the special `*SUPPORT` contact.
 * If this contact is already added, it will be updated.
 *
 * @see SendToSupportBackgroundTask
 */
class AddOrUpdateSupportContactBackgroundTask(
    myIdentity: IdentityString,
    apiConnector: APIConnector,
    contactModelRepository: ContactModelRepository,
    appRestrictions: AppRestrictions,
) : AddOrUpdateContactBackgroundTask<ContactResult>(
    identity = THREEMA_SUPPORT_IDENTITY,
    acquaintanceLevel = AcquaintanceLevel.DIRECT,
    myIdentity = myIdentity,
    apiConnector = apiConnector,
    contactModelRepository = contactModelRepository,
    addContactRestrictionPolicy = AddContactRestrictionPolicy.IGNORE,
    appRestrictions = appRestrictions,
    expectedPublicKey = ContactServiceImpl.SUPPORT_PUBLIC_KEY,
) {
    override fun onContactResult(result: ContactResult): ContactResult = result
}

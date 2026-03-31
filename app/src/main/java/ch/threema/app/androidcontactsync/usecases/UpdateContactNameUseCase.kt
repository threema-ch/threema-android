package ch.threema.app.androidcontactsync.usecases

import ch.threema.app.androidcontactsync.read.AndroidContactLookupException
import ch.threema.app.androidcontactsync.read.AndroidContactReadException
import ch.threema.app.androidcontactsync.read.AndroidContactReader
import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.models.ContactModel

private val logger = getThreemaLogger("UpdateContactNameUseCase")

/**
 * Update the name of the given contacts with the current values from the android contacts.
 *
 * Note that the android contact lookup info may be updated in case it has changed in the content provider.
 */
class UpdateContactNameUseCase(
    private val androidContactReader: AndroidContactReader,
    private val getAndroidContactNameUseCase: GetAndroidContactNameUseCase,
) {
    /**
     * Update the contact names from the synchronized android contacts.
     */
    suspend fun call(contactModels: Set<ContactModel>) {
        contactModels.forEach { contactModel -> updateContactLookupInfoAndContactName(contactModel) }
    }

    private suspend fun updateContactLookupInfoAndContactName(contactModel: ContactModel) {
        logger.info("Trying to update name of contact {}", contactModel.identity)
        val androidContact = getAndroidContact(contactModel) ?: return

        updateAndroidContactLookupInfoIfNeeded(
            contactModel = contactModel,
            androidContact = androidContact,
        )

        updateNameIfPossible(
            contactModel = contactModel,
            androidContact = androidContact,
        )
    }

    private suspend fun getAndroidContact(contactModel: ContactModel): AndroidContact? {
        val contactModelData = contactModel.data ?: run {
            logger.warn("Cannot update contact's name: contact model data is null")
            return null
        }

        val androidContactLookupInfo = contactModelData.androidContactLookupInfo ?: run {
            logger.warn("Cannot update contact's name: contact does not have android contact lookup information")
            return null
        }

        val androidContact = try {
            androidContactReader.readAndroidContactWithLookup(androidContactLookupInfo.getContactUri())
        } catch (e: AndroidContactReadException) {
            when (e) {
                is AndroidContactReadException.MissingPermission -> logger.warn("No permission to read the contacts", e)
                is AndroidContactReadException.MultipleContactIdsPerLookupKey -> logger.warn("Invalid data: multiple contact ids per lookup key", e)
                is AndroidContactReadException.MultipleLookupKeysPerContactId -> logger.warn("Invalid data: multiple lookup keys per contact id", e)
                is AndroidContactReadException.MultipleLookupKeysPerRawContact -> logger.warn("Invalid data: multiple lookup keys per raw contact", e)
                is AndroidContactReadException.Other -> logger.error("Could not read contacts.", e)
            }
            return null
        } catch (e: AndroidContactLookupException) {
            when (e) {
                is AndroidContactLookupException.LookupInfoException -> logger.error("Could not get lookup info", e)
                is AndroidContactLookupException.UnstableContactLookupInfo -> logger.warn("Race condition while performing lookup", e)
                is AndroidContactLookupException.MultipleContactsReceivedForLookup -> logger.warn("Received multiple contacts in lookup", e)
                is AndroidContactLookupException.WrongContactReceivedForLookup -> logger.warn("Received wrong contact for lookup", e)
            }
            return null
        }

        if (androidContact == null) {
            logger.info("Cannot update contact's name: no android contact found")
        }

        return androidContact
    }

    private fun updateAndroidContactLookupInfoIfNeeded(
        contactModel: ContactModel,
        androidContact: AndroidContact,
    ) {
        val existingAndroidContactLookupInfo = contactModel.data?.androidContactLookupInfo ?: run {
            logger.error("Cannot update android contact lookup info as the contact model data is null")
            return
        }
        val currentAndroidContactLookupInfo = androidContact.lookupInfo.toAndroidContactLookupInfo()

        // We do update the contact lookup info to prevent that it deviates to much over time. According to android documentation this should not be
        // necessary, but it won't hurt either.
        if (existingAndroidContactLookupInfo != currentAndroidContactLookupInfo) {
            logger.info("Contact lookup info is not up to date: updating android contact lookup info of {}", contactModel.identity)
            contactModel.setAndroidContactLookupKey(currentAndroidContactLookupInfo)
        }
    }

    private fun updateNameIfPossible(
        contactModel: ContactModel,
        androidContact: AndroidContact,
    ) {
        val contactName = getAndroidContactNameUseCase.call(androidContact) ?: run {
            logger.warn("Cannot update contact's name: no name could be determined")
            return
        }

        logger.info("Updating the name of contact {}", contactModel.identity)
        contactModel.setNameFromLocal(
            firstName = contactName.firstName,
            lastName = contactName.lastName,
        )
    }

    private fun LookupInfo.toAndroidContactLookupInfo() = AndroidContactLookupInfo(
        lookupKey = lookupKey.key,
        contactId = contactId.id.toLong(),
    )
}

package ch.threema.app.androidcontactsync.usecases

import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.ContactName
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.associateWithNotNull

private val logger = getThreemaLogger("GetAndroidContactNameUseCase")

/**
 * Get the android contact's name. As there are possibly many names for a contact, we determine a name per raw contact based on
 * [getRawContactNameUseCase] and then choose the most frequent one. If there are multiple names that are used in equally many raw contacts, then we
 * take the name of the raw contact with the lowest id.
 */
class GetAndroidContactNameUseCase(
    private val getRawContactNameUseCase: GetRawContactNameUseCase,
) {
    fun call(androidContact: AndroidContact): ContactName? {
        // Maps all contact names of the android contact's raw contacts to its raw contact
        val contactNameToRawContacts: Map<ContactName, Set<RawContact>> = buildMap {
            val contactNameToMutableRawContacts = mutableMapOf<ContactName, MutableSet<RawContact>>()
            androidContact
                .rawContacts
                .associateWithNotNull(getRawContactNameUseCase::call)
                .forEach { (rawContact, contactName) ->
                    contactNameToMutableRawContacts.getOrPut(
                        key = contactName,
                        defaultValue = { mutableSetOf() },
                    ).add(rawContact)
                }
            putAll(contactNameToMutableRawContacts)
        }

        // A list of contact names mapped to all raw contacts that use the corresponding name sorted descending by the number of raw contacts
        val namesSortedByFrequency = contactNameToRawContacts
            .entries
            .sortedByDescending { entry -> entry.value.size }

        return when (namesSortedByFrequency.size) {
            0 -> null
            1 -> namesSortedByFrequency.first().key
            else -> {
                logger.info(
                    "{} different names found for android contact {} with the following frequencies: {}",
                    namesSortedByFrequency.size,
                    androidContact.lookupInfo.contactId.id,
                    namesSortedByFrequency.joinToString(separator = ", ") { nameEntry -> nameEntry.value.size.toString() },
                )

                // In case there are several contact names, we take the one that is used most often and if that applies to multiple names, we take the
                // one with the lowest raw contact id amongst the most frequently used names.
                val maxFrequency = namesSortedByFrequency.first().value.size
                namesSortedByFrequency
                    .takeWhile { entry -> entry.value.size == maxFrequency }
                    .minBy { entry -> entry.value.minOf { rawContacts -> rawContacts.rawContactId.id } }
                    .key
            }
        }
    }
}

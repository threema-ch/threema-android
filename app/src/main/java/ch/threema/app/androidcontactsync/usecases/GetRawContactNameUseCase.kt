package ch.threema.app.androidcontactsync.usecases

import ch.threema.app.androidcontactsync.types.ContactName
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.StructuredName
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("GetRawContactNameUseCase")

/**
 * Get the raw contact's name grouped into a first and last name. In case there are several structured names linked to this raw contact, the grouping
 * is done for each of them. If they lead to different first and/or last names, the most frequent one that is available is chosen. Otherwise the
 * longest name is used.
 */
class GetRawContactNameUseCase {
    fun call(rawContact: RawContact): ContactName? {
        val contactNameByFrequency = rawContact.structuredNames
            .mapNotNull(StructuredName::reduceToFirstAndLastName)
            .groupingBy { it }
            .eachCount()

        val namesSortedByFrequency = contactNameByFrequency
            .entries
            .sortedWith { a, b ->
                // First we sort by their frequency (ascending)
                when (val compareFrequency = a.value.compareTo(b.value)) {
                    // In case they are equally frequent, we sort by name length (ascending)
                    0 -> a.fullNameLength().compareTo(b.fullNameLength())
                    else -> compareFrequency
                }
            }

        return when (namesSortedByFrequency.size) {
            0 -> null
            1 -> namesSortedByFrequency.last().key
            else -> {
                logger.info(
                    "{} different names found for raw contact {} with the following frequencies: {}",
                    namesSortedByFrequency.size,
                    rawContact.rawContactId.id,
                    namesSortedByFrequency.joinToString(separator = ", ") { nameEntry -> nameEntry.value.toString() },
                )
                namesSortedByFrequency.last().key
            }
        }
    }
}

private fun Map.Entry<ContactName, Int>.fullNameLength(): Int =
    key.fullName.length

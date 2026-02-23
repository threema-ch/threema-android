package ch.threema.app.debug

import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AndroidContactLookupInfo

private val logger = getThreemaLogger("AndroidContactSyncLogger")

/**
 * This logger is used to count the number of identical contacts when importing them from the android contacts.
 */
class AndroidContactSyncLogger {
    private val nameMap = mutableMapOf<Pair<String, String>, MutableList<AndroidContactLookupInfo>>()

    /**
     * Add the first and last name that was received for a certain lookup key to the logger.
     */
    fun addSyncedNames(androidContactLookupInfo: AndroidContactLookupInfo, firstName: String?, lastName: String?) {
        nameMap.getOrPut(firstName.orEmpty() to lastName.orEmpty()) { mutableListOf() }.add(androidContactLookupInfo)
    }

    /**
     * Check whether there were multiple contacts with the same name added.
     */
    fun logDuplicates() {
        val duplicateNames = nameMap.values.filter { it.size > 1 }
        duplicateNames.forEach { androidContactLookupInfo ->
            // The number of different combinations of lookup key and contact id
            val lookupKeyAndContactIdCombinations = androidContactLookupInfo.toSet().size
            // The number of different lookup keys
            val lookupKeys = androidContactLookupInfo.map(AndroidContactLookupInfo::lookupKey).toSet().size
            // The number of different contact ids
            val contactIds = androidContactLookupInfo.map(AndroidContactLookupInfo::contactId).toSet().size
            logger.warn(
                "Got the same name for {} contacts (with {} lookup key and contact id combinations; {} lookup keys; {} contact ids)",
                androidContactLookupInfo.size,
                lookupKeyAndContactIdCombinations,
                lookupKeys,
                contactIds,
            )
        }

        if (duplicateNames.isEmpty()) {
            logger.info("All synchronized android contacts' names are unique")
        }
    }
}

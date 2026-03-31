package ch.threema.app.androidcontactsync.types

/**
 * This represents a contact of the android content provider. The associated data is stored in its [rawContacts]. Note that every raw contact
 * in [rawContacts] must have the same lookup information as in [lookupInfo].
 */
data class AndroidContact(
    val lookupInfo: LookupInfo,
    val rawContacts: Set<RawContact>,
) {
    init {
        require(
            rawContacts.all { rawContact -> rawContact.lookupInfo == lookupInfo },
        ) {
            "Every raw contact of an android contact must have the same lookup information."
        }

        require(
            rawContacts.distinctBy(RawContact::rawContactId).size == rawContacts.size,
        ) {
            "Every raw contact of an android contact must have a unique raw contact id"
        }
    }
}

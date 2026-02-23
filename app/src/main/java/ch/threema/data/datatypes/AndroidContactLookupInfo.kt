package ch.threema.data.datatypes

/**
 * This contains the required information to look up a contact in the android contacts.
 */
data class AndroidContactLookupInfo(
    /**
     * The lookup key used to look up a contact.
     */
    val lookupKey: String,

    /**
     * The contact id is only used to optimize the performance when querying the contact.
     */
    val contactId: Long?,
) {
    /**
     * The obfuscated string representation of the android contact lookup info. This can be used for logging purposes.
     */
    val obfuscatedString: String
        get() = "${lookupKey.hashCode()}/$contactId"
}

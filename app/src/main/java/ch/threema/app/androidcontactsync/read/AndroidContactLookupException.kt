package ch.threema.app.androidcontactsync.read

sealed class AndroidContactLookupException : Throwable() {
    /**
     * There was a failure in getting the lookup information.
     */
    class LookupInfoException(override val cause: ch.threema.app.androidcontactsync.read.LookupInfoException) : AndroidContactLookupException()

    /**
     * The contact lookup info reader returned unstable lookup info.
     */
    class UnstableContactLookupInfo(override val message: String?) : AndroidContactLookupException()

    /**
     * There was more than 1 contact ([amountOfContacts]) returned by looking up a specific contact. This indicates that the provided data is corrupt
     * and cannot be used reliably.
     */
    class MultipleContactsReceivedForLookup(val amountOfContacts: Int) : AndroidContactLookupException()

    /**
     * A wrong contact has been received
     */
    class WrongContactReceivedForLookup() : AndroidContactLookupException()
}

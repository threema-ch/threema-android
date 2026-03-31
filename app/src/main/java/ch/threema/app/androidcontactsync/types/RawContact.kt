package ch.threema.app.androidcontactsync.types

import ch.threema.app.androidcontactsync.read.ContactDataRow
import ch.threema.app.androidcontactsync.read.ContactDataRow.EmailAddressRow
import ch.threema.app.androidcontactsync.read.ContactDataRow.PhoneNumberRow
import ch.threema.app.androidcontactsync.read.ContactDataRow.StructuredNameRow
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("RawContact")

data class RawContact(
    val rawContactId: RawContactId,
    val lookupInfo: LookupInfo,
    val phoneNumbers: Set<PhoneNumber>,
    val emailAddresses: Set<EmailAddress>,
    val structuredNames: Set<StructuredName>,
) {
    class Builder(
        private val rawContactId: RawContactId,
        private val lookupInfo: LookupInfo,
    ) {
        private val phoneNumbers = mutableSetOf<PhoneNumber>()
        private val emailAddresses = mutableSetOf<EmailAddress>()
        private val structuredNames = mutableSetOf<StructuredName>()

        /**
         * Add a contact data row to the raw contact builder.
         *
         * @throws [RawContactBuildException] in case the raw contact id does not match or the raw contact is associated with different lookup info
         */
        @Throws(RawContactBuildException::class)
        fun addContactDataRow(contactDataRow: ContactDataRow) {
            if (contactDataRow.rawContactId != rawContactId) {
                logger.error("Trying to add row that does not belong to this raw contact")
                throw RawContactBuildException("Wrong raw contact data")
            }

            if (contactDataRow.lookupInfo != lookupInfo) {
                logger.warn("Trying to add row that does not match regarding the lookup info")
                throw RawContactBuildException("Contact data corrupt. Lookup info does not match for the same raw contact.")
            }

            when (contactDataRow) {
                is PhoneNumberRow -> phoneNumbers.add(contactDataRow.phoneNumber)
                is EmailAddressRow -> emailAddresses.add(contactDataRow.emailAddress)
                is StructuredNameRow -> structuredNames.add(contactDataRow.structuredName)
            }
        }

        fun build() = RawContact(
            rawContactId = rawContactId,
            lookupInfo = lookupInfo,
            phoneNumbers = phoneNumbers,
            emailAddresses = emailAddresses,
            structuredNames = structuredNames,
        )

        class RawContactBuildException(override val message: String?) : Exception()
    }
}

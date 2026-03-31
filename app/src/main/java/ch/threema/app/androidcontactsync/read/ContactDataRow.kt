package ch.threema.app.androidcontactsync.read

import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.app.androidcontactsync.types.StructuredName

sealed interface ContactDataRow {
    val rawContactId: RawContactId
    val lookupInfo: LookupInfo

    data class PhoneNumberRow(
        override val rawContactId: RawContactId,
        override val lookupInfo: LookupInfo,
        val phoneNumber: PhoneNumber,
    ) : ContactDataRow

    data class EmailAddressRow(
        override val rawContactId: RawContactId,
        override val lookupInfo: LookupInfo,
        val emailAddress: EmailAddress,
    ) : ContactDataRow

    data class StructuredNameRow(
        override val rawContactId: RawContactId,
        override val lookupInfo: LookupInfo,
        val structuredName: StructuredName,
    ) : ContactDataRow
}

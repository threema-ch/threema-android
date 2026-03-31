package ch.threema.app.androidcontactsync

import ch.threema.app.androidcontactsync.read.ContactDataRow
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.LookupKey
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.app.androidcontactsync.types.StructuredName

val testLookupKey = LookupKey("LookupKey")
val testContactId = ContactId(1u)
val testLookupInfo = LookupInfo(testLookupKey, testContactId)

fun getPhoneNumberSequence(): Sequence<PhoneNumber> =
    generateSequence(0) { it + 1 }
        .map { number -> String.format("+41 %9d", number) }
        .map(::PhoneNumber)

fun getEmailAddressSequence(): Sequence<EmailAddress> =
    generateSequence(0) { it + 1 }
        .map { number -> "max.muster.$number@example.org" }
        .map(::EmailAddress)

fun getStructuredNameSequence(): Sequence<StructuredName> =
    generateSequence(0) { it + 1 }
        .map { number ->
            StructuredName(
                givenName = "Max$number",
                familyName = "Muster",
            )
        }

fun getPhoneNumberRowSequence(
    rawContactId: RawContactId,
    lookupInfo: LookupInfo,
): Sequence<ContactDataRow.PhoneNumberRow> =
    getPhoneNumberSequence()
        .map { phoneNumber ->
            ContactDataRow.PhoneNumberRow(
                rawContactId = rawContactId,
                lookupInfo = lookupInfo,
                phoneNumber = phoneNumber,
            )
        }

fun getEmailAddressRowSequence(
    rawContactId: RawContactId,
    lookupInfo: LookupInfo,
): Sequence<ContactDataRow.EmailAddressRow> =
    getEmailAddressSequence()
        .map { emailAddress ->
            ContactDataRow.EmailAddressRow(
                rawContactId = rawContactId,
                lookupInfo = lookupInfo,
                emailAddress = emailAddress,
            )
        }

fun getStructuredNameRowSequence(
    rawContactId: RawContactId,
    lookupInfo: LookupInfo,
): Sequence<ContactDataRow.StructuredNameRow> =
    getStructuredNameSequence()
        .map { structuredName ->
            ContactDataRow.StructuredNameRow(
                rawContactId = rawContactId,
                lookupInfo = lookupInfo,
                structuredName = structuredName,
            )
        }

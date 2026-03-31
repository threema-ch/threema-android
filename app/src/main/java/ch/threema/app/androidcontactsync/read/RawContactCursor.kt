package ch.threema.app.androidcontactsync.read

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import ch.threema.app.androidcontactsync.read.ContactDataRow.EmailAddressRow
import ch.threema.app.androidcontactsync.read.ContactDataRow.PhoneNumberRow
import ch.threema.app.androidcontactsync.read.ContactDataRow.StructuredNameRow
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.LookupKey
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.app.androidcontactsync.types.StructuredName

class RawContactCursor private constructor(
    private val cursor: Cursor,
    private val lookupKeyIndex: Int,
    private val contactIdIndex: Int,
    private val rawContactIdIndex: Int,
    private val mimeTypeIndex: Int,
    private val phoneNumberIndex: Int,
    private val emailAddressIndex: Int,
    private val prefixIndex: Int,
    private val givenNameIndex: Int,
    private val middleNameIndex: Int,
    private val familyNameIndex: Int,
    private val suffixIndex: Int,
    private val displayNameIndex: Int,
) : AutoCloseable {

    /**
     * Move the cursor to the next row.
     *
     * @return true if the move succeeded and false if the cursor is already at the last position
     */
    fun moveToNext(): Boolean = cursor.moveToNext()

    /**
     * Extract a contact data row that contains either a phone number, email address or structured name.
     *
     * @throws CursorException if the current row contains invalid values
     */
    @Throws(CursorException::class)
    fun getContactDataRow(): ContactDataRow =
        getPhoneNumberRow()
            ?: getEmailAddressRow()
            ?: getStructuredNameRow()
            ?: throw CursorException.IllegalMimeType(getMimeType())

    /**
     * Close the cursor.
     */
    override fun close() {
        cursor.close()
    }

    private fun getPhoneNumberRow(): PhoneNumberRow? {
        if (!isPhoneNumberData()) {
            return null
        }

        return PhoneNumberRow(
            rawContactId = getRawContactId(),
            lookupInfo = getLookupInfo(),
            phoneNumber = getPhoneNumber(),
        )
    }

    private fun getEmailAddressRow(): EmailAddressRow? {
        if (!isEmailAddressData()) {
            return null
        }

        return EmailAddressRow(
            rawContactId = getRawContactId(),
            lookupInfo = getLookupInfo(),
            emailAddress = getEmailAddress(),
        )
    }

    private fun getStructuredNameRow(): StructuredNameRow? {
        if (!isStructuredNameData()) {
            return null
        }

        return StructuredNameRow(
            rawContactId = getRawContactId(),
            lookupInfo = getLookupInfo(),
            structuredName = getStructuredName(),
        )
    }

    private fun isPhoneNumberData(): Boolean =
        getMimeType() == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE

    private fun isEmailAddressData(): Boolean =
        getMimeType() == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE

    private fun isStructuredNameData(): Boolean =
        getMimeType() == ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE

    private fun getMimeType(): String? =
        cursor.getStringOrNull(mimeTypeIndex)

    private fun getLookupInfo(): LookupInfo =
        LookupInfo(
            lookupKey = getLookupKey(),
            contactId = getContactId(),
        )

    private fun getLookupKey(): LookupKey =
        cursor.getStringOrNull(lookupKeyIndex)?.let { value -> LookupKey(value) }
            ?: throw CursorException.MissingProperty("lookupKey")

    private fun getContactId(): ContactId =
        cursor.getLongOrNull(contactIdIndex)?.let { value -> ContactId.fromLong(value) }
            ?: throw CursorException.MissingProperty("contactId")

    private fun getRawContactId(): RawContactId =
        cursor.getLongOrNull(rawContactIdIndex)?.let { value -> RawContactId.fromLong(value) }
            ?: throw CursorException.MissingProperty("rawContactId")

    private fun getPhoneNumber(): PhoneNumber =
        cursor.getStringOrNull(phoneNumberIndex)?.let { value -> PhoneNumber.fromString(value) }
            ?: throw CursorException.MissingProperty("phoneNumber")

    private fun getEmailAddress(): EmailAddress =
        cursor.getStringOrNull(emailAddressIndex)?.let { value -> EmailAddress.fromString(value) }
            ?: throw CursorException.MissingProperty("emailAddress")

    private fun getStructuredName() = StructuredName(
        prefix = cursor.getStringOrNull(prefixIndex),
        givenName = cursor.getStringOrNull(givenNameIndex),
        middleName = cursor.getStringOrNull(middleNameIndex),
        familyName = cursor.getStringOrNull(familyNameIndex),
        suffix = cursor.getStringOrNull(suffixIndex),
        displayName = cursor.getStringOrNull(displayNameIndex),
    )

    companion object {
        private val uri = ContactsContract.Data.CONTENT_URI

        private val projection =
            arrayOf(
                // Get columns that are used to link the data to a contact
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.RAW_CONTACT_ID,

                // The mime type is used to filter just the data we need (name, phone, email)
                ContactsContract.Data.MIMETYPE,

                // This contains the phone number
                ContactsContract.CommonDataKinds.Phone.NUMBER,

                // This contains the email address
                ContactsContract.CommonDataKinds.Email.ADDRESS,

                // These are the columns used in the structured name
                ContactsContract.CommonDataKinds.StructuredName.PREFIX,
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
            )

        private const val SELECTION =
            "${ContactsContract.CommonDataKinds.Phone.IN_DEFAULT_DIRECTORY} = 1 AND ${ContactsContract.Data.MIMETYPE} in (?, ?, ?)"

        private val selectionArgs =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            )

        /**
         * Create a raw contact cursor.
         *
         * @throws [CursorCreateException] if the cursor could not be created
         * @throws [SecurityException] if there is no permission to read the contacts
         */
        @Throws(CursorCreateException::class, SecurityException::class)
        fun createRawContactCursor(contentResolver: ContentResolver): RawContactCursor {
            val cursor = contentResolver.query(
                uri,
                projection,
                SELECTION,
                selectionArgs,
                /* sortOrder = */
                null,
            ) ?: throw CursorCreateException(message = "Created cursor is null")

            return fromCursor(cursor)
        }

        /**
         * Create a raw contact cursor to lookup the raw contact with [lookupInfo].
         *
         * @throws [CursorCreateException] if the cursor could not be created
         * @throws [SecurityException] if there is no permission to read the contacts
         */
        fun createRawContactCursorForLookup(contentResolver: ContentResolver, lookupInfo: LookupInfo): RawContactCursor {
            val contactIdSelection = "${ContactsContract.Data.CONTACT_ID} = ?"

            val cursor = contentResolver.query(
                uri,
                projection,
                "$SELECTION AND $contactIdSelection",
                selectionArgs + lookupInfo.contactId.id.toString(),
                /* sortOrder = */
                null,
            ) ?: throw CursorCreateException(message = "Created cursor is null")

            return fromCursor(cursor)
        }

        private fun fromCursor(cursor: Cursor): RawContactCursor = try {
            with(cursor) {
                RawContactCursor(
                    cursor = cursor,
                    lookupKeyIndex = getColumnIndexOrThrow(ContactsContract.Data.LOOKUP_KEY),
                    contactIdIndex = getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID),
                    rawContactIdIndex = getColumnIndexOrThrow(ContactsContract.Data.RAW_CONTACT_ID),
                    mimeTypeIndex = getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE),
                    phoneNumberIndex = getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    emailAddressIndex = getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS),
                    prefixIndex = getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.PREFIX),
                    givenNameIndex = getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME),
                    middleNameIndex = getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME),
                    familyNameIndex = getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME),
                    suffixIndex = getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.SUFFIX),
                    displayNameIndex = getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME),
                )
            }
        } catch (e: IllegalArgumentException) {
            throw CursorCreateException(cause = e)
        }

        /**
         * The cursor could not be created.
         */
        class CursorCreateException(message: String? = null, cause: Throwable? = null) : Throwable(
            message = message,
            cause = cause,
        )

        sealed class CursorException : Throwable() {
            /**
             * Thrown if the mime type [mimeType] of the current row is invalid.
             */
            class IllegalMimeType(val mimeType: String?) : CursorException()

            /**
             * Thrown if the property with the name [propertyName] is missing.
             */
            class MissingProperty(val propertyName: String) : CursorException()
        }
    }
}

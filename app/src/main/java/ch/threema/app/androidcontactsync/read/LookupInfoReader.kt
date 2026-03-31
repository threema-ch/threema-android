package ch.threema.app.androidcontactsync.read

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.LookupKey

class LookupInfoReader(private val appContext: Context) {

    @WorkerThread
    @Throws(LookupInfoException::class)
    fun getLookupInfo(androidContactLookupUri: Uri): LookupInfo? = try {
        appContext.contentResolver.queryLookupInfo(androidContactLookupUri)?.use { cursor ->
            if (!cursor.moveToNext()) {
                return null
            }

            try {
                return cursor.getLookupInfo()
            } catch (e: IllegalArgumentException) {
                throw LookupInfoException.Other(cause = e)
            }
        } ?: throw LookupInfoException.CursorCreateException(message = "Created cursor is null")
    } catch (e: SecurityException) {
        throw LookupInfoException.MissingPermission(cause = e)
    }

    companion object {
        private val projection = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts._ID,
        )

        private fun ContentResolver.queryLookupInfo(androidContactLookupUri: Uri) =
            query(
                androidContactLookupUri,
                projection,
                null,
                null,
                null,
            )

        @Throws(IllegalArgumentException::class)
        private fun Cursor.getLookupInfo(): LookupInfo? {
            val lookupKeyIndex = getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val contactIdIndex = getColumnIndexOrThrow(ContactsContract.Contacts._ID)

            val lookupKeyValue = getStringOrNull(lookupKeyIndex)
            val contactIdValue = getLongOrNull(contactIdIndex)

            if (lookupKeyValue == null || contactIdValue == null) {
                return null
            }

            val lookupKey = LookupKey(lookupKeyValue)
            val contactId = ContactId.fromLong(contactIdValue) ?: return null

            return LookupInfo(
                lookupKey = lookupKey,
                contactId = contactId,
            )
        }
    }
}

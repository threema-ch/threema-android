package ch.threema.app.androidcontactsync

import android.net.Uri
import android.provider.ContactsContract
import ch.threema.data.datatypes.AndroidContactLookupInfo

/**
 * Get the contact uri that can be used to access the android contact.
 */
fun AndroidContactLookupInfo.getContactUri(): Uri = Uri.withAppendedPath(
    ContactsContract.Contacts.CONTENT_LOOKUP_URI,
    lookupKey + (if (contactId != null) "/$contactId" else ""),
)

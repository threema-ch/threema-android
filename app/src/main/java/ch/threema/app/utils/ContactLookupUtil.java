package ch.threema.app.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import ch.threema.app.services.ContactService;
import ch.threema.storage.models.ContactModel;

public class ContactLookupUtil {

    public static ContactModel phoneNumberToContact(final Context context, final ContactService contactService, final String phoneNumber) {

        Cursor phonesCursor = null;
        String lookupKey = null;

        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));

            phonesCursor = context.getContentResolver().query(
                uri,
                new String[]{ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY},
                null,
                null,
                null
            );

            if (phonesCursor != null) {
                if (phonesCursor.moveToFirst()) {
                    lookupKey = phonesCursor.getString(phonesCursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY));
                }
            }
        } finally {
            if (phonesCursor != null) {
                phonesCursor.close();
            }
        }

        if (!TestUtil.isEmptyOrNull(lookupKey)) {
            return contactService.getByLookupKey(lookupKey);
        }

        return null;
    }
}

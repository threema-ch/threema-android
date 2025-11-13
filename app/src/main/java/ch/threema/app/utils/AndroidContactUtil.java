/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.utils;

import static ch.threema.storage.models.ContactModel.DEFAULT_ANDROID_CONTACT_AVATAR_EXPIRY;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.debug.AndroidContactSyncLogger;
import ch.threema.app.debug.LookupKeyAndContactId;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.FileService;
import ch.threema.app.services.UserService;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;

public class AndroidContactUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AndroidContactUtil");
    private UserService userService;
    private FileService fileService;

    private static AndroidContactUtil sInstance = null;

    public static synchronized AndroidContactUtil getInstance() {
        if (sInstance == null) {
            sInstance = new AndroidContactUtil();
        }
        return sInstance;
    }

    private AndroidContactUtil() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager != null) {
            this.userService = serviceManager.getUserService();
            this.fileService = serviceManager.getFileService();
        }
    }

    private static final String[] NAME_PROJECTION = new String[]{
        ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.Contacts.SORT_KEY_ALTERNATIVE,
        ContactsContract.Contacts.LOOKUP_KEY
    };

    private static final String[] RAW_CONTACT_PROJECTION = new String[]{
        ContactsContract.RawContacts._ID,
        ContactsContract.RawContacts.CONTACT_ID,
        ContactsContract.RawContacts.SYNC1,
    };

    private static final String[] STRUCTURED_NAME_FIELDS = new String[]{
        ContactsContract.CommonDataKinds.StructuredName.PREFIX,
        ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
        ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
        ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
        ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME
    };

    private final ContentResolver contentResolver = ThreemaApplication.getAppContext().getContentResolver();

    private @Nullable Account getAccount() {
        if (userService == null) {
            logger.info("UserService not available");
            return null;
        }
        return userService.getAccount();
    }

    public static class RawContactInfo {
        public final long contactId;
        public final long rawContactId;

        RawContactInfo(long contactId, long rawContactId) {
            this.contactId = contactId;
            this.rawContactId = rawContactId;
        }
    }

    private static class ContactName {
        final String firstName;
        final String lastName;

        public ContactName(@Nullable String firstName, @Nullable String lastName) {
            this.firstName = firstName != null ? firstName.trim() : null;
            this.lastName = lastName != null ? lastName.trim() : null;
        }
    }

    /**
     * Return a valid uri to the given contact that can be used to build an intent for the contact app
     * It is safe to call this method if permission to access contacts is not granted - null will be returned in that case
     *
     * @param contactModel ContactModel for which to get the Android contact URI
     * @return a valid uri pointing to the android contact or null if permission was not granted, no android contact is linked or android contact could not be looked up
     */
    @Nullable
    public Uri getAndroidContactUri(@NonNull ContactModel contactModel, @NonNull Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        ContactModelData data = contactModel.getData();

        if (data != null) {
            final String androidContactLookupKey = data.androidContactLookupKey;
            if (androidContactLookupKey != null) {
                return Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, androidContactLookupKey);
            }
        }
        return null;
    }

    /**
     * Update the avatar for the specified contact from Android's contact database, if any.
     * If there's no avatar for this Android contact, any current avatar file will be deleted.
     * <p>
     * It is safe to call this method even if permission to read contacts is not given
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    public void updateAvatarByAndroidContact(@NonNull ContactModel contactModel, @NonNull Context context) {
        if (fileService == null) {
            logger.info("FileService not available");
            return;
        }

        ContactModelData data = contactModel.getData();
        if (data == null) {
            logger.warn("Contact model data is null");
            return;
        }

        final String androidContactLookupKey = data.androidContactLookupKey;
        if (androidContactLookupKey == null) {
            logger.warn("Cannot update avatar of contact {} because there is no lookup key", contactModel.getIdentity());
            return;
        }

        // contactUri will be null if permission is not granted
        Uri contactUri = getAndroidContactUri(contactModel, context);
        if (contactUri != null) {
            Bitmap bitmap = AvatarConverterUtil.convert(ThreemaApplication.getAppContext(), contactUri);

            if (bitmap != null) {
                try {
                    fileService.writeAndroidDefinedProfilePicture(contactModel.getIdentity(), BitmapUtil.bitmapToByteArray(bitmap, Bitmap.CompressFormat.PNG, 100));
                    contactModel.setLocalAvatarExpires(Instant.ofEpochMilli(System.currentTimeMillis() + DEFAULT_ANDROID_CONTACT_AVATAR_EXPIRY));
                    return;
                } catch (Exception e) {
                    logger.error("Exception while writing android defined profile picture", e);
                }
            } else {
                // delete old avatar
                if (!fileService.hasAndroidDefinedProfilePicture(contactModel.getIdentity())) {
                    return;
                }
                logger.info("Removing android defined profile picture for identity {}", contactModel.getIdentity());
                boolean success = fileService.removeAndroidDefinedProfilePicture(contactModel.getIdentity());
                if (success) {
                    contactModel.setLocalAvatarExpires(Instant.ofEpochMilli(System.currentTimeMillis() + DEFAULT_ANDROID_CONTACT_AVATAR_EXPIRY));
                    return;
                }
            }
        }

        // Note that directly logging the lookup key and the contact uri is ok for debug level.
        logger.debug("Unable to get avatar for {} lookupKey = {} contactUri = {}", contactModel.getIdentity(), androidContactLookupKey, contactUri);
    }

    /**
     * Update the name of this contact according to the name of the Android contact
     *
     * @param androidContactSyncLogger this logger can be provided to log duplicate contact names
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    public void updateNameByAndroidContact(
        @NonNull ContactModel contactModel,
        @Nullable AndroidContactSyncLogger androidContactSyncLogger,
        @NonNull Context context
    ) throws ThreemaException {
        ContactModelData data = contactModel.getData();
        if (data == null) {
            logger.warn("Contact model data is null");
            return;
        }
        Uri namedContactUri = getAndroidContactUri(contactModel, context);
        if (namedContactUri == null) {
            logger.info("Unable to get android contact uri for {} obfuscatedLookupKey = {}", contactModel.getIdentity(), getObfuscatedAndroidContactLookupKey(data));
            return;
        }

        ContactName contactName = getContactName(namedContactUri);

        if (contactName == null) {
            logger.info("Unable to get contact name for {} obfuscatedLookupKey = {}", contactModel.getIdentity(), getObfuscatedAndroidContactLookupKey(data));
            // remove contact link to unresolvable contact
            contactModel.removeAndroidContactLink();
            throw new ThreemaException("Unable to get contact name");
        }

        if (androidContactSyncLogger != null && data.androidContactLookupKey != null) {
            LookupKeyAndContactId lookupKeyAndContactId = getLookupKeyAndContactId(data.androidContactLookupKey);
            androidContactSyncLogger.addSyncedNames(lookupKeyAndContactId, contactName.firstName, contactName.lastName);
        }

        if (!data.firstName.equals(contactName.firstName) || !data.lastName.equals(contactName.lastName)) {
            logger.info("Updating name of contact. identity={}, obfuscatedLookupKey={}",
                contactModel.getIdentity(), getObfuscatedAndroidContactLookupKey(data));
            contactModel.setNameFromLocal(contactName.firstName, contactName.lastName);
        }
    }

    /**
     * Get the contact name for a system contact specified by the specified Uri
     * First we will consider the Structured Name of the contact
     * If the Structured Name is lacking either a first name, a last name, or both, we will fall back to the Display Name
     * If there's still neither first nor last name available, we will resort to the alternative representation of the full name (for Western names, it is the one using the "last, first" format)
     *
     * @param contactUri Uri pointing to the contact
     * @return ContactName object containing first and last name or null if lookup failed
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    @Nullable
    private ContactName getContactName(@NonNull Uri contactUri) {
        if (this.contentResolver == null) {
            logger.error("Cannot get contact name as content resolver is null");
            return null;
        }

        ContactName contactName = null;
        try (Cursor nameCursor = this.contentResolver.query(
            contactUri,
            NAME_PROJECTION,
            null,
            null,
            null)) {

            if (nameCursor != null && nameCursor.moveToFirst()) {
                String lookupKey = nameCursor.getString(nameCursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY));
                if (lookupKey == null) {
                    logger.error("Cannot get contact name as contact lookup key is null");
                    return null;
                }
                logLookupKeyDifference(contactUri, lookupKey);

                contactName = this.getContactNameFromLookupKey(lookupKey);

                // fallback
                if (contactName.firstName == null && contactName.lastName == null) {
                    logger.info("Falling back to alternative sort key.");
                    //lastname, firstname
                    String alternativeSortKey = nameCursor.getString(nameCursor.getColumnIndexOrThrow(ContactsContract.Contacts.SORT_KEY_ALTERNATIVE));

                    if (!TestUtil.isEmptyOrNull(alternativeSortKey)) {
                        String[] lastNameFirstName = alternativeSortKey.split(",");
                        if (lastNameFirstName.length == 2) {
                            String lastName = lastNameFirstName[0].trim();
                            String firstName = lastNameFirstName[1].trim();

                            if (!TestUtil.compare(lastName, "") && !TestUtil.compare(firstName, "")) {
                                contactName = new ContactName(firstName, lastName);
                            }
                        }
                    } else {
                        // no contact name found
                        logger.info("No contact name found for lookup key '{}'", lookupKey);
                        return null;
                    }
                } else {
                    logger.info("Getting structured name for contact was successful");
                }
            } else {
                logger.info("Contact not found");
            }
        } catch (IllegalArgumentException e) {
            logger.error("Could not get name of contact", e);
        }
        return contactName;
    }

    /**
     * Get the contact name for a system contact specified by contactId
     * - First we will consider the Structured Name of the contact
     * - If the Structured Name is lacking either a first name, a last name, or both, we will fall back to the Display Name
     *
     * @param lookupKey the lookup key of the Android contact
     * @return ContactName object containing first and last name
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    private @NonNull ContactName getContactNameFromLookupKey(@NonNull String lookupKey) {
        Map<String, String> structure = this.getStructuredNameByLookupKey(lookupKey);

        String firstName = structure.get(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
        String lastName = structure.get(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);

        String prefix = structure.get(ContactsContract.CommonDataKinds.StructuredName.PREFIX);
        String middleName = structure.get(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
        String suffix = structure.get(ContactsContract.CommonDataKinds.StructuredName.SUFFIX);

        String displayName = structure.get(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);

        StringBuilder contactFirstName = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            contactFirstName.append(prefix);
        }
        if (firstName != null) {
            if (contactFirstName.length() > 0) {
                contactFirstName.append(" ");
            }
            contactFirstName.append(firstName);
        }
        if (middleName != null && !middleName.isEmpty()) {
            if (contactFirstName.length() > 0) {
                contactFirstName.append(' ');
            }
            contactFirstName.append(middleName);
        }

        StringBuilder contactLastName = new StringBuilder();
        if (lastName != null) {
            contactLastName.append(lastName);
        }
        if (suffix != null && !suffix.isEmpty()) {
            if (contactLastName.length() > 0) {
                contactLastName.append(", ");
            }
            contactLastName.append(suffix);
        }

        /* Only use this structured name if we have a first or last name. Otherwise use display name (below) */
        if (contactFirstName.length() > 0 || contactLastName.length() > 0) {
            return new ContactName(contactFirstName.toString(), contactLastName.toString());
        }

        final Pair<String, String> firstLastName = NameUtil.getFirstLastNameFromDisplayName(displayName);
        return new ContactName(firstLastName.first, firstLastName.second);
    }

    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    private @NonNull Map<String, String> getStructuredNameByLookupKey(@NonNull String lookupKey) {
        Map<String, String> structuredName = new TreeMap<>();

        Cursor cursor = this.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            STRUCTURED_NAME_FIELDS,
            ContactsContract.Data.LOOKUP_KEY + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
            new String[]{lookupKey, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE},
            null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    for (int i = 0; i < STRUCTURED_NAME_FIELDS.length; i++) {
                        structuredName.put(STRUCTURED_NAME_FIELDS[i], cursor.getString(i));
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return structuredName;
    }

    /**
     * Add ContentProviderOperations to create a raw contact for the given identity to a provided List of ContentProviderOperations.
     * Put the identity into the SYNC1 column and set data records for messaging and calling
     *
     * @param contentProviderOperations List of ContentProviderOperations to add this operation to
     * @param systemRawContactId        The raw contact that matched the criteria for aggregation (i.e. email or phone number)
     * @param identity                  the identity of the contact to create a raw contact for
     * @param supportsVoiceCalls        Whether the user has voice calls enabled
     */
    @RequiresPermission(allOf = {Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS})
    public void createThreemaRawContact(@NonNull List<ContentProviderOperation> contentProviderOperations, long systemRawContactId, @NonNull String identity, boolean supportsVoiceCalls) {
        Context context = ThreemaApplication.getAppContext();
        Account account = this.getAccount();
        if (account == null) {
            logger.error("Cannot create threema raw contact as the account is null");
            return;
        }

        if (systemRawContactId == 0L) {
            return;
        }

        int backReference = contentProviderOperations.size();
        logger.debug("Adding contact: {}", identity);

        /* Create our RawContact */
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
        builder.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name);
        builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type);
        builder.withValue(ContactsContract.RawContacts.SYNC1, identity);
        contentProviderOperations.add(builder.build());

        Uri insertUri = ContactsContract.Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

        /* Some contact apps may require a name */
        /*
        builder = ContentProviderOperation.newInsert(insertUri);
        builder.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, backReference);
        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, identity);
        contentProviderOperations.add(builder.build());
        */

        /* Create a Data record of custom type */
        builder = ContentProviderOperation.newInsert(insertUri);
        builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference);
        builder.withValue(ContactsContract.Data.MIMETYPE, context.getString(R.string.contacts_mime_type));
        builder.withValue(ContactsContract.Data.DATA1, identity);
        builder.withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name));
        builder.withValue(ContactsContract.Data.DATA3, context.getString(R.string.threema_message_to, identity));
        builder.withYieldAllowed(true);
        contentProviderOperations.add(builder.build());

        if (supportsVoiceCalls) {
            /* Create a Data record of custom type for call */
            builder = ContentProviderOperation.newInsert(insertUri);
            builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference);
            builder.withValue(ContactsContract.Data.MIMETYPE, context.getString(R.string.call_mime_type));
            builder.withValue(ContactsContract.Data.DATA1, identity);
            builder.withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name));
            builder.withValue(ContactsContract.Data.DATA3, context.getString(R.string.threema_call_with, identity));
            builder.withYieldAllowed(true);
            contentProviderOperations.add(builder.build());
        }

        builder = ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI);
        builder.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, systemRawContactId);
        builder.withValueBackReference(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, backReference);
        builder.withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER);
        contentProviderOperations.add(builder.build());

        logger.info("Create a raw contact for ID {} and aggregate it with system raw contact {}", identity, systemRawContactId);
    }

    /**
     * Delete all raw contacts where the given identity matches the entry in the contact's SYNC1 column
     * It's safe to call this method without contacts permission
     *
     * @param identity the identity of the contact whose raw contact we want to be deleted
     * @return number of raw contacts deleted
     */
    public int deleteThreemaRawContact(@NonNull String identity) {
        if (!ConfigUtils.isPermissionGranted(ThreemaApplication.getAppContext(), Manifest.permission.WRITE_CONTACTS)) {
            return 0;
        }

        Account account = this.getAccount();
        if (account == null) {
            return 0;
        }

        Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI
            .buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ContactsContract.RawContacts.SYNC1, identity)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type).build();

        try {
            return contentResolver.delete(rawContactUri, null, null);
        } catch (Exception e) {
            logger.error("Exception", e);
        }
        return 0;
    }

    /**
     * Delete all raw contacts specified in rawContacts Map
     *
     * @param rawContacts Map of the rawContacts to delete. The key of the map entry contains the identity
     * @return Number of raw contacts that were supposed to be deleted. Does not necessarily represent the real number of deleted raw contacts.
     */
    public int deleteThreemaRawContacts(@NonNull ListMultimap<String, RawContactInfo> rawContacts) {
        if (!ConfigUtils.isPermissionGranted(ThreemaApplication.getAppContext(), Manifest.permission.WRITE_CONTACTS)) {
            return 0;
        }

        Account account = this.getAccount();
        if (account == null) {
            return 0;
        }

        if (rawContacts.isEmpty()) {
            return 0;
        }

        ArrayList<ContentProviderOperation> contentProviderOperations = new ArrayList<>();

        for (Map.Entry<String, RawContactInfo> rawContact : rawContacts.entries()) {
            if (!TestUtil.isEmptyOrNull(rawContact.getKey()) && rawContact.getValue().rawContactId != 0L) {
                try {
                    ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(
                            ContactsContract.RawContacts.CONTENT_URI
                                .buildUpon()
                                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                                .appendQueryParameter(ContactsContract.RawContacts.SYNC1, rawContact.getKey())
                                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type).build())
                        .withSelection(ContactsContract.RawContacts._ID + " = ?", new String[]{String.valueOf(rawContact.getValue().rawContactId)});

                    contentProviderOperations.add(builder.build());
                } catch (Exception e) {
                    logger.error("Exception", e);
                }
            }
        }

        int operationCount = contentProviderOperations.size();
        if (operationCount > 0) {
            try {
                ConfigUtils.applyToContentResolverInBatches(ContactsContract.AUTHORITY, contentProviderOperations);
            } catch (Exception e) {
                logger.error("Error during raw contact deletion! ", e);
            }
            contentProviderOperations.clear();
        }

        logger.debug("Deleted {} raw contacts", operationCount);

        return operationCount;
    }

    /**
     * Delete all raw contacts associated with Threema (including stray ones)
     * Safe to be called without permission
     *
     * @return number of raw contacts deleted
     */
    public int deleteAllThreemaRawContacts() {
        if (!ConfigUtils.isPermissionGranted(ThreemaApplication.getAppContext(), Manifest.permission.WRITE_CONTACTS)) {
            return 0;
        }

        Account account = this.getAccount();
        if (account == null) {
            return 0;
        }

        Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI
            .buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type).build();

        try {
            return contentResolver.delete(rawContactUri, null, null);
        } catch (Exception e) {
            logger.error("Exception", e);
        }
        return 0;
    }

    /**
     * Get a list of all Threema raw contacts from the contact database. This may include "stray" contacts.
     *
     * @return List containing pairs of identity and android contact id, null if permissions have not been granted
     */
    @Nullable
    public ListMultimap<String, RawContactInfo> getAllThreemaRawContacts() {
        if (!ConfigUtils.isPermissionGranted(ThreemaApplication.getAppContext(), Manifest.permission.WRITE_CONTACTS)) {
            return null;
        }

        Account account = this.getAccount();
        if (account == null) {
            return null;
        }

        Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI
            .buildUpon()
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type).build();

        ListMultimap<String, RawContactInfo> rawContacts = ArrayListMultimap.create();
        try (Cursor cursor = contentResolver.query(rawContactUri, RAW_CONTACT_PROJECTION, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long rawContactId = cursor.getLong(0);
                    long contactId = cursor.getLong(1);
                    String identity = cursor.getString(2);
                    rawContacts.put(identity, new RawContactInfo(contactId, rawContactId));
                }
            }
        } catch (Exception e) {
            logger.error("Exception", e);
        }
        return rawContacts;
    }

    /**
     * Get the "main" raw contact representing the Android contact specified by the lookup key
     * We consider the contact referenced as display name source for the Android contact as the "main" contact
     *
     * @param lookupKey The lookup key of the contact
     * @return ID of the raw contact or 0 if none is found
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    public long getMainRawContact(String lookupKey) {
        long rawContactId = 0;
        Uri lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);

        try (Cursor cursor = ThreemaApplication.getAppContext().getContentResolver().query(
            lookupUri,
            new String[]{
                ContactsContract.Contacts.NAME_RAW_CONTACT_ID
            },
            null,
            null,
            null)) {

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    rawContactId = cursor.getLong(0);
                }
            }
        }

        return rawContactId;
    }

    @RequiresPermission(allOf = {Manifest.permission.READ_CONTACTS, Manifest.permission.GET_ACCOUNTS})
    @Nullable
    @WorkerThread
    public Drawable getAccountIcon(@Nullable String androidContactLookupKey) {
        final PackageManager pm = ThreemaApplication.getAppContext().getPackageManager();

        Account myAccount = this.getAccount();
        if (myAccount == null) {
            return null;
        }

        if (androidContactLookupKey == null) {
            return null;
        }

        long nameSourceRawContactId = getMainRawContact(androidContactLookupKey);
        if (nameSourceRawContactId == 0) {
            return null;
        }

        AccountManager accountManager = AccountManager.get(ThreemaApplication.getAppContext());
        AuthenticatorDescription[] descriptions = accountManager.getAuthenticatorTypes();

        Drawable drawable = null;

        Uri nameSourceRawContactUri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, nameSourceRawContactId);
        try (Cursor cursor = this.contentResolver.query(
            nameSourceRawContactUri,
            new String[]{
                ContactsContract.RawContacts.ACCOUNT_TYPE
            }, null, null, null)) {
            if (cursor != null) {
                if (cursor.moveToNext()) {
                    String accountType = cursor.getString(0);
                    for (AuthenticatorDescription description : descriptions) {
                        if (description.type.equalsIgnoreCase(accountType)) {
                            drawable = pm.getDrawable(description.packageName, description.iconId, null);
                            break;
                        }
                    }
                }
            }
        }

        //if no icon found, display the icon of the phone or contacts app
        if (drawable == null) {
            for (String substitutePackageName : new String[]{
                "com.android.contacts",
                "com.android.providers.contacts",
                "com.android.phone",
            }) {
                try {
                    drawable = pm.getApplicationIcon(substitutePackageName);
                    break;
                } catch (PackageManager.NameNotFoundException x) {
                    //
                }
            }
        }
        return drawable;
    }

    /**
     * Open the system's contact editor for the provided Threema contact
     *
     * @param context Context
     * @param contactModel Threema contact
     * @return true if the contact is linked with a system contact (even if no app is available for an ACTION_EDIT intent in the system), false otherwise
     */
    public boolean openContactEditor(@NonNull Context context, @NonNull ContactModel contactModel, int requestCode) {
        Uri contactUri = AndroidContactUtil.getInstance().getAndroidContactUri(contactModel, context);

        if (contactUri != null) {
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(contactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra("finishActivityOnSaveCompleted", true);

            // make sure users are coming back to threema and not the external activity
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
            try {
                if (context instanceof Activity) {
                    ((Activity) context).startActivityForResult(intent, requestCode);
                } else {
                    context.startActivity(intent);
                }
            } catch (Exception e) {
                Toast.makeText(context, "No contact editor found on device.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }

    @Nullable
    private static String getObfuscatedAndroidContactLookupKey(@Nullable ContactModelData contactModelData) {
        if (contactModelData == null) {
            return null;
        }
        return getObfuscatedAndroidContactLookupKey(contactModelData.androidContactLookupKey);
    }

    @Nullable
    private static String getObfuscatedAndroidContactLookupKey(@Nullable String androidContactLookupKey) {
        if (androidContactLookupKey == null) {
            return null;
        }

        LookupKeyAndContactId lookupKeyAndContactId = getLookupKeyAndContactId(androidContactLookupKey);
        int obfuscatedLookupKey = lookupKeyAndContactId.getLookupKey().hashCode();
        long contactId = lookupKeyAndContactId.getContactId();
        // Concatenate the obfuscated lookup key with the raw contact id
        return obfuscatedLookupKey + "/"  + contactId;
    }

    @NonNull
    private static LookupKeyAndContactId getLookupKeyAndContactId(@NonNull String androidContactLookupKey) {
        // The lookup key and contact id are separated by a slash
        int splitIndex = androidContactLookupKey.lastIndexOf('/');
        if (splitIndex == -1) {
            logger.warn("Unexpected lookup key format as it does not contain '/'");
            return new LookupKeyAndContactId(androidContactLookupKey, -1L);
        }

        String lookupKey = androidContactLookupKey.substring(0, splitIndex);
        try {
            long contactId = Long.parseLong(androidContactLookupKey.substring(splitIndex + 1));
            return new LookupKeyAndContactId(lookupKey, contactId);
        } catch (NumberFormatException e) {
            logger.warn("Could not parse the contact id", e);
            return new LookupKeyAndContactId(lookupKey, -2L);
        }
    }

    private void logLookupKeyDifference(@NonNull Uri contactUri, @NonNull String lookupKey) {
        String path = contactUri.getPath();
        if (path == null) {
            logger.warn("No path of contact uri");
            return;
        }

        String lookupKeyAndContactIdFromUri = path.replace("/contacts/lookup/", "");
        int lookupKeySeparationIndex = lookupKeyAndContactIdFromUri.lastIndexOf("/");
        if (lookupKeySeparationIndex < 0) {
            logger.warn("No / in lookup key and contact id from uri");
            return;
        }
        String lookupKeyFromUri = lookupKeyAndContactIdFromUri.substring(0, lookupKeySeparationIndex);
        if (lookupKey.equals(lookupKeyFromUri)) {
            logger.info("Lookup key of uri matches the queried lookup key");
        } else {
            logger.warn("Queried lookup key is different ({} characters vs {} characters)", lookupKey.length(), lookupKeyFromUri.length());
        }
    }
}

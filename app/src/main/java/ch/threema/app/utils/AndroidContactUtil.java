/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.ContactsContract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.FileService;
import ch.threema.app.services.UserService;
import ch.threema.storage.models.ContactModel;
import java8.util.J8Arrays;
import java8.util.stream.Collectors;

import static ch.threema.storage.models.ContactModel.DEFAULT_ANDROID_CONTACT_AVATAR_EXPIRY;

public class AndroidContactUtil {
	private static final Logger logger = LoggerFactory.getLogger(AndroidContactUtil.class);

	// Singleton stuff
	private static AndroidContactUtil sInstance = null;

	public static synchronized AndroidContactUtil getInstance() {
		if (sInstance == null) {
			sInstance = new AndroidContactUtil();
		}
		return sInstance;
	}

	private static final String[] NAME_PROJECTION = new String[]{
			ContactsContract.Contacts.DISPLAY_NAME,
			ContactsContract.Contacts.SORT_KEY_ALTERNATIVE,
			ContactsContract.Contacts._ID
	};
	private static final String[] LOOKUP_KEY_PROJECTION = new String[]{ContactsContract.Contacts.LOOKUP_KEY};

	private static final String[] STRUCTURED_NAME_FIELDS = new String[] {
			ContactsContract.CommonDataKinds.StructuredName.PREFIX,
			ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
			ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
			ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
			ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
			ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME
	};

	private interface JoinContactQuery {
		int _ID = 0;
		int CONTACT_ID = 1;
		int DISPLAY_NAME_SOURCE = 2;
	}

	private final ContentResolver contentResolver = ThreemaApplication.getAppContext().getContentResolver();
	private Map<String, String> identityLookupCache = null;
	private final Object identityLookupCacheLock = new Object();

	private Account getAccount() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if(serviceManager != null) {
			UserService userService = serviceManager.getUserService();
			if(userService != null) {
				return userService.getAccount();
			}
		}
		return null;
	}

	private FileService getFileService() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if(serviceManager != null) {
			try {
				return serviceManager.getFileService();
			} catch (FileSystemNotPresentException ignored) {}
		}
		return null;
	}

	private Long getContactId(String lookupKey) {
		if(TestUtil.empty(lookupKey)) {
			return null;
		}
		Long id = null;
		Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
		if(uri != null) {
			Cursor cursor = this.contentResolver.query(
					uri,
					new String[]{
							ContactsContract.Contacts._ID
					},
					null,
					null,
					null
			);

			if(cursor != null) {
				if(cursor.moveToFirst()) {
					id = cursor.getLong(0);
				}
				cursor.close();
			}
		}

		return id;
	}

	public boolean isThreemaAndroidContactJoined(String identity, String androidContactLookupkey) {
		String lookupKey = this.getRawContactLookupKeyByIdentity(identity);

		if(!TestUtil.empty(lookupKey)) {
			Long threemaContactId = this.getContactId(lookupKey);
			Long androidContactId = this.getContactId(androidContactLookupkey);

			return TestUtil.compare(threemaContactId, androidContactId);
		}

		return false;
	}


	/**
	 * copy of ContactSaveService joinContacts (Google Code)
	 */
	private boolean joinContacts(long contactId1, long contactId2) {
		if (contactId1 == -1 || contactId2 == -1) {
			logger.debug("Invalid arguments for joinContacts request");
			return false;
		}

		final ContentResolver resolver = this.contentResolver;

		// Load raw contact IDs for all raw contacts involved - currently edited and selected
		// in the join UIs
		Cursor c = resolver.query(ContactsContract.RawContacts.CONTENT_URI,
				new String[] {
					ContactsContract.RawContacts._ID,
					ContactsContract.RawContacts.CONTACT_ID,
					ContactsContract.RawContacts.DISPLAY_NAME_SOURCE
				},
				ContactsContract.RawContacts.CONTACT_ID + "=? OR " + ContactsContract.RawContacts.CONTACT_ID + "=?",
				new String[]{String.valueOf(contactId1), String.valueOf(contactId2)}, null);

		long rawContactIds[];
		long verifiedNameRawContactId = -1;

		if(c != null) {
			try {
				if (c.getCount() == 0) {
					return false;
				}
				int maxDisplayNameSource = -1;
				rawContactIds = new long[c.getCount()];
				for (int i = 0; i < rawContactIds.length; i++) {
					c.moveToPosition(i);
					long rawContactId = c.getLong(JoinContactQuery._ID);
					rawContactIds[i] = rawContactId;
					int nameSource = c.getInt(JoinContactQuery.DISPLAY_NAME_SOURCE);
					if (nameSource > maxDisplayNameSource) {
						maxDisplayNameSource = nameSource;
					}
				}

				for (int i = 0; i < rawContactIds.length; i++) {
					c.moveToPosition(i);
					if (c.getLong(JoinContactQuery.CONTACT_ID) == contactId1) {
						int nameSource = c.getInt(JoinContactQuery.DISPLAY_NAME_SOURCE);
						if (nameSource == maxDisplayNameSource
								&& (verifiedNameRawContactId == -1)) {
							verifiedNameRawContactId = c.getLong(JoinContactQuery._ID);
						}
					}
				}
			} finally {
				c.close();
			}
		}
		else {
			return false;
		}

		// For each pair of raw contacts, insert an aggregation exception
		ArrayList<ContentProviderOperation> operations = new ArrayList<>();
		for (int i = 0; i < rawContactIds.length; i++) {
			for (int j = 0; j < rawContactIds.length; j++) {
				if (i != j) {
					ContentProviderOperation.Builder builder =
							ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI);
					builder.withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER);
					builder.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, rawContactIds[i]);
					builder.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, rawContactIds[j]);
					operations.add(builder.build());
				}
			}
		}

		//mark as SUPER PRIMARY
		if (verifiedNameRawContactId != -1) {
			operations.add(
					ContentProviderOperation.newUpdate(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, verifiedNameRawContactId))
							.withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
							.build());
		}

		boolean success = false;
		// split aggregation excemptions into chunks of 200 operations
		final int chunkSize = 200;
		int size = operations.size();

		for (int i = 0; i < size; i += chunkSize) {
			int end = Math.min(size, i + chunkSize) - 1;

			try {
				resolver.applyBatch(ContactsContract.AUTHORITY, new ArrayList<>(operations.subList(i, end)));
				success = true;
			} catch (RemoteException e) {
				logger.error("RemoteException: Failed to apply aggregation exception batch", e);
			} catch (OperationApplicationException e) {
				logger.error("OperationApplicationException: Failed to apply aggregation exception batch", e);
			}
		}
		return success;
	}

	public boolean joinThreemaAndroidContact(String identity, String androidContactLookupkey) {
		boolean success = false;

		String lookupKey = this.getRawContactLookupKeyByIdentity(identity);

		if(!TestUtil.empty(lookupKey)) {
			Long threemaContactId = this.getContactId(lookupKey);
			Long androidContactId = this.getContactId(androidContactLookupkey);
			if (TestUtil.required(threemaContactId, androidContactId)) {
				success = this.joinContacts(androidContactId, threemaContactId);
			}
		}
		return success;
	}

	public boolean splitThreemaAndroidContact(String identity, String androidContactLookupkey) {
		boolean success = false;
		Account account = this.getAccount();
		if(account == null) {
			return false;
		}

		String lookupKey = this.getRawContactLookupKeyByIdentity(identity);

		if(!TestUtil.empty(lookupKey)) {
			Long threemaContactId = this.getContactId(lookupKey);
			Long androidContactId = this.getContactId(androidContactLookupkey);

			//are the same... ok
			if(TestUtil.compare(threemaContactId, androidContactId)) {
				Cursor cursor = this.contentResolver.query(
						ContactsContract.RawContacts.CONTENT_URI,
						new String[]{
								ContactsContract.RawContacts._ID,
								ContactsContract.RawContacts.ACCOUNT_NAME,
								ContactsContract.RawContacts.ACCOUNT_TYPE,
								ContactsContract.RawContacts.SYNC1
						},
						ContactsContract.RawContacts.CONTACT_ID + "=?",
						new String[]{String.valueOf(threemaContactId)},
						null);

				if(cursor != null) {
					List<Long> rawContactIds = new ArrayList<>();
					List<Long> accountContactIds = new ArrayList<>();

					while(cursor.moveToNext()) {
						long id = cursor.getLong(0);
						String accountName = cursor.getString(1);
						String accountType = cursor.getString(2);
						String accountIdentity = cursor.getString(3);

						if(TestUtil.compare(accountName, account.name) &&
								TestUtil.compare(accountType, account.type) &&
								TestUtil.compare(accountIdentity, identity)) {
							accountContactIds.add(id);
						}
						else {
							rawContactIds.add(id);
						}
					}
					cursor.close();
					// For each pair of raw contacts, insert an aggregation exception
					ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();

					for (long joinId: rawContactIds) {
						for(long accountId: accountContactIds) {
							if (joinId != accountId) {
								try {
									ContentProviderOperation.Builder builder =
											ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI);
									builder.withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_SEPARATE);
									builder.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, joinId);
									builder.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, accountId);
									operations.add(builder.build());
								} catch (Exception e) {
									logger.error("Exception", e);
								}
								//reset name of threema account

							}
						}
					}

					try {
						this.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations);
						success = true;
					} catch (RemoteException | OperationApplicationException e) {
						logger.error("Exception", e);
					}

					//reset name to identity
//					this.updateNameOfThreemaContact(identity, identity, true);
				}
			}
		}

		return success;
	}

	public void startCache() {
		this.stopCache();
		this.identityLookupCache = new HashMap<String, String>();
	}

	public void stopCache() {
		if(this.identityLookupCache != null) {
			synchronized (this.identityLookupCacheLock) {
				this.identityLookupCache.clear();
				this.identityLookupCache = null;
			}
		}
	}

	public Drawable getAccountIcon(String identity) {
		Account myAccount = this.getAccount();
		if(myAccount == null) {
			return null;
		}

		String lookupKey = this.getRawContactLookupKeyByIdentity(identity);
		if(TestUtil.empty(lookupKey)) {
			return null;
		}

		AccountManager accountManager = AccountManager.get(ThreemaApplication.getAppContext());
		List<AuthenticatorDescription> descriptions = new ArrayList<AuthenticatorDescription>();

		//add google as first!
		for(AuthenticatorDescription des: accountManager.getAuthenticatorTypes()) {

			if("com.google".equals(des.type)) {
				//first
				descriptions.add(0, des);
			}
			else if(!TestUtil.compare(BuildConfig.APPLICATION_ID, des.type)) {
				descriptions.add(des);
			}

		}

		AuthenticatorDescription[] descriptionResult = new AuthenticatorDescription[descriptions.size()];
		Long contactId = this.getContactId(lookupKey);
		Drawable fallback = null;

		//select joined contact (excluding threema contact)
		if(contactId != null) {
			Cursor cursor = this.contentResolver.query(
					ContactsContract.RawContacts.CONTENT_URI,
					new String[]{
							ContactsContract.RawContacts.ACCOUNT_TYPE
					},
					ContactsContract.RawContacts.CONTACT_ID + " = ? AND "
					+ ContactsContract.RawContacts.ACCOUNT_TYPE + " != ?",
					new String[]{
							String.valueOf(contactId),
							BuildConfig.APPLICATION_ID
					},
					null
			);
			if(cursor != null) {

				while(cursor.moveToNext()) {
					String type = cursor.getString(0);

					for(int n = 0; n < descriptions.size(); n++) {
						AuthenticatorDescription description = descriptions.get(n);
						if (description.type.equals(type)) {
							descriptionResult[n] = description;
						}
					}
				}

				cursor.close();
			}
		}

		for(AuthenticatorDescription des: descriptionResult) {
			if(des != null) {
				PackageManager pm = ThreemaApplication.getAppContext().getPackageManager();
				Drawable drawable = pm.getDrawable(des.packageName, des.iconId, null);
				if(drawable != null) {
					if(des.type.equals(myAccount.type)) {
						fallback = drawable;
					}
					else {
						return drawable;
					}
				}
			}
		}

		//if no icon found, display the icon of the phone or contacts app
		if( fallback == null) {
			for (String namespace : new String[]{
				"com.android.phone",
				"com.android.providers.contacts"
			}) {
				try {
					fallback = ThreemaApplication.getAppContext().getPackageManager().getApplicationIcon(namespace);
					break;
				} catch (PackageManager.NameNotFoundException x) {
					//
				}
			}
		}
		return fallback;
	}

	private class ContactName {
		final String firstName;
		final String lastName;

		public ContactName(String firstName, String lastName) {
			this.firstName = firstName;
			this.lastName = lastName;
		}
	}

	public boolean updateAvatarByAndroidContact(ContactModel contactModel) {
		FileService fileService = getFileService();

		if (fileService == null) {
			return false;
		}

		String androidContactId = contactModel.getAndroidContactId();

		if(TestUtil.empty(androidContactId)) {
			return false;
		}

		Uri contactUri = ContactUtil.getAndroidContactUri(ThreemaApplication.getAppContext(), contactModel);
		if (contactUri == null) {
			return false;
		}

		Bitmap bitmap = null;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
			ContextCompat.checkSelfPermission(ThreemaApplication.getAppContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {

			bitmap = AvatarConverterUtil.convert(ThreemaApplication.getAppContext(), contactUri);
		}

		if (bitmap != null) {
			try {
				fileService.writeAndroidContactAvatar(contactModel, BitmapUtil.bitmapToByteArray(bitmap, Bitmap.CompressFormat.PNG, 100));
				contactModel.setAvatarExpires(new Date(System.currentTimeMillis() + DEFAULT_ANDROID_CONTACT_AVATAR_EXPIRY));
				return true;
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		} else {
			// delete old avatar
			boolean success = fileService.removeAndroidContactAvatar(contactModel);
			if (success) {
				contactModel.setAvatarExpires(new Date(System.currentTimeMillis() + DEFAULT_ANDROID_CONTACT_AVATAR_EXPIRY));
				return true;
			}
		}

		return false;
	}

	public boolean updateNameByAndroidContact(@NonNull ContactModel contactModel) {
		Uri namedContactUri = ContactUtil.getAndroidContactUri(ThreemaApplication.getAppContext(), contactModel);
		if(TestUtil.required(contactModel, namedContactUri)) {
			ContactName contactName = this.getContactName(namedContactUri);
			if(TestUtil.required(contactModel, contactName)) {
				if(!TestUtil.compare(contactModel.getFirstName(), contactName.firstName)
						|| !TestUtil.compare(contactModel.getLastName(), contactName.lastName)) {
					//changed... update
					contactModel.setFirstName(contactName.firstName);
					contactModel.setLastName(contactName.lastName);
					return true;
				}
			}
		}
		return false;
	}

	private ContactName getContactName(Uri contactUri) {
		if(!TestUtil.required(this.contentResolver)) {
			return null;
		}

		Cursor nameCursor = this.contentResolver.query(
				contactUri,
				NAME_PROJECTION,
				null,
				null,
				null);

		ContactName contactName = null;
		if(nameCursor != null) {
			if(nameCursor.moveToFirst()) {
				long contactId = nameCursor.getLong(nameCursor.getColumnIndex(ContactsContract.Contacts._ID));
				contactName = this.getContactNameFromId(contactId);

				// fallback
				if (contactName == null || (contactName.firstName == null && contactName.lastName == null)) {
					//lastname, firstname
					String alternativeSortKey = nameCursor.getString(nameCursor.getColumnIndex(ContactsContract.Contacts.SORT_KEY_ALTERNATIVE));

					if (!TestUtil.empty(alternativeSortKey)) {
						String[] lastNameFirstName = alternativeSortKey.split(",");
						if (lastNameFirstName != null && lastNameFirstName.length == 2) {
							String lastName = lastNameFirstName[0].trim();
							String firstName = lastNameFirstName[1].trim();

							if (!TestUtil.compare(lastName, "") && !TestUtil.compare(firstName, "")) {
								contactName = new ContactName(firstName, lastName);
							}
						}
					}
				}
			}
			nameCursor.close();
		}

		return contactName;
	}

	private ContactName getContactNameFromId(long contactId) {
		Map<String, String> structure = this.getStructuredNameByContactId(contactId);

		String firstName = structure.get(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
		String lastName = structure.get(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);

		String prefix = structure.get(ContactsContract.CommonDataKinds.StructuredName.PREFIX);
		String middleName = structure.get(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
		String suffix = structure.get(ContactsContract.CommonDataKinds.StructuredName.SUFFIX);

		String displayName = structure.get(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);

		StringBuilder contactFirstName = new StringBuilder();
		if (prefix != null && prefix.length() > 0) {
			contactFirstName.append(prefix);
		}
		if (firstName != null) {
			if (contactFirstName.length() > 0) {
				contactFirstName.append(" ");
			}
			contactFirstName.append(firstName);
		}
		if (middleName != null && middleName.length() > 0) {
			if (contactFirstName.length() > 0) {
				contactFirstName.append(' ');
			}
			contactFirstName.append(middleName);
		}

		StringBuilder contactLastName = new StringBuilder();
		if (lastName != null) {
			contactLastName.append(lastName);
		}
		if (suffix != null && suffix.length() > 0) {
			if (contactLastName.length() > 0) {
				contactLastName.append(", ");
			}
			contactLastName.append(suffix);
		}

		/* Only use this structured name if we have a first or last name. Otherwise use display name (below) */
		if (contactFirstName.length() > 0 || contactLastName.length() > 0) {
			return new ContactName(contactFirstName.toString(), contactLastName.toString());
		}

		final Pair<String, String> firstLastName = getFirstLastNameFromDisplayName(displayName);
		return new ContactName(firstLastName.first, firstLastName.second);
	}

	/**
	 * Extract first and last name from display name.
	 *
	 * If the displayName is empty or null, then empty strings will be returned for first/last name.
	 */
	private static @NonNull Pair<String, String> getFirstLastNameFromDisplayName(@Nullable String displayName) {
		final String[] parts = displayName == null ? null : displayName.split(" ");
		if (parts == null || parts.length == 0) {
			return new Pair<>("", "");
		}
		final String firstName = parts[0];
		final String lastName = J8Arrays.stream(parts)
			.skip(1)
			.collect(Collectors.joining(" "));
		return new Pair<>(firstName, lastName);
	}

	private  Map<String, String> getStructuredNameByContactId(long id) {
		Map<String, String> structuredName = new TreeMap<String, String>();

		Cursor cursor = this.contentResolver.query(
				ContactsContract.Data.CONTENT_URI,
				STRUCTURED_NAME_FIELDS,
				ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
				new String[] {String.valueOf(id), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE},
				null);

		if(cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					for (int i = 0; i < STRUCTURED_NAME_FIELDS.length; i++) {
						structuredName.put(STRUCTURED_NAME_FIELDS[i], cursor.getString(i));
					}
				}
			}
			finally {
				cursor.close();
			}
		}
		return structuredName;
	}

	/**
	 * Create a raw contact for the given identity. Put the identity into the SYNC1 column and set DISPLAY_NAME and data records for messaging and calling
	 * @param identity
	 * @param displayName
	 * @param supportsVoiceCalls
	 * @return LOOKUP_KEY of the newly created raw contact or null if no contact has been created
	 */
	public String createThreemaAndroidContact(String identity, String displayName, boolean supportsVoiceCalls) {
		Context context = ThreemaApplication.getAppContext();
		Account account = this.getAccount();
		if (!TestUtil.required(account, identity)) {
			//do nothing
			return null;
		}

		String threemaContactLookupKey = this.getRawContactLookupKeyByIdentity(identity);

		//alread exist
		if(!TestUtil.empty(threemaContactLookupKey)) {
			return threemaContactLookupKey;
		}

		ArrayList<ContentProviderOperation> insertOperationList = new ArrayList<ContentProviderOperation>();

		logger.debug("Adding contact: " + identity);

		logger.debug("   Create our RawContact");
		ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
		builder.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name);
		builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type);
		builder.withValue(ContactsContract.RawContacts.SYNC1, identity);
		insertOperationList.add(builder.build());

		logger.debug("   Create a Data record of type 'Nickname' for our RawContact");
		builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
		builder.withValueBackReference(ContactsContract.CommonDataKinds.Nickname.RAW_CONTACT_ID, 0);
		builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);
		builder.withValue(ContactsContract.CommonDataKinds.Nickname.NAME, identity);
		insertOperationList.add(builder.build());

		logger.debug("   Create a Data record of custom type");
		builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
		builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
		builder.withValue(ContactsContract.Data.MIMETYPE, context.getString(R.string.contacts_mime_type));
		//DATA1 have to be the identity to fetch in the activity
		builder.withValue(ContactsContract.Data.DATA1, identity);
		builder.withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name));
		builder.withValue(ContactsContract.Data.DATA3, context.getString(R.string.threema_message_to, identity));
		insertOperationList.add(builder.build());

		if (supportsVoiceCalls) {
			logger.debug("   Create a Data record of custom type for call");
			builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
			builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
			builder.withValue(ContactsContract.Data.MIMETYPE, context.getString(R.string.call_mime_type));
			builder.withValue(ContactsContract.Data.DATA1, identity);
			builder.withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name));
			builder.withValue(ContactsContract.Data.DATA3, context.getString(R.string.threema_call_with, identity));
			insertOperationList.add(builder.build());
		}

		try {
			context.getContentResolver().applyBatch(
					ContactsContract.AUTHORITY,
					insertOperationList);

			//get the created or updated contact id
			return this.getRawContactLookupKeyByIdentity(identity);
		} catch (Exception e) {
			logger.error("Error during raw contact creation! ", e);
		}

		return null;
	}


	/**
	 * Check if a raw contact exists where the given identity matches the entry in the contact's SYNC1 column
	 * @param identity Threema identity to look for
	 * @return LOOKUP_KEY of the matching Raw Contact or null if none is found
	 */
	public String getRawContactLookupKeyByIdentity(String identity) {
		Account account = this.getAccount();
		if (account == null) {
			//do nothing
			return null;
		}

		if(this.identityLookupCache != null) {
			synchronized (this.identityLookupCacheLock) {
				String res = this.identityLookupCache.get(identity);
				if(res != null) {
					return res;
				}
			}
		}

		String lookupKey = null;

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
				ContextCompat.checkSelfPermission(ThreemaApplication.getAppContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
			//get linked contact!
			Cursor c1 = null;
			try {
				c1 = ThreemaApplication.getAppContext().getContentResolver().query(
					ContactsContract.Data.CONTENT_URI,
					LOOKUP_KEY_PROJECTION,
					ContactsContract.RawContacts.ACCOUNT_NAME + "=? and "
						+ ContactsContract.RawContacts.ACCOUNT_TYPE + "=? and "
						+ ContactsContract.RawContacts.SYNC1 + "=?", new String[]{
						String.valueOf(account.name),
						String.valueOf(account.type),
						String.valueOf(identity),
					},
					null);

				if (c1 != null) {
					if (c1.moveToFirst()) {
						lookupKey = c1.getString(0);
					}
				}
			} catch (Exception e) {
				// JollaPhone crashes within this query
				logger.error("Exception", e);
			} finally {
				if (c1 != null) {
					c1.close();
				}
			}

			if (this.identityLookupCache != null) {
				synchronized (this.identityLookupCacheLock) {
					this.identityLookupCache.put(identity, lookupKey);
				}
			}
		}
		return lookupKey;
	}

	/**
	 * Delete the raw contact where the given identity matches the entry in the contact's SYNC1 column
	 * @param identity Threema identity to look for
	 */
	public void deleteRawContactByIdentity(String identity) {
		Account account = this.getAccount();
		if (account == null) {
			//do nothing
			return;
		}

		Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI
			.buildUpon()
			.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
			.appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
			.appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type).build();

		try {
			contentResolver.delete(rawContactUri,
				ContactsContract.RawContacts.SYNC1 + " = ?", new String[]{
					identity
				});
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	public boolean isAndroidContactNameMaster(ContactModel contactModel) {
		if(contactModel != null) {
			return !TestUtil.empty(contactModel.getAndroidContactId());
		}

		return false;
	}
}

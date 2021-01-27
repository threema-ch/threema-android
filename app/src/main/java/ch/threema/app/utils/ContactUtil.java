/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.PreferenceService;
import ch.threema.storage.models.ContactModel;

public class ContactUtil {
	private static final Logger logger = LoggerFactory.getLogger(ContactUtil.class);

	public static final int CHANNEL_NAME_MAX_LENGTH_BYTES = 256;

	/**
	 * return a valid uri to the given contact
	 *
	 * @param context context
	 * @param contactModel contactmodel
	 * @return null or a valid uri
	 */
	public static Uri getAndroidContactUri(Context context, ContactModel contactModel) {
		if (contactModel != null) {
			String contactLookupKey = contactModel.getAndroidContactId();

			if (TestUtil.empty(contactLookupKey)) {
				contactLookupKey = contactModel.getThreemaAndroidContactId();
			}

			if (!TestUtil.empty(contactLookupKey)) {
				return getAndroidContactUri(context, contactLookupKey);
			}
		}
		return null;
	}

	private static Uri getAndroidContactUri(Context context, String lookupKey) {
		Uri contactLookupUri = null;

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
				ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
			contactLookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);

			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
				if (contactLookupUri != null) {
					// Some implementations of QuickContactActivity/Contact App Intents ( (e.g. 4.0.4 on Samsung or HTC Android 4-5) have troubles dealing
					// with CONTENT_LOOKUP_URI style URIs - they crash with a NumberFormatException. To avoid this,
					// we need to lookup the proper (number-only) contact ID.
					try {
						contactLookupUri = ContactsContract.Contacts.lookupContact(context.getContentResolver(), contactLookupUri);
					} catch (Exception e) {
						// Could be an old (non-lookup) URI - ignore
						logger.error("Exception", e);
					}
				}
			}
		}

		return contactLookupUri;
	}

	/**
	 * check if this contact is *currently* linked to an android contact
	 * @param contact
	 * @return
	 */
	public static boolean isLinked(ContactModel contact) {
		return contact != null
				&& !TestUtil.empty(contact.getAndroidContactId());
	}

	/**
	 * @param contact
	 * @return
	 */
	public static boolean canChangeFirstName(ContactModel contact) {
		return contact != null
				&& !isLinked(contact);
	}

	/**
	 * @param contact
	 * @return
	 */
	public static boolean canChangeLastName(ContactModel contact) {
		return contact != null
				&& !isLinked(contact)
				&& !isChannelContact(contact);
	}

	public static boolean canChangeAvatar(ContactModel contactModel, PreferenceService preferenceService, FileService fileService) {
		return canHaveCustomAvatar(contactModel)
				&& !(preferenceService.getProfilePicReceive() && fileService.hasContactPhotoFile(contactModel));
	}
	/**
	 * check if this contact was added during a synchronization run.
	 * note that the contact may no longer be linked to a system contact
	 * @param contact
	 * @return
	 */
	public static boolean isSynchronized(ContactModel contact) {
		return contact != null && contact.isSynchronized();
	}

	public static Uri getLinkedUri(Context context, ContactService contactService, ContactModel contact) {
		contactService.validateContactAggregation(contact, true);
		final Uri contactUri = ContactUtil.getAndroidContactUri(context, contact);

		if (TestUtil.required(contactUri) && TestUtil.required(contact)) {
			if (AndroidContactUtil.getInstance().isAndroidContactNameMaster(contact)) {
				return contactUri;
			}
		}
		return null;
	}

	/**
	 * return true on channel-type contact (i.e. gateway, threema broadcast)
	 *
	 * @param contactModel
	 * @return if channel contact
	 */
	public static boolean isChannelContact(ContactModel contactModel) {
		if(contactModel != null) {
			return isChannelContact(contactModel.getIdentity());
		}
		return false;
	}

	/**
	 * return true on channel-type contact (i.e. gateway, threema broadcast)
	 *
	 * @param identity
	 * @return if channel contact
	 */
	public static boolean isChannelContact(String identity) {
		return identity != null && identity.startsWith("*");
	}

	public static boolean canReceiveProfilePics(ContactModel contactModel) {
		return contactModel != null
				&& !isChannelContact(contactModel)
				&& !contactModel.getIdentity().equals(ThreemaApplication.ECHO_USER_IDENTITY);
	}

	public static boolean canReceiveVoipMessages(ContactModel contactModel, IdListService blackListIdentityService) {
		return contactModel != null
				&& blackListIdentityService != null
				&& !blackListIdentityService.has(contactModel.getIdentity())
				&& !isChannelContact(contactModel)
				&& !contactModel.getIdentity().equals(ThreemaApplication.ECHO_USER_IDENTITY);
	}

	public static boolean allowedChangeToState(ContactModel contactModel, ContactModel.State newState) {
		if(contactModel != null && contactModel.getState() != newState) {
			ContactModel.State oldState = contactModel.getState();

			switch (newState) {
				//never change to temporary
				case TEMPORARY:
					return false;
				//change to active is always allowed
				case ACTIVE:
					return true;
				case INACTIVE:
					return oldState == ContactModel.State.TEMPORARY
							|| oldState == ContactModel.State.ACTIVE;
				case INVALID:
					return true;
			}
		}
		return false;
	}

	/**
	 *
	 * @param contact
	 * @return
	 */
	public static boolean canHaveCustomAvatar(ContactModel contact) {
		return contact != null
				&& !isLinked(contact)
				&& !isChannelContact(contact);
	}

	/**
	 * check if the avatar is expired (or no date set)
	 *
	 * @param contactModel
	 * @return
	 */
	public static boolean isAvatarExpired(ContactModel contactModel) {
		return contactModel != null
				&& (
					contactModel.getAvatarExpires() == null
					|| contactModel.getAvatarExpires().before(new Date())
			);
	}

	/**
	 * returns a representation of the contact's name according to sort settings,
	 * suitable for comparing
	 */
	public static String getSafeNameString(ContactModel c, PreferenceService preferenceService) {
		if (preferenceService.isContactListSortingFirstName()) {
			return (c.getFirstName() != null ? c.getFirstName() : "") +
					(c.getLastName() != null ? c.getLastName() : "") +
					(c.getPublicNickName() != null ? c.getPublicNickName() : "") +
					c.getIdentity();
		}
		else {
			return (c.getLastName() != null ? c.getLastName() : "") +
					(c.getFirstName() != null ? c.getFirstName() : "") +
					(c.getPublicNickName() != null ? c.getPublicNickName() : "") +
					c.getIdentity();
		}
	}

	public static String getSafeNameStringNoNickname(ContactModel c, PreferenceService preferenceService) {
		if (preferenceService.isContactListSortingFirstName()) {
			return (c.getFirstName() != null ? c.getFirstName() : "") +
					(c.getLastName() != null ? c.getLastName() : "") +
					c.getIdentity();
		}
		else {
			return (c.getLastName() != null ? c.getLastName() : "") +
					(c.getFirstName() != null ? c.getFirstName() : "") +
					c.getIdentity();
		}
	}

	public static @DrawableRes int getVerificationResource(ContactModel contactModel) {
		int iconResource = R.drawable.ic_verification_none;
		if(contactModel != null) {
			switch (contactModel.getVerificationLevel()) {
				case SERVER_VERIFIED:
					if (ConfigUtils.isWorkBuild() && contactModel.isWork()) {
						iconResource = R.drawable.ic_verification_server_work;
					} else {
						iconResource = R.drawable.ic_verification_server;
					}
					break;
				case FULLY_VERIFIED:
					if (ConfigUtils.isWorkBuild() && contactModel.isWork()) {
						iconResource = R.drawable.ic_verification_full_work;
					} else {
						iconResource = R.drawable.ic_verification_full;
					}
					break;
			}
		}
		return iconResource;
	}

	public static Drawable getVerificationDrawable(Context context, ContactModel contactModel) {
		if (context != null) {
			return AppCompatResources.getDrawable(context, getVerificationResource(contactModel));
		}
		return null;
	}

	public static String getIdentityFromViewIntent(Context context, Intent intent) {
		if (Intent.ACTION_VIEW.equals(intent.getAction()) && context.getString(R.string.contacts_mime_type).equals(intent.getType())) {
			Cursor cursor = null;
			try {
				cursor = context.getContentResolver().query(intent.getData(), null, null, null, null);
				if (cursor != null) {
					if (cursor.moveToNext()) {
						return cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DATA1));
					}
				}
			} catch (Exception e) {
				logger.error("Exception", e);
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		}
		return null;
	}
}

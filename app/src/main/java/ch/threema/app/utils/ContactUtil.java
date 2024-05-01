/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.text.Collator;
import java.util.Comparator;
import java.util.Date;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.util.Pair;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.FileService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.PreferenceService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;

public class ContactUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ContactUtil");

	public static final int CHANNEL_NAME_MAX_LENGTH_BYTES = 256;

	public static final long PROFILE_PICTURE_BLOB_CACHE_DURATION = DateUtils.WEEK_IN_MILLIS;

	/**
	 * check if this contact is *currently* linked to an android contact
	 * @param contact
	 * @return
	 */
	public static boolean isLinked(ContactModel contact) {
		return contact != null
				&& !TestUtil.empty(contact.getAndroidContactLookupKey());
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

	/**
	 * Checks whether the contact's id is ECHOECHO or a Channel ID
	 * @return {@code true} if the contact is ECHOECHO or a channel ID, {@code false} otherwise
	 */
	public static boolean isEchoEchoOrChannelContact(ContactModel contactModel) {
		return contactModel != null
			&& (isChannelContact(contactModel)
			|| ThreemaApplication.ECHO_USER_IDENTITY.equals(contactModel.getIdentity())
		);
	}

	public static boolean canReceiveVoipMessages(ContactModel contactModel, IdListService blackListIdentityService) {
		return contactModel != null
				&& blackListIdentityService != null
				&& !blackListIdentityService.has(contactModel.getIdentity())
				&& !isEchoEchoOrChannelContact(contactModel);
	}

	public static boolean allowedChangeToState(
		@Nullable ContactModel contactModel,
		@Nullable ContactModel.State newState
	) {
		if(contactModel != null && newState != null && contactModel.getState() != newState) {
			ContactModel.State oldState = contactModel.getState();

			switch (newState) {
				//change to active is always allowed
				case ACTIVE:
					return true;
				case INACTIVE:
					return oldState == ContactModel.State.ACTIVE;
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
	public static String getSafeNameString(ContactModel contactModel, boolean sortOrderFirstName) {
		String key = contactModel.getIdentity();
		String firstName = contactModel.getFirstName();
		String lastName = contactModel.getLastName();

		if (TestUtil.empty(firstName) && TestUtil.empty(lastName) && !TestUtil.empty(contactModel.getPublicNickName())) {
			Pair<String, String> namePair = NameUtil.getFirstLastNameFromDisplayName(contactModel.getPublicNickName().trim());
			firstName = namePair.first;
			lastName = namePair.second;
		}

		if (sortOrderFirstName) {
			if (!TextUtils.isEmpty(lastName)) {
				key = lastName + key;
			}
			if (!TextUtils.isEmpty(firstName)) {
				key = firstName + key;
			}
		} else {
			if (!TextUtils.isEmpty(firstName)) {
				key = firstName + key;
			}
			if (!TextUtils.isEmpty(lastName)) {
				key = lastName + key;
			}
		}
		return key;
	}

	public static @NonNull Comparator<ContactModel> getContactComparator(boolean sortOrderFirstName) {
		final Collator collator = Collator.getInstance();
		collator.setStrength(Collator.PRIMARY);
		return (contact1, contact2) -> collator.compare(
			getSortKey(contact1, sortOrderFirstName), getSortKey(contact2, sortOrderFirstName)
		);
	}

	private static @NonNull String getSortKey(ContactModel contactModel, boolean sortOrderFirstName) {
		String key = ContactUtil.getSafeNameString(contactModel, sortOrderFirstName);

		if (contactModel.getIdentity().startsWith("*")) {
			key = "\uFFFF" + key;
		}
		return key;
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

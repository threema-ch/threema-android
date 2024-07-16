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
import android.icu.text.ListFormatter;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.text.Collator;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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

/**
 * Static utility functions related to contacts.
 *
 * TODO(ANDR-2985): Most of these could be contact model methods.
 */
public class ContactUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ContactUtil");

	public static final int CHANNEL_NAME_MAX_LENGTH_BYTES = 256;

	public static final long PROFILE_PICTURE_BLOB_CACHE_DURATION = DateUtils.WEEK_IN_MILLIS;

	public static boolean canChangeFirstName(@Nullable ContactModel contact) {
		return contact != null && !contact.isLinkedToAndroidContact();
	}

	public static boolean canChangeLastName(@Nullable ContactModel contact) {
		return contact != null
				&& !contact.isLinkedToAndroidContact()
				&& !isGatewayContact(contact);
	}

	public static boolean canChangeAvatar(
		@NonNull ContactModel contactModel,
		@NonNull PreferenceService preferenceService,
		@NonNull FileService fileService
	) {
		return canHaveCustomAvatar(contactModel)
				&& !(preferenceService.getProfilePicReceive() && fileService.hasContactPhotoFile(contactModel.getIdentity()));
	}

	/**
	 * Return whether this {@param contactModel} is a Threema Gateway contact.
	 */
	public static boolean isGatewayContact(ContactModel contactModel) {
		if(contactModel != null) {
			return isGatewayContact(contactModel.getIdentity());
		}
		return false;
	}

	/**
	 * Return whether this {@param identity} is a Threema Gateway contact (starting with "*").
	 */
	public static boolean isGatewayContact(@NonNull String identity) {
		return identity.startsWith("*");
	}

	/**
	 * Return whether this {@param contactModel} is a Threema Gateway contact
	 * or the special contact "ECHOECHO".
	 */
	public static boolean isEchoEchoOrGatewayContact(ContactModel contactModel) {
		return contactModel != null
			&& (isGatewayContact(contactModel.getIdentity())
				|| ThreemaApplication.ECHO_USER_IDENTITY.equals(contactModel.getIdentity())
		);
	}

	/**
	 * Return whether this {@param identity} is a Threema Gateway contact (starting with "*")
	 * or the special contact "ECHOECHO".
	 */
	public static boolean isEchoEchoOrGatewayContact(@NonNull String identity) {
		return isGatewayContact(identity)
			|| ThreemaApplication.ECHO_USER_IDENTITY.equals(identity);
	}

	public static boolean canReceiveVoipMessages(ContactModel contactModel, IdListService blackListIdentityService) {
		return contactModel != null
				&& blackListIdentityService != null
				&& !blackListIdentityService.has(contactModel.getIdentity())
				&& !isEchoEchoOrGatewayContact(contactModel);
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

	public static boolean canHaveCustomAvatar(@Nullable ContactModel contact) {
		return contact != null
				&& !contact.isLinkedToAndroidContact()
				&& !isGatewayContact(contact);
	}

	/**
	 * check if the avatar is expired (or no date set)
	 */
	public static boolean isAvatarExpired(ContactModel contactModel) {
		return contactModel != null
				&& (
					contactModel.getLocalAvatarExpires() == null
					|| contactModel.getLocalAvatarExpires().before(new Date())
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
			switch (contactModel.verificationLevel) {
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

	public static String joinDisplayNames(@Nullable Context context, @Nullable List<ContactModel> contacts) {
		if (contacts == null) {
			return "";
		}

		List<String> contactNames = contacts.stream()
			.map(contact -> NameUtil.getDisplayNameOrNickname(contact, true))
			.collect(Collectors.toList());

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context != null) {
			return ListFormatter.getInstance(LocaleUtil.getCurrentLocale(context)).format(contactNames);
		} else {
			return String.join(", ", contactNames);
		}
	}
}

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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.FileService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.tasks.OnFSFeatureMaskDowngradedTask;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base32;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.models.WorkVerificationLevel;
import ch.threema.storage.models.ContactModel;

/**
 * Static utility functions related to contacts.
 *
 * TODO(ANDR-2985): Most of these could be contact model methods.
 */
public class ContactUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ContactUtil");

	private static final String CONTACT_UID_PREFIX = "c-";

	public static final int CHANNEL_NAME_MAX_LENGTH_BYTES = 256;

	public static final long PROFILE_PICTURE_BLOB_CACHE_DURATION = DateUtils.WEEK_IN_MILLIS;

	@Deprecated
	public static int getUniqueId(@Nullable String identity) {
		if (identity == null) {
			return 0;
		}
		return (CONTACT_UID_PREFIX + identity).hashCode();
	}

	@NonNull
	public static String getUniqueIdString(@Nullable String identity) {
		if (identity != null) {
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
				messageDigest.update((CONTACT_UID_PREFIX + identity).getBytes());
				return Base32.encode(messageDigest.digest());
			} catch (NoSuchAlgorithmException e) {
				logger.warn("Could not calculate unique id string");
			}
		}
		return "";
	}

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
				&& !(preferenceService.getProfilePicReceive() && fileService.hasContactDefinedProfilePicture(contactModel.getIdentity()));
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

	public static boolean canReceiveVoipMessages(ContactModel contactModel, IdListService blockedContactsService) {
		return contactModel != null
				&& blockedContactsService != null
				&& !blockedContactsService.has(contactModel.getIdentity())
				&& !isEchoEchoOrGatewayContact(contactModel);
	}

	public static boolean canReceiveVoipMessages(@Nullable String identity, @Nullable IdListService blockedContactsService) {
		return identity != null
			&& blockedContactsService != null
			&& !blockedContactsService.has(identity)
			&& !isEchoEchoOrGatewayContact(identity);
	}

	public static boolean allowedChangeToState(
		@Nullable IdentityState oldState,
		@Nullable IdentityState newState
	) {
		if (oldState == newState || newState == null) {
			return false;
		}
		switch (newState) {
			//change to active is always allowed
			case ACTIVE:
				return true;
			case INACTIVE:
				return oldState == IdentityState.ACTIVE;
			case INVALID:
				return true;
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

		if (TestUtil.isEmptyOrNull(firstName) && TestUtil.isEmptyOrNull(lastName) && !TestUtil.isEmptyOrNull(contactModel.getPublicNickName())) {
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

	public static @DrawableRes int getVerificationResource(
		@NonNull VerificationLevel verificationLevel,
		@NonNull WorkVerificationLevel workVerificationLevel
	) {
		int iconResource;
		boolean isWorkVerifiedOnWorkBuild = ConfigUtils.isWorkBuild()
			&& workVerificationLevel == WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED;
		switch (verificationLevel) {
			case SERVER_VERIFIED:
				if (isWorkVerifiedOnWorkBuild) {
					iconResource = R.drawable.ic_verification_server_work;
				} else {
					iconResource = R.drawable.ic_verification_server;
				}
				break;
			case FULLY_VERIFIED:
				if (isWorkVerifiedOnWorkBuild) {
					iconResource = R.drawable.ic_verification_full_work;
				} else {
					iconResource = R.drawable.ic_verification_full;
				}
				break;
			case UNVERIFIED:
			default:
				iconResource = R.drawable.ic_verification_none;
				break;
		}
		return iconResource;
	}

	@Nullable
	public static Drawable getVerificationDrawable(
		@NonNull Context context,
		@NonNull VerificationLevel verificationLevel,
		@NonNull WorkVerificationLevel workVerificationLevel
	) {
		return AppCompatResources.getDrawable(context, getVerificationResource(
			verificationLevel,
			workVerificationLevel
		));
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

	/**
	 * Perform the required steps if a contact does not support forward security anymore due to a
	 * change of its feature mask. This includes creating a status message in the conversation with
	 * that contact to warn the user that forward security has been disabled for this contact. This
	 * method also terminates all existing sessions with the contact.
	 * <p>
	 * Note that the status message is only created if a forward security session currently exists.
	 * <p>
	 * Note that this method must only be called if the feature mask of a contact is changed from a
	 * feature mask that indicates forward security support to a feature mask without forward
	 * security support.
	 *
	 * @param contactModel the affected contact
	 */
	public static void onForwardSecurityNotSupportedAnymore(@NonNull ch.threema.data.models.ContactModel contactModel) {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			logger.error("Service manager is null");
			return;
		}

		try {
			serviceManager.getTaskManager().schedule(
				new OnFSFeatureMaskDowngradedTask(
					contactModel,
					serviceManager.getContactService(),
					serviceManager.getMessageService(),
					serviceManager.getDHSessionStore(),
					serviceManager.getIdentityStore(),
					serviceManager.getForwardSecurityMessageProcessor()
				)
			);
		} catch (ThreemaException e) {
			logger.error("Could not schedule fs feature mask downgraded task");
		}
	}
}

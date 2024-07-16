/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.FilterableListAdapter;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import java8.util.J8Arrays;
import java8.util.stream.Collectors;

public class NameUtil {

	private static PreferenceService preferenceService;

	private NameUtil(PreferenceStore preferenceStore) {
	}

	private static PreferenceService getPreferenceService() {
		if (NameUtil.preferenceService == null) {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager != null) {
				NameUtil.preferenceService = serviceManager.getPreferenceService();
			}
		}

		return NameUtil.preferenceService;
	}

	/**
	 * Return the display name for a group.
	 */
	public static String getDisplayName(GroupModel groupModel, GroupService groupService) {
		if (groupModel.getName() != null && groupModel.getName().length() > 0) {
			return groupModel.getName();
		}

		//list members
		StringBuilder name = new StringBuilder();
		for (ContactModel contactModel : groupService.getMembers(groupModel)) {
			if (name.length() > 0) {
				name.append(", ");
			}
			name.append(NameUtil.getDisplayName(contactModel));
		}

		if (name.length() > 0) {
			return name.toString();
		}

		return groupModel.getApiGroupId().toString();
	}

	/**
	 * Return the display name for a distribution list.
	 */
	public static String getDisplayName(DistributionListModel distributionListModel, DistributionListService distributionListService) {
		if (!TestUtil.empty(distributionListModel.getName()) || distributionListService == null) {
			return distributionListModel.getName();
		}

		StringBuilder name = new StringBuilder();
		for (ContactModel contactModel : distributionListService.getMembers(distributionListModel)) {
			if (name.length() > 0) {
				name.append(", ");
			}
			name.append(NameUtil.getDisplayName(contactModel));
		}

		if (name.length() > 0) {
			return name.toString();
		}

		return String.valueOf(distributionListModel.getId());
	}

	public static String getDisplayNameOrNickname(Context context, AbstractMessageModel messageModel, ContactService contactService) {
		if (TestUtil.required(context, messageModel)) {
			ContactModel model;

			if (messageModel.isOutbox()) {
				model = contactService.getMe();
			} else {
				model = contactService.getByIdentity(messageModel.getIdentity());
			}

			return getDisplayNameOrNickname(model, true);
		}
		return null;
	}

	public static String getShortName(String identity, ContactService contactService) {
		String shortname = null;

		if (identity.equals(ContactService.ALL_USERS_PLACEHOLDER_ID)) {
			return ThreemaApplication.getAppContext().getString(R.string.all);
		}

		if (contactService != null) {
			shortname = NameUtil.getShortName(contactService.getByIdentity(identity));
		}
		return shortname != null ? shortname : identity;
	}

	public static String getShortName(ContactModel model) {
		if (model != null) {
			if (TestUtil.empty(model.getFirstName())) {
				if (TestUtil.empty(model.getLastName())) {
					return getFallbackName(model);
				} else {
					return getDisplayName(model);
				}
			} else {
				return model.getFirstName();
			}
		}
		return null;
	}

	public static String getShortName(Context context, AbstractMessageModel messageModel, ContactService contactService) {
		if (TestUtil.required(context, messageModel)) {
			if (messageModel.isOutbox()) {
				return context.getString(R.string.me_myself_and_i);
			} else {
				return getShortName(contactService.getByIdentity(messageModel.getIdentity()));
			}
		}
		return null;
	}

	private static String getFallbackName(ContactModel model) {
		if (!TestUtil.empty(model.getPublicNickName()) &&
				!model.getPublicNickName().equals(model.getIdentity())) {
			return "~" + model.getPublicNickName();
		} else {
			return model.getIdentity();
		}
	}

	/**
	 * Return the display name for a contact.
	 */
	@NonNull
	public static String getDisplayName(ContactModel contactModel) {
		String c = "";

		if (contactModel == null) {
			return "undefined";
		}

		if (contactModel.getIdentity().isEmpty()) {
			return "invalid contact";
		}

		String f = contactModel.getFirstName();
		String l = contactModel.getLastName();

		if (TestUtil.empty(f, l)) {
			return contactModel.getIdentity();
		}

		PreferenceService preferenceService = NameUtil.getPreferenceService();
		if (preferenceService == null || preferenceService.isContactFormatFirstNameLastName()) {
			if (f != null) {
				c += f + " ";
			}

			if (l != null) {
				c += l;
			}
		} else {
			if (l != null) {
				c += l + " ";
			}

			if (f != null) {
				c += f;
			}
		}

		if (TestUtil.empty(c)) {
			c = contactModel.getIdentity();
		}

		return c.trim();
	}

	/**
	 * Return the display name for a contact, or fall back to the nickname.
	 */
	@NonNull
	public static String getDisplayNameOrNickname(@Nullable ContactModel contactModel, boolean withPrefix) {
		if (contactModel == null) return "";

		String displayName = NameUtil.getDisplayName(contactModel);
		String nickName = contactModel.getPublicNickName();

		if (
				displayName.equals(contactModel.getIdentity()) &&
				nickName != null &&
				!nickName.isEmpty() &&
				!displayName.equals(nickName)) {
			return withPrefix ? "~" + nickName : nickName;
		} else {
			return displayName;
		}
	}

	@NonNull
	public static String getDisplayNameOrNickname(String identity, ContactService contactService) {
		if (contactService == null) {
			return "";
		}
		String displayName = NameUtil.getDisplayNameOrNickname(contactService.getByIdentity(identity), true);
		return TextUtils.isEmpty(displayName) ? identity : displayName.substring(0, Math.min(displayName.length(), 24));
	}

	/**
	 * Return the name used for quotes and mentions.
	 */
	@NonNull
	public static String getQuoteName(@Nullable ContactModel contactModel, @NonNull UserService userService) {
		if (contactModel == null) {
			return "";
		}

		// If the contact is the local user, and the nickname does not equal the identity,
		// return the nickname.
		if (userService.isMe(contactModel.getIdentity())) {
			final String myNickname = userService.getPublicNickname();
			if (!TestUtil.empty(myNickname) && !myNickname.equals(userService.getIdentity())) {
				return myNickname;
			}
		}

		return getDisplayNameOrNickname(contactModel, true);
	}

	/**
	 * Return the name used for quotes and mentions. If the contact is not known or an error occurs
	 * while getting the quote name, the identity is returned if not null. Otherwise an empty string
	 * is returned.
	 */
	@NonNull
	public static String getQuoteName(@Nullable String identity, ContactService contactService, UserService userService) {
		if (contactService == null || userService == null || identity == null) {
			if (identity != null) {
				return identity;
			} else {
				return "";
			}
		}

		if (ContactService.ALL_USERS_PLACEHOLDER_ID.equals(identity)) {
			return ThreemaApplication.getAppContext().getString(R.string.all);
		}

		final ContactModel contactModel = contactService.getByIdentity(identity);
		String quoteName = getQuoteName(contactModel, userService);
		if (quoteName.isBlank()) {
			return identity;
		} else {
			return quoteName;
		}
	}

	public static void showNicknameInView(TextView nickNameTextView, ContactModel contactModel, String filterString, FilterableListAdapter adapter) {
		if (nickNameTextView != null) {
			if (contactModel != null) {
				String nickname = contactModel.getPublicNickName();

				if (!TestUtil.empty(nickname) && !nickname.equals(contactModel.getIdentity())) {
					String text = "~" + nickname;

					if (filterString != null && adapter != null) {
						nickNameTextView.setText(adapter.highlightMatches(text, filterString));
					}
					else {
						nickNameTextView.setText(text);
					}
					nickNameTextView.setVisibility(View.VISIBLE);
					return;
				}
			}

			nickNameTextView.setVisibility(View.GONE);
		}
	}

	/**
	 * Extract first and last name from display name as provided by the Android contact database
	 * If displayName is empty or null, empty strings will be returned for first/last name.
	 *
	 * @param displayName Name of the contact to split
	 * @return A Pair containing first and last name
	 */
	@NonNull public static Pair<String, String> getFirstLastNameFromDisplayName(@Nullable String displayName) {
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
}

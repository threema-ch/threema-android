/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

import java.util.HashMap;
import java.util.Map;

import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

public class IdUtil {

	private static final Map<String, Integer> contactIds = new HashMap<>();
	private static final String KEY_CONTACT = "c-";
	private static final String KEY_GROUP = "g-";

	/**
	 * Return a unique integer for the specified key.
	 *
	 * The function always returns the same value for the same key as long as the app is
	 * running. After an app restart (when the memory is cleared), a new value will be generated.
	 *
	 * Currently the function is implemented with a sequential positive integer, so the first
	 * contact will get the number 1, the second contact will get the number 2, and so on.
	 */
	private static int getTempId(String key) {
		synchronized (contactIds) {
			if (!contactIds.containsKey(key)) {
				contactIds.put(key, contactIds.size() + 1);
			}
			return contactIds.get(key);
		}
	}

	/**
	 * Return a unique integer for the specified contact.
	 *
	 * The function always returns the same value for the same contact as long as the app is
	 * running. After an app restart (when the memory is cleared), a new value will be generated.
	 *
	 * Currently the function is implemented with a sequential positive integer, so the first
	 * contact will get the number 1, the second contact will get the number 2, and so on.
	 */
	public static int getTempId(ContactModel contact) {
		return getTempId(KEY_CONTACT + contact.getIdentity());
	}

	/**
	 * Return a unique integer for the specified group.
	 *
	 * The function always returns the same value for the same group as long as the app is
	 * running. After an app restart (when the memory is cleared), a new value will be generated.
	 *
	 * Currently the function is implemented with a sequential positive integer, so the first
	 * contact will get the number 1, the second contact will get the number 2, and so on.
	 */
	public static int getTempId(GroupModel group) {
		return getTempId(KEY_GROUP + group.getId());
	}
}

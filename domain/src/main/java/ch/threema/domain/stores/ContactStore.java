/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

package ch.threema.domain.stores;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.models.Contact;

/**
 * A contact store stores {@link Contact} instances.
 */
public interface ContactStore {

	/**
	 * Return the contact with the specified identity. If the contact cannot be found, return null.
	 * Note that this methods only checks the store and ignores cached contacts. Therefore, this
	 * method can be used to check whether a contact is unknown or not.
	 */
	@Nullable Contact getContactForIdentity(@NonNull String identity);

	/**
	 * Add a contact into the cache. Note that this contact is not permanently stored. This is only
	 * used to reduce the number of contact fetches from the server. A contact that is in this cache
	 * is still treated as unknown contact.
	 *
 	 * @param contact the contact that is temporarily saved
	 */
	void addCachedContact(@NonNull Contact contact);

	/**
	 * Get the cached or stored contact for the given identity. This method checks for stored
	 * contacts in this contact store as well as for temporarily cached contacts. Note that this
	 * method therefore also may return unknown contacts. To check if a contact is known, use
	 * {@link #getContactForIdentity(String)}.
	 *
	 * @param identity the identity of the contact
	 * @return the contact with the given identity or null if none has been found
	 */
	@Nullable
	Contact getContactForIdentityIncludingCache(@NonNull String identity);

	/**
	 * Add a contact to the contact store.
	 *
	 * If a contact already exists, update it.
	 */
	void addContact(@NonNull Contact contact);

	/**
	 * Add a contact to the contact store. If it already exists, update it. The given contact object
	 * is modified when the hide argument is true.
	 *
	 * @param contact the contact that is added or updated
	 * @param hide if true, updates the contact model object and adds or updates the contact; otherwise the contact is added/updated as is
	 */
	void addContact(@NonNull Contact contact, boolean hide);

	/**
	 * Remove a contact from the contact store.
	 */
	void removeContact(@NonNull Contact contact);
}

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
	 * Obtain the public key for the given identity and wrap it in a contact object.
	 *
	 * @param identity desired identity
	 * @param fetch if true, attempt to synchronously fetch the key from the server if necessary
	 * @param saveContact if true, save the contact if it does not exist
	 * @return public key, or null if not found (or fetch failed)
	 */
	@Nullable Contact getContactForIdentity(@NonNull String identity, boolean fetch, boolean saveContact);

	/**
	 * Return the contact with the specified identity.
	 *
	 * If the contact cannot be found, return null.
	 */
	@Nullable Contact getContactForIdentity(@NonNull String identity);

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

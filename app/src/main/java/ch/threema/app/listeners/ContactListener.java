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

package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.storage.models.ContactModel;

public interface ContactListener {
	/**
	 * A new contact is added.
	 */
	@AnyThread default void onNew(final ContactModel createdContactModel) { }

	/**
	 * Called when the contact is modified.
	 */
	@AnyThread default void onModified(final ContactModel modifiedContactModel) { }

	/**
	 * Called when the contact avatar was changed.
	 */
	@AnyThread default void onAvatarChanged(final ContactModel contactModel) { }

	/**
	 * The contact was removed.
	 */
	@AnyThread default void onRemoved(final ContactModel removedContactModel) { }

	/**
	 * Return true if the specified contact should be handled.
	 */
	@AnyThread default boolean handle(String identity) {
		return true;
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app.stores;

import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.SynchronizeContactsUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.stores.ContactStore;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.models.ContactModel;

/**
 * The {@link DatabaseContactStore} is an implementation of the {@link ContactStore} interface
 * which stores the contacts in the Android SQLite database.
 */
public class DatabaseContactStore implements ContactStore {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DatabaseContactStore");

	private final APIConnector apiConnector;
	private final PreferenceService preferenceService;
	private final DatabaseServiceNew databaseServiceNew;
	private final IdListService blackListService;
	private final IdListService excludeListService;

	public DatabaseContactStore(
		APIConnector apiConnector,
		PreferenceService preferenceService,
		DatabaseServiceNew databaseServiceNew,
		IdListService blackListService,
		IdListService excludeListService
	) {
		this.apiConnector = apiConnector;
		this.preferenceService = preferenceService;
		this.databaseServiceNew = databaseServiceNew;
		this.blackListService = blackListService;
		this.excludeListService = excludeListService;
	}

	@Override
	public byte[] getPublicKeyForIdentity(@NonNull String identity, boolean fetch) {
		Contact contact = this.getContactForIdentity(identity);

		if (contact == null) {
			if (fetch) {
				try {
					//check if identity is on black list
					if(this.blackListService != null && this.blackListService.has(identity)) {
						return null;
					}

					if (this.preferenceService != null) {
						if (this.preferenceService.isSyncContacts()) {
							//check if is on exclude list
							if (this.excludeListService != null && !this.excludeListService.has(identity)) {
								SynchronizeContactsUtil.startDirectly(identity);

								//try to select again
								contact = this.getContactForIdentity(identity);
								if (contact != null) {
									return contact.getPublicKey();
								}
							}
						}

						//do not fetch if block unknown is enabled
						if (this.preferenceService.isBlockUnknown()) {
							return null;
						}
					}

					return this.fetchPublicKeyForIdentity(identity);
				} catch (Exception e) {
					logger.error("Exception", e);
					return null;
				}
			}
			return null;
		}

		return contact.getPublicKey();
	}

	/**
	 * Fetch a public key for an identity, create a contact and save it
	 *
	 * @param identity Identity to add a contact for
	 * @throws ThreemaException if a contact with this identity already exists
	 *          FileNotFoundException if identity was not found on the server
	 * @return public key of identity in case of success, null otherwise
	 */
	@WorkerThread
	public @Nullable byte[] fetchPublicKeyForIdentity(@NonNull String identity) throws FileNotFoundException, ThreemaException {
		APIConnector.FetchIdentityResult result = null;
		try {
			Contact contact = this.getContactForIdentity(identity);
			if(contact != null) {
				//cannot fetch and save... contact already exists
				throw new ThreemaException("contact already exists, cannot fetch and save");
			}

			result = this.apiConnector.fetchIdentity(identity);
		}
		catch (FileNotFoundException e) {
			throw e;
		} catch (Exception e) {
			//do nothing
			return null;
		}
		byte[] b = result.publicKey;

		if(b != null) {
			ContactModel contact = new ContactModel(identity, b);
			contact.setFeatureMask(result.featureMask);
			contact.setVerificationLevel(VerificationLevel.UNVERIFIED);
			contact.setDateCreated(new Date());
			contact.setIdentityType(result.type);
			switch (result.state) {
				case IdentityState.ACTIVE:
					contact.setState(ContactModel.State.ACTIVE);
					break;
				case IdentityState.INACTIVE:
					contact.setState(ContactModel.State.INACTIVE);
					break;
				case IdentityState.INVALID:
					contact.setState(ContactModel.State.INVALID);
					break;

			}

			this.addContact(contact);

			return b;
		}

		return null;
	}

	@Override
	public @Nullable Contact getContactForIdentity(@NonNull String identity) {
		return this.databaseServiceNew.getContactModelFactory().getByIdentity(identity);
	}

	public @Nullable ContactModel getContactModelForIdentity(@NonNull String identity) {
		return this.databaseServiceNew.getContactModelFactory().getByIdentity(identity);
	}

	public @Nullable ContactModel getContactModelForPublicKey(final byte[] publicKey) {
		return this.databaseServiceNew.getContactModelFactory().getByPublicKey(publicKey);
	}

	public @Nullable ContactModel getContactModelForLookupKey(final String lookupKey) {
		return this.databaseServiceNew.getContactModelFactory().getByLookupKey(lookupKey);
	}

	@Override
	public void addContact(@NonNull Contact contact) {
		ContactModel contactModel = (ContactModel)contact;
		boolean isUpdate = false;

		ContactModelFactory contactModelFactory = this.databaseServiceNew.getContactModelFactory();
		//get db record
		ContactModel existingModel = contactModelFactory.getByIdentity(contactModel.getIdentity());

		if (existingModel != null) {
			isUpdate = true;
			//check for modifications!
			if(TestUtil.compare(contactModel.getModifiedValueCandidates(), existingModel.getModifiedValueCandidates())) {
				logger.debug("do not save unmodified contact");
				return;
			}
		}

		if (contactModel.getColor() == 0) {
			// rebuild color for the contact model
			long count = this.databaseServiceNew.getContactModelFactory().count();
			contactModel.setColor(ColorUtil.getInstance().getRecordColor((int) count));
		}
		contactModelFactory.createOrUpdate(contactModel);

		// Fire listeners
		if (!isUpdate) {
			this.fireOnNewContact(contactModel);
		} else {
			this.fireOnModifiedContact(contactModel);
		}
	}

	/**
	 * Mark the contact as hidden / unhidden. Then store or update the contact in the database.
	 */
	public void hideContact(@NonNull ContactModel contactModel, boolean hide) {
		// Mark as hidden / unhidden
		contactModel.setIsHidden(hide);

		// Update database
		ContactModelFactory contactModelFactory = this.databaseServiceNew.getContactModelFactory();
		contactModelFactory.createOrUpdate(contactModel);

		// Fire listeners
		if (hide) {
			this.fireOnRemovedContact(contactModel);
		} else {
			this.fireOnNewContact(contactModel);
		}
	}

	@Override
	public void removeContact(@NonNull Contact contact) {
		this.removeContact((ContactModel)contact);
	}

	public void removeContact(final ContactModel contactModel) {
		this.databaseServiceNew.getContactModelFactory().delete(contactModel);
		fireOnRemovedContact(contactModel);
	}

	private void fireOnNewContact(final ContactModel createdContactModel) {
		ListenerManager.contactListeners.handle(listener -> {
			if (listener.handle(createdContactModel.getIdentity())) {
				listener.onNew(createdContactModel);
			}
		});
	}

	private void fireOnModifiedContact(final ContactModel modifiedContactModel) {
		ListenerManager.contactListeners.handle(listener -> {
			if (listener.handle(modifiedContactModel.getIdentity())) {
				listener.onModified(modifiedContactModel);
			}
		});
	}

	private void fireOnRemovedContact(final ContactModel removedContactModel) {
		ListenerManager.contactListeners.handle(listener -> {
			if (listener.handle(removedContactModel.getIdentity())) {
				listener.onRemoved(removedContactModel);
			}
		});
	}
}

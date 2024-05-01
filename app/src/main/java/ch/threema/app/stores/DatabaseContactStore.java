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

package ch.threema.app.stores;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.models.Contact;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.DHSessionStoreException;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.protobuf.csp.e2e.fs.Terminate;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ContactModel.AcquaintanceLevel;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel;

/**
 * The {@link DatabaseContactStore} is an implementation of the {@link ContactStore} interface
 * which stores the contacts in the Android SQLite database.
 */
public class DatabaseContactStore implements ContactStore {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DatabaseContactStore");

	private final @NonNull IdentityStoreInterface identityStore;
	private final @NonNull DHSessionStoreInterface fsSessions;
	private final @NonNull DatabaseServiceNew databaseServiceNew;

	/**
	 * This map contains the special contacts.
	 */
	private final @NonNull Map<String, Contact> specialContacts = new HashMap<>();

	/**
	 * The cache of fetched contacts. Note that this cache only contains the cached contacts from a
	 * server fetch. Contacts from the database are not cached here.
	 */
	private final @NonNull Map<String, Contact> contactCache = new HashMap<>();

	public DatabaseContactStore(
		@NonNull IdentityStoreInterface identityStore,
		@NonNull DHSessionStoreInterface fsSessions,
		@NonNull DatabaseServiceNew databaseServiceNew,
		@NonNull ServerAddressProvider serverAddressProvider
	) {
		this.identityStore = identityStore;
		this.fsSessions = fsSessions;
		this.databaseServiceNew = databaseServiceNew;

		try {
			// Add special contact '*3MAPUSH'
			specialContacts.put(
				ProtocolDefines.SPECIAL_CONTACT_PUSH,
				new Contact(ProtocolDefines.SPECIAL_CONTACT_PUSH, serverAddressProvider.getThreemaPushPublicKey())
			);
		} catch (ThreemaException e) {
			logger.error("Could not add special contact {} due to missing public key", ProtocolDefines.SPECIAL_CONTACT_PUSH, e);
		}
	}

	@Override
	public @Nullable ContactModel getContactForIdentity(@NonNull String identity) {
		return this.databaseServiceNew.getContactModelFactory().getByIdentity(identity);
	}

	public @Nullable ContactModel getContactModelForPublicKey(final byte[] publicKey) {
		return this.databaseServiceNew.getContactModelFactory().getByPublicKey(publicKey);
	}

	public @Nullable ContactModel getContactModelForLookupKey(final String lookupKey) {
		return this.databaseServiceNew.getContactModelFactory().getByLookupKey(lookupKey);
	}

	@Override
	public void addCachedContact(@NonNull Contact contact) {
		contactCache.put(contact.getIdentity(), contact);
	}

	@Nullable
	@Override
	public Contact getContactForIdentityIncludingCache(@NonNull String identity) {
		Contact special = specialContacts.get(identity);
		if (special != null) {
			return special;
		}

		Contact cached = contactCache.get(identity);
		if (cached != null) {
			return cached;
		}

		return getContactForIdentity(identity);
	}

	@Override
	public void addContact(@NonNull Contact contact) {
		addContact(contact, false);
	}

	@Override
	public void addContact(@NonNull Contact contact, boolean hide) {
		ContactModel contactModel = (ContactModel)contact;
		boolean isUpdate = false;

		contactModel.setAcquaintanceLevel(hide ? AcquaintanceLevel.GROUP : AcquaintanceLevel.DIRECT);

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

			// Only warn about an FS feature mask downgrade if an FS session existed.
			DHSession fsSession = null;
			try {
				fsSession = fsSessions.getBestDHSession(identityStore.getIdentity(), contact.getIdentity(), ThreemaApplication.requireServiceManager().getMigrationTaskHandle());
			} catch (DHSessionStoreException | NullPointerException exception) {
				logger.error("Unable to determine best DH session", exception);
			}
			if (fsSession != null && !ThreemaFeature.canForwardSecurity(contactModel.getFeatureMask())) {
				logger.info("Forward security feature has been downgraded for contact {}", contactModel.getIdentity());
				// Create a status message that forward security has been disabled for this contact
				// due to a downgrade.
				createForwardSecurityDowngradedStatus(contactModel);

				// Clear and terminate all sessions with that contact
				ServiceManager serviceManager = ThreemaApplication.getServiceManager();
				if (serviceManager != null) {
					serviceManager.getTaskCreator().scheduleDeleteAndTerminateFSSessionsTaskAsync(
						contact, Terminate.Cause.DISABLED_BY_REMOTE
					);
				}
			}
		}

		contactModelFactory.createOrUpdate(contactModel);

		// Fire listeners
		if (!isUpdate) {
			this.fireOnNewContact(contactModel);
		} else {
			this.fireOnModifiedContact(contactModel);
		}
	}

	private void createForwardSecurityDowngradedStatus(@NonNull ContactModel contactModel) {
		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager != null) {
				MessageService messageService = serviceManager.getMessageService();
				ContactService contactService = serviceManager.getContactService();
				messageService.createForwardSecurityStatus(
					contactService.createReceiver(contactModel),
					ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE,
					0,
					null
				);
			} else {
				logger.error("ServiceManager is null");
			}
		} catch (ThreemaException e) {
			logger.error("Error while creating forward security downgrade status message", e);
		}
	}

	/**
	 * Mark the contact as hidden / unhidden. Then store or update the contact in the database.
	 */
	public void hideContact(@NonNull ContactModel contactModel, boolean hide) {
		// Mark as hidden / unhidden
		contactModel.setAcquaintanceLevel(hide ? AcquaintanceLevel.GROUP : AcquaintanceLevel.DIRECT);

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

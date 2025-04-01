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

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.BasicContact;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.csp.ProtocolDefines;
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

    private final @NonNull DatabaseServiceNew databaseServiceNew;

    /**
     * This map contains the special contacts.
     */
    private final @NonNull Map<String, Contact> specialContacts = new HashMap<>();

    /**
     * The cache of contacts. Note that this cache only contains the cached contacts from a server
     * fetch. Contacts from the database are not cached here.
     */
    private final @NonNull Map<String, BasicContact> contactCache = new HashMap<>();

    public DatabaseContactStore(
        @NonNull DatabaseServiceNew databaseServiceNew,
        @NonNull ServerAddressProvider serverAddressProvider
    ) {
        this.databaseServiceNew = databaseServiceNew;

        try {
            // Add special contact '*3MAPUSH'
            final byte[] publicKey = serverAddressProvider.getThreemaPushPublicKey();
            if (publicKey == null) {
                logger.error("Could not add special contact {} due to missing public key", ProtocolDefines.SPECIAL_CONTACT_PUSH);
            } else {
                specialContacts.put(
                    ProtocolDefines.SPECIAL_CONTACT_PUSH,
                    new Contact(ProtocolDefines.SPECIAL_CONTACT_PUSH, publicKey, VerificationLevel.FULLY_VERIFIED)
                );
            }
        } catch (ThreemaException e) {
            logger.error("Could not add special contact {}", ProtocolDefines.SPECIAL_CONTACT_PUSH, e);
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
    public void addCachedContact(@NonNull BasicContact contact) {
        contactCache.put(contact.getIdentity(), contact);
    }

    @Nullable
    @Override
    public BasicContact getCachedContact(@NonNull String identity) {
        return contactCache.get(identity);
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
        ContactModel contactModel = (ContactModel) contact;
        boolean isUpdate = false;

        ContactModelFactory contactModelFactory = this.databaseServiceNew.getContactModelFactory();
        //get db record
        ContactModel existingModel = contactModelFactory.getByIdentity(contactModel.getIdentity());

        if (existingModel != null) {
            isUpdate = true;
            //check for modifications!
            if (TestUtil.compare(contactModel.getModifiedValueCandidates(), existingModel.getModifiedValueCandidates())) {
                logger.info("Do not save unmodified contact");
                return;
            }

            // TODO(ANDR-3113): Just for debugging. Must be removed once the error is found.
            if (ConfigUtils.isDevBuild()) {
                Date existingLastUpdate = existingModel.getLastUpdate();
                Date newLastUpdate = contactModel.getLastUpdate();
                if (existingLastUpdate != null && newLastUpdate != null
                    && newLastUpdate.before(existingLastUpdate)) {
                    logger.error(
                        "Storing contact model of '{}' with older last update ({}) than before ({}): {}",
                        contactModel.getIdentity(),
                        newLastUpdate.getTime(),
                        existingLastUpdate.getTime(),
                        Arrays.stream(Thread.currentThread().getStackTrace())
                            .map(StackTraceElement::toString)
                            .collect(Collectors.joining("\n"))
                    );
                }
            }
        }

        contactModelFactory.createOrUpdate(contactModel);

        // Fire listeners
        if (!isUpdate) {
            this.fireOnNewContact(contactModel);
        } else {
            this.fireOnModifiedContact(contactModel.getIdentity());
        }
    }

    @Override
    public boolean isSpecialContact(@NonNull String identity) {
        return specialContacts.containsKey(identity);
    }

    private void fireOnNewContact(final ContactModel createdContactModel) {
        ListenerManager.contactListeners.handle(listener ->
            listener.onNew(createdContactModel.getIdentity())
        );
    }

    private void fireOnModifiedContact(final String identity) {
        ListenerManager.contactListeners.handle(listener -> {
            listener.onModified(identity);
        });
    }
}

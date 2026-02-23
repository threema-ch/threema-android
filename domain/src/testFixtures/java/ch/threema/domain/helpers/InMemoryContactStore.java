package ch.threema.domain.helpers;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.BasicContact;
import ch.threema.domain.stores.ContactStore;

/**
 * An in-memory contact store, used for testing.
 */
public class InMemoryContactStore implements ContactStore {
    private final Map<String, Contact> contacts = new HashMap<>();
    private final Map<String, BasicContact> contactsCache = new HashMap<>();

    @Override
    public Contact getContactForIdentity(@NonNull String identity) {
        return this.contacts.get(identity);
    }

    @Override
    public void addContact(@NonNull Contact contact) {
        this.contacts.put(contact.getIdentity(), contact);
    }

    @Override
    public void addCachedContact(@NonNull BasicContact contact) {
        this.contactsCache.put(contact.getIdentity(), contact);
    }

    @Nullable
    @Override
    public BasicContact getCachedContact(@NonNull String identity) {
        return this.contactsCache.get(identity);
    }

    @Nullable
    @Override
    public Contact getContactForIdentityIncludingCache(@NonNull String identity) {
        Contact cached = contactsCache.get(identity);
        if (cached != null) {
            return cached;
        }

        return getContactForIdentity(identity);
    }

    @Override
    public boolean isSpecialContact(@NonNull String identity) {
        return false;
    }
}

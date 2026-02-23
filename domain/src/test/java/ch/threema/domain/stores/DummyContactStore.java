package ch.threema.domain.stores;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.BasicContact;

public class DummyContactStore implements ContactStore {
    private final Map<String, Contact> contactMap;
    private final Map<String, BasicContact> contactCache = new HashMap<>();

    public DummyContactStore() {
        this.contactMap = new HashMap<>();
    }

    @Nullable
    @Override
    public Contact getContactForIdentity(@NonNull String identity) {
        return contactMap.get(identity);
    }

    @Override
    public void addContact(@NonNull Contact contact) {
        contactMap.put(contact.getIdentity(), contact);
    }

    @Override
    public void addCachedContact(@NonNull BasicContact contact) {
        this.contactCache.put(contact.getIdentity(), contact);
    }

    @Nullable
    @Override
    public BasicContact getCachedContact(@NonNull String identity) {
        return this.contactCache.get(identity);
    }

    @Nullable
    @Override
    public Contact getContactForIdentityIncludingCache(@NonNull String identity) {
        Contact cached = contactCache.get(identity);
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

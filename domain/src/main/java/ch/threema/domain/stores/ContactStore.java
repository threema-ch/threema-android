package ch.threema.domain.stores;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.BasicContact;

/**
 * A contact store stores {@link Contact} instances.
 */
public interface ContactStore {

    /**
     * Return the contact with the specified identity. If the contact cannot be found, return null.
     * Note that this methods only checks the store and ignores cached contacts. Therefore, this
     * method can be used to check whether a contact is unknown or not.
     */
    @Nullable
    Contact getContactForIdentity(@NonNull String identity);

    /**
     * Add a contact into the cache. Note that this contact is not permanently stored. This is only
     * used to reduce the number of contact fetches from the server. A contact that is in this cache
     * is still treated as unknown contact.
     *
     * @param contact the contact that is temporarily saved
     */
    void addCachedContact(@NonNull BasicContact contact);

    /**
     * Get the cached contact for the given identity. If there is no cached contact, null is
     * returned. Note that if the contact with the given identity exists but is not in cache, null
     * is returned.
     */
    @Nullable
    BasicContact getCachedContact(@NonNull String identity);

    /**
     * Get the cached or stored contact for the given identity. This method first checks if the
     * identity belongs to a special contact. Then it checks the cache and if not successful the
     * database. Note that this method therefore also may return unknown contacts (if the contact is
     * cached). To check if a contact is known (except special contacts), use
     * {@link #getContactForIdentity(String)}.
     *
     * @param identity the identity of the contact
     * @return the contact with the given identity or null if none has been found
     */
    @Nullable
    Contact getContactForIdentityIncludingCache(@NonNull String identity);

    /**
     * Add or update a contact in the contact store.
     */
    void addContact(@NonNull Contact contact);

    /**
     * Check whether the identity belongs to a special contact or not.
     */
    boolean isSpecialContact(@NonNull String identity);

}

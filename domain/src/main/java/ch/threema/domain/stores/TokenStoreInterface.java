package ch.threema.domain.stores;

import androidx.annotation.Nullable;

/**
 * Interface for storing/caching tokens to avoid obtaining them again before each request.
 */
public interface TokenStoreInterface {
    @Nullable
    String getToken();

    void storeToken(String token);
}

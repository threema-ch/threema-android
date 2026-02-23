package ch.threema.domain.stores;

/**
 * Interface for storing/caching tokens to avoid obtaining them again before each request.
 */
public interface TokenStoreInterface {
    String getToken();

    void storeToken(String token);
}

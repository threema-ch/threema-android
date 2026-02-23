package ch.threema.app.stores;

import android.os.SystemClock;
import android.text.format.DateUtils;

import androidx.annotation.Nullable;
import ch.threema.domain.stores.TokenStoreInterface;

/**
 * Stores the auth token used for onprem. It is cached for 24 hours. After 24 hours,
 * {@link #getToken()} returns null.
 */
public class AuthTokenStore implements TokenStoreInterface {

    @Nullable
    private String authToken;

    private long ttl = 0;

    @Override
    public String getToken() {
        // If the time to live is in the past, we set the token to null
        if (ttl < SystemClock.elapsedRealtime()) {
            authToken = null;
        }

        return authToken;
    }

    @Override
    public void storeToken(@Nullable String authToken) {
        this.authToken = authToken;
        // Set the ttl to 24 hours in the future
        this.ttl = SystemClock.elapsedRealtime() + DateUtils.DAY_IN_MILLIS;
    }
}

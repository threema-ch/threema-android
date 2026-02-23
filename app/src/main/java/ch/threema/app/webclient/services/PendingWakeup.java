package ch.threema.app.webclient.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.annotation.SameThread;

/**
 * POJO for pending wakeups.
 */
@SameThread
class PendingWakeup {
    @NonNull
    final String publicKeySha256String;
    @Nullable
    String affiliationId;
    long expiration;

    PendingWakeup(
        @NonNull final String publicKeySha256String,
        @Nullable final String affiliationId,
        final long expiration
    ) {
        this.publicKeySha256String = publicKeySha256String;
        this.affiliationId = affiliationId;
        this.expiration = expiration;
    }
}

package ch.threema.domain.protocol;

import javax.net.ssl.SSLSocketFactory;

import androidx.annotation.NonNull;

/**
 * A factory that creates a socket factory (based on a hostname). Very java-esque :)
 */
@FunctionalInterface
public interface SSLSocketFactoryFactory {
    @NonNull
    SSLSocketFactory makeFactory(@NonNull String hostname);
}

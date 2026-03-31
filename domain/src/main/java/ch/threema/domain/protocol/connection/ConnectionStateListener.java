package ch.threema.domain.protocol.connection;

import androidx.annotation.Nullable;

/**
 * Interface for objects that wish to be informed about changes in the server connection state.
 */
public interface ConnectionStateListener {
    void updateConnectionState(@Nullable ConnectionState connectionState);
}

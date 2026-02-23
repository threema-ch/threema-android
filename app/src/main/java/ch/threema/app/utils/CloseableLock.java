package ch.threema.app.utils;

/**
 * An {@link AutoCloseable} where {@link #close()} does not throw any checked exception.
 */
public interface CloseableLock extends AutoCloseable {
    /**
     * Unlocking does not throw any checked exception.
     */
    @Override
    void close();
}

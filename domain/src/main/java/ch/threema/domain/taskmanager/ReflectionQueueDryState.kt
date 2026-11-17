package ch.threema.domain.taskmanager

/**
 * The state of the reflection queue of the current connection.
 *
 * Note: If multi-device is not active, the reflection queue does not exist, so the state remains PENDING.
 */
enum class ReflectionQueueDryState {
    /**
     * There is no connection or the server has not sent the reflection queue dry message for the current connection yet.
     */
    PENDING,

    /**
     * The server has sent the reflection queue dry message for the current connection, which is still open.
     */
    DRY,
}

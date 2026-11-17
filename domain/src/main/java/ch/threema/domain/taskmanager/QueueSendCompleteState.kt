package ch.threema.domain.taskmanager

/**
 * The state of the message queue of the current connection.
 */
enum class QueueSendCompleteState {
    /**
     * There is no connection or the server has not sent the entire message queue of the current connection yet.
     */
    PENDING,

    /**
     * The server has sent the entire message queue of the current connection, which is still open.
     */
    DRY,
}

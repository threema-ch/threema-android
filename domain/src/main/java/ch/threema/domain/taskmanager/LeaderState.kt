package ch.threema.domain.taskmanager

/**
 * The state of the leader role of this device in multi-device mode.
 *
 * Note: If multi-device is not active, there is no leader role, so the state remains NOT_LEADER.
 */
enum class LeaderState {
    /**
     * There is no connection, multi device is disabled, or the server has not promoted this device to leader on the current connection yet.
     */
    NOT_LEADER,

    /**
     * The server has promoted this device to leader on the current connection, which is still open.
     */
    LEADER,
}

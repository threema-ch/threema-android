package ch.threema.domain.taskmanager

import ch.threema.domain.protocol.connection.ConnectionLock

/**
 * Handle several connection locks. This allows to release multiple locks at once. Adding and
 * releasing the locks is synchronized.
 */
class ConnectionLockManager {

    private val connectionLocks: MutableList<ConnectionLock> = mutableListOf()

    /**
     * Add a new connection lock. This also performs a cleanup operation to remove old unused locks.
     */
    @Synchronized
    fun addConnectionLock(connectionLock: ConnectionLock) {
        // Add the lock if it is held
        connectionLocks.add(connectionLock)
        // Remove all locks that are not held anymore because they timed out. This reduces the
        // amount of locks in case the locks are not often released.
        connectionLocks.removeIf { lock -> !lock.isHeld() }
    }

    /**
     * Release all connection locks.
     */
    @Synchronized
    fun releaseConnectionLocks() {
        for (connectionLock in connectionLocks) {
            connectionLock.release()
        }
        connectionLocks.clear()
    }
}

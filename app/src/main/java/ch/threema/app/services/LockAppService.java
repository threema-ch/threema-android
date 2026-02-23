package ch.threema.app.services;

import ch.threema.base.SessionScoped;

@SessionScoped
public interface LockAppService {

    interface OnLockAppStateChanged {
        /**
         * return true if the event will be removed from the queue
         */
        boolean changed(boolean locked);
    }

    /**
     * return if app locking is enabled
     */
    boolean isLockingEnabled();

    /**
     * return if the application is locked
     */
    boolean isLocked();

    /**
     * try to unlock the application
     */
    boolean unlock(String pin);

    /**
     * lock the application
     */
    void lock();

    boolean checkLock();

    /**
     * reset the timer
     */
    void resetLockTimer(boolean restartAfterReset);

    void addOnLockAppStateChanged(OnLockAppStateChanged c);
}

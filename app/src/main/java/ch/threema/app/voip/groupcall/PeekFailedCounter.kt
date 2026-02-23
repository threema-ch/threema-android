package ch.threema.app.voip.groupcall

import ch.threema.app.voip.groupcall.sfu.CallId

class PeekFailedCounter {
    private val failedCounter: MutableMap<CallId, Int> = mutableMapOf()

    /**
     * Get the current count of failed attempts and increment the counter _after_ reading the current value.
     */
    fun getAndIncrementCounter(callId: CallId): Int {
        return synchronized(failedCounter) {
            val counter = failedCounter[callId] ?: 0
            failedCounter[callId] = counter + 1
            counter
        }
    }

    /**
     * Reset the counter associated with a [CallId]
     *
     * @return 0 (the reset value)
     */
    fun resetCounter(callId: CallId): Int {
        synchronized(failedCounter) {
            failedCounter.remove(callId)
        }
        return 0
    }
}

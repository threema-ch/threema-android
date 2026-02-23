package ch.threema.app.voip.groupcall.service

import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.Participant
import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("GroupCallLeaveTimer")

/**
 * This timer ends the call after being alone in a call for [GroupCallLeaveTimer.timeUntilLeaveMs] milliseconds.
 */
class GroupCallLeaveTimer(
    private val participants: Flow<Set<Participant>>,
    private val groupCallLeaveTimeoutCallback: () -> Unit,
) {
    private val timeUntilLeaveMs = 180_000L

    private val scheduledExecutorService: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor()
    private var scheduledFuture: ScheduledFuture<*>? = null

    private var stopped = false
    private var participantCount = -1

    init {
        CoroutineScope(GroupCallThreadUtil.dispatcher).launch {
            participants.collect {
                participantCount = it.size
                onParticipantCountChanged()
            }
        }
    }

    /**
     * Disable (and cancel) the timer. After this call being alone in a call does not start the timer again.
     */
    fun disable() {
        logger.info("Group call leave timer disabled")
        stopTimer()
        stopped = true
    }

    private fun onParticipantCountChanged() {
        if (participantCount > 1) {
            stopTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (!stopped) {
            scheduledFuture?.cancel(false)
            scheduledFuture = scheduledExecutorService.schedule(
                ::onTimeout,
                timeUntilLeaveMs,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun stopTimer() {
        scheduledFuture?.cancel(false)
        scheduledFuture = null
    }

    private fun onTimeout() {
        logger.info("Leaving group call as no one has been in the call for the last ${timeUntilLeaveMs / 1000} seconds")
        groupCallLeaveTimeoutCallback()
    }
}

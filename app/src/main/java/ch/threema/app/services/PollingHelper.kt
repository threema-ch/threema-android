package ch.threema.app.services

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import ch.threema.android.getPendingIntentForBroadcast
import ch.threema.app.BuildConfig
import ch.threema.app.GlobalAppState
import ch.threema.app.connection.ServerConnectionManager
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.receivers.FetchMessagesBroadcastReceiver
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.TimeProvider
import ch.threema.common.await
import ch.threema.common.plus
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.taskmanager.QueueSendCompleteState
import ch.threema.domain.taskmanager.TaskManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("PollingHelper")

/**
 * Helper class to simplify polling (both time-based and FCM-based) by handling all the gory details of connecting to fetch new messages and of
 * ensuring that the connection is released once the server has sent all new messages.
 *
 * This class is thread-safe. However, wake lock handling is best-effort: if multiple instances of this class exist, the wake lock may be released
 * too early.
 */
class PollingHelper(
    appContext: Context,
    private val lifetimeServiceTag: LifetimeService.SourceTag,
) : KoinComponent {
    private val context: Context = appContext.applicationContext
    private val globalAppState: GlobalAppState by inject()
    private val deviceService: DeviceService? by injectNullableNonBinding()
    private val taskManager: TaskManager? by injectNullableNonBinding()
    private val lifetimeService: LifetimeService? by injectNullableNonBinding()
    private val serverConnectionManager: ServerConnectionManager? by injectNullableNonBinding()
    private val dispatcherProvider: DispatcherProvider by inject()
    private val timeProvider: TimeProvider by inject()
    private val pollScope = CoroutineScope(dispatcherProvider.worker)

    @Volatile
    private var connectionAcquired = false

    // The wake lock handle, cached to avoid creating a new one for every poll
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Try to connect to the server and fetch new messages.
     *
     * If a connection slot is already held by this instance, this method does nothing.
     *
     * @param useWakeLock if set to true, a partial wake lock will be acquired while polling and during the connection linger phase
     * @return true if a connection slot was (or already is) acquired, false otherwise
     */
    @Synchronized
    fun poll(useWakeLock: Boolean): Boolean {
        logger.info("Fetch attempt. Source = {}", lifetimeServiceTag)

        val deviceService = this.deviceService ?: return false
        val taskManager = this.taskManager ?: return false
        val lifetimeService = this.lifetimeService ?: return false

        // If the device is not online, there's no use in trying to connect.
        if (!deviceService.isOnline()) {
            logger.info("Not polling, device is offline")
            return false
        }

        if (connectionAcquired) {
            logger.info("Fetch attempt. Connection already acquired.")
            return true
        }

        // Don't poll if the app is currently in a special mode
        if (globalAppState.specialMode != null) {
            return false
        }

        // If requested, acquire a wakelock
        val wakeLock = if (useWakeLock) acquireWakeLock() else null

        // Determine timeout duration. If we're already connected it can be shorter.
        var timeout = CONNECTION_TIMEOUT
        if (serverConnectionManager?.connection?.connectionState == ConnectionState.LOGGED_IN) {
            logger.info("Already connected")
            timeout = CONNECTION_TIMEOUT_ALREADY_CONNECTED
        }

        // Acquire a connection to the Threema server
        lifetimeService.acquireConnection(lifetimeServiceTag)

        if (!lifetimeService.isActive) {
            scheduleRetryAlarm(timeout)
            releaseWakeLock(wakeLock)
            return false
        }

        // Polling was successful, run the poll in the background
        connectionAcquired = true
        pollScope.launch {
            runPoll(taskManager, lifetimeService, timeout, wakeLock)
        }

        return true
    }

    /**
     * Waits until the queue of the current connection is dry or the timeout is reached, then releases the connection (it will linger for a bit,
     * so that outgoing delivery receipts can be sent) and the wake lock (if any).
     *
     * The DRY state is only reported while the connection is open, so if the connection drops we keep waiting for the next connection attempt, up
     * to the timeout.
     */
    private suspend fun runPoll(
        taskManager: TaskManager,
        lifetimeService: LifetimeService,
        timeout: Duration,
        wakeLock: PowerManager.WakeLock?,
    ) {
        try {
            withTimeout(timeout) {
                taskManager.watchQueueSendComplete().await(QueueSendCompleteState.DRY)
            }
            logger.info("Queue of the current connection is dry, releasing connection (source={})", lifetimeServiceTag)
        } catch (_: TimeoutCancellationException) {
            logger.warn("Timeout fetching message (source={}). Releasing connection", lifetimeServiceTag)
        }

        logger.debug("Releasing connection (source={})", lifetimeServiceTag)

        // Release the connection slot and mark the connection as released, atomically. The slot is released while the flag is still set, so a
        // concurrent poll cannot re-acquire it.
        synchronized(this) {
            lifetimeService.releaseConnectionLinger(lifetimeServiceTag, CONNECTION_LINGER)
            connectionAcquired = false
        }

        // Keep the wakelock until the connection linger phase is over, so the app is not killed while the connection is still active.
        delay(CONNECTION_LINGER)
        releaseWakeLock(wakeLock)
    }

    private fun scheduleRetryAlarm(timeout: Duration) {
        val alarmManager = context.getSystemService<AlarmManager>()
        val pendingIntent = getPendingIntentForBroadcast(
            context = context,
            requestCode = 0,
            intent = Intent(context, FetchMessagesBroadcastReceiver::class.java),
            flags = IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE,
        )

        // Cancel pending alarms
        alarmManager?.cancel(pendingIntent)

        logger.info("Schedule another fetching attempt in two minutes")
        alarmManager?.setAndAllowWhileIdle(
            AlarmManager.RTC,
            (timeProvider.get() + timeout).toEpochMilli(),
            pendingIntent,
        )
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        val powerManager = context.getSystemService<PowerManager>()
        if (powerManager == null) {
            logger.warn("PowerManager not available, continuing without wakelock")
            return null
        }
        logger.info("Acquiring wakelock")
        val lock = wakeLock
            ?: powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).also { wakeLock = it }
        lock.acquire(WAKE_LOCK_TIMEOUT.inWholeMilliseconds)
        return lock
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        if (wakeLock?.isHeld == true) {
            logger.info("Releasing wakelock")
            wakeLock.release()
        }
    }

    companion object {
        /** Tag identifying the wake locks acquired by this class. Must be unique per consumer. */
        private const val WAKE_LOCK_TAG = BuildConfig.APPLICATION_ID + ":PollingHelper"

        /** Maximum time to hold the wake lock (the release is always much earlier in practice) */
        private val WAKE_LOCK_TIMEOUT = 10.minutes

        /**
         * Maximum time to stay connected for each poll (usually the connection will be terminated earlier as the server signals the end of the queue)
         */
        private val CONNECTION_TIMEOUT = 2.minutes

        /** Same, but timeout to use if we're already connected when polling */
        private val CONNECTION_TIMEOUT_ALREADY_CONNECTED = 60.seconds

        /** Linger a bit to allow outgoing delivery receipts to be sent */
        private val CONNECTION_LINGER = 5.seconds
    }
}

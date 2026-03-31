package ch.threema.app.workers

import android.content.Context
import androidx.work.*
import ch.threema.android.buildOneTimeWorkRequest
import ch.threema.android.setInputData
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.DeviceService
import ch.threema.app.services.LifetimeService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.TaskManager
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ConnectivityChangeWorker")

class ConnectivityChangeWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val preferenceService: PreferenceService by inject()
    private val deviceService: DeviceService by inject()
    private val taskManager: TaskManager by inject()
    private val lifetimeService: LifetimeService by inject()

    override suspend fun doWork(): Result {
        val networkState = inputData.getString(EXTRA_NETWORK_STATE)

        awaitAppFullyReadyWithTimeout(timeout = 20.seconds)
            ?: return Result.success()

        val preferenceService = preferenceService
        val isOnline = deviceService.isOnline
        val wasOnline = preferenceService.getLastOnlineStatus()
        preferenceService.setLastOnlineStatus(isOnline)

        logger.info("Network state = {}", networkState)
        if (isOnline == wasOnline) {
            return Result.success()
        }

        logger.info("Device is now {}", if (isOnline) "ONLINE" else "OFFLINE")

        // if there are pending messages in the queue, go online for a moment to send them
        try {
            if (isOnline && taskManager.hasPendingTasks()) {
                logger.info("Messages in queue; acquiring connection")
                lifetimeService.acquireConnection(SOURCE_TAG)
                lifetimeService.releaseConnectionLinger(SOURCE_TAG, MESSAGE_SEND_TIME)
            }
        } catch (e: Exception) {
            logger.error("Failed to process pending tasks", e)
        }
        return Result.success()
    }

    companion object {
        private const val MESSAGE_SEND_TIME = 30L * 1000L
        private const val EXTRA_NETWORK_STATE = "NETWORK_STATE"
        private const val SOURCE_TAG = "connectivityChange"

        fun buildWorkRequest(networkState: String): OneTimeWorkRequest =
            buildOneTimeWorkRequest<ConnectivityChangeWorker> {
                setInputData {
                    putString(EXTRA_NETWORK_STATE, networkState)
                }
            }
    }
}

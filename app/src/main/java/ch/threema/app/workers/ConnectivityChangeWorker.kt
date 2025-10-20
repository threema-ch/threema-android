/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.workers

import android.content.Context
import androidx.work.*
import ch.threema.app.di.awaitServiceManagerWithTimeout
import ch.threema.base.utils.LoggingUtil
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent

private val logger = LoggingUtil.getThreemaLogger("ConnectivityChangeWorker")

class ConnectivityChangeWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    override suspend fun doWork(): Result {
        val networkState = inputData.getString(EXTRA_NETWORK_STATE)

        val serviceManager = awaitServiceManagerWithTimeout(timeout = 20.seconds)
            ?: return Result.success()

        val preferenceService = serviceManager.preferenceService
        val isOnline = serviceManager.deviceService.isOnline
        val wasOnline = preferenceService.lastOnlineStatus
        preferenceService.lastOnlineStatus = isOnline

        logger.info("Network state = {}", networkState)
        if (isOnline == wasOnline) {
            return Result.success()
        }

        logger.info("Device is now {}", if (isOnline) "ONLINE" else "OFFLINE")

        // if there are pending messages in the queue, go online for a moment to send them
        try {
            if (isOnline && serviceManager.taskManager.hasPendingTasks()) {
                logger.info("Messages in queue; acquiring connection")
                serviceManager.lifetimeService.acquireConnection(SOURCE_TAG)
                serviceManager.lifetimeService.releaseConnectionLinger(SOURCE_TAG, MESSAGE_SEND_TIME)
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

        fun buildOneTimeWorkRequest(networkState: String): OneTimeWorkRequest {
            val data = Data.Builder()
                .putString(EXTRA_NETWORK_STATE, networkState)
                .build()

            return OneTimeWorkRequestBuilder<ConnectivityChangeWorker>()
                .setInputData(data)
                .build()
        }
    }
}

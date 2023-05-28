/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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
import ch.threema.app.ThreemaApplication
import ch.threema.base.utils.LoggingUtil

class ConnectivityChangeWorker(context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {
    private val logger = LoggingUtil.getThreemaLogger("ConnectivityChangeWorker")

    companion object {
        private const val MESSAGE_SEND_TIME = 30L * 1000L
        private const val EXTRA_NETWORK_STATE = "NETWORK_STATE"

        fun buildOneTimeWorkRequest(networkState: String): OneTimeWorkRequest {
            val data = Data.Builder()
                    .putString(EXTRA_NETWORK_STATE, networkState)
                    .build()

            return OneTimeWorkRequestBuilder<ConnectivityChangeWorker>()
                    .apply { setInputData(data) }
                    .build()
        }
    }

    override fun doWork(): Result {
        var wasOnline = false
        val networkState = inputData.getString(EXTRA_NETWORK_STATE)

        val serviceManager = ThreemaApplication.getServiceManager()
        if (serviceManager != null) {
            val preferenceService = serviceManager.preferenceService
            val online = serviceManager.deviceService.isOnline
            if (preferenceService != null) {
                wasOnline = preferenceService.lastOnlineStatus
                preferenceService.lastOnlineStatus = online
            }

            logger.info("Network state = {}", networkState)

            if (online != wasOnline) {
                logger.info("Device is now {}", if (online) "ONLINE" else "OFFLINE")

                /* if there are pending messages in the queue, go online for a moment to send them */
                try {
                    if (serviceManager.messageQueue.queueSize > 0) {
                        logger.info("Messages in queue; acquiring connection")
                        serviceManager.lifetimeService.acquireConnection("connectivityChange")
                        serviceManager.lifetimeService.releaseConnectionLinger("connectivityChange", MESSAGE_SEND_TIME)
                    }
                } catch (e: Exception) {
                   logger.error("Error", e)
                }
            }
        }
        return Result.success()
    }
}

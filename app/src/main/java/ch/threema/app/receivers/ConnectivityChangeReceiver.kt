/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import ch.threema.app.workers.ConnectivityChangeWorker.Companion.buildOneTimeWorkRequest
import ch.threema.app.workers.WorkerNames
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("ConnectivityChangeReceiver")

class ConnectivityChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        logger.debug("Connectivity change broadcast received")
        try {
            val networkState = intent.extras
                ?.run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        getParcelable(ConnectivityManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        get(ConnectivityManager.EXTRA_NETWORK_INFO) as NetworkInfo?
                    }
                }
                ?.toString()
                ?.also { networkState ->
                    logger.info(networkState)
                }
                ?: "UNKNOWN"

            val workRequest = buildOneTimeWorkRequest(networkState)
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WorkerNames.WORKER_CONNECTIVITY_CHANGE, ExistingWorkPolicy.REPLACE, workRequest)
        } catch (e: IllegalStateException) {
            logger.error("Unable to schedule connectivity change work", e)
        }
    }
}

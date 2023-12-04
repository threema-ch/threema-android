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
import android.net.ConnectivityManager
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import ch.threema.app.ThreemaApplication
import ch.threema.base.utils.LoggingUtil

class RestrictBackgroundChangedWorker(val context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {
    private val logger = LoggingUtil.getThreemaLogger("RestrictBackgroundChangedWorker")

    override fun doWork(): Result {
        logger.info("Processing RestrictBackgroundChanged - start")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (connMgr != null) {
                val serviceManager = ThreemaApplication.getServiceManager()
                if (serviceManager != null) {
                    val notificationService = serviceManager.notificationService
                    when (connMgr.restrictBackgroundStatus) {
                        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ->
                            // Background data usage is blocked for this app. Wherever possible,
                            // the app should also use less data in the foreground.
                            notificationService.showNetworkBlockedNotification(false)
                        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED,
                            // Data Saver is disabled. Since the device is connected to a
                            // metered network, the app should use less data wherever possible.
                        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED ->
                            // The app is whitelisted. Wherever possible,
                            // the app should use less data in the foreground and background.
                            notificationService.cancelNetworkBlockedNotification()
                    }
                }
            }
        }

        logger.info("Processing RestrictBackgroundChanged - end")

        return Result.success()
    }
}

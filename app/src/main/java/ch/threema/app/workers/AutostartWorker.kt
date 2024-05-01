/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.HomeActivity
import ch.threema.app.notifications.NotificationBuilderWrapper
import ch.threema.app.services.NotificationService
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.LoggingUtil

class AutostartWorker(val context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {
    private val logger = LoggingUtil.getThreemaLogger("AutostartWorker")

    override fun doWork(): Result {
        logger.info("Processing AutoStart - start")

        val masterKey = ThreemaApplication.getMasterKey()
        if (masterKey == null) {
            logger.error("Unable to launch app")
            return Result.failure()
        }

        // check if masterkey needs a password and issue a notification if necessary
        if (masterKey.isLocked) {
            val notificationCompat: NotificationCompat.Builder = NotificationBuilderWrapper(
                context,
                NotificationService.NOTIFICATION_CHANNEL_NOTICE,
                null
            )
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle(context.getString(R.string.master_key_locked))
                .setContentText(context.getString(R.string.master_key_locked_notify_description))
                .setTicker(context.getString(R.string.master_key_locked))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
            val notificationIntent = IntentDataUtil.createActionIntentHideAfterUnlock(
                Intent(
                    context,
                    HomeActivity::class.java
                )
            )
            notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE
            )
            notificationCompat.setContentIntent(pendingIntent)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(
                ThreemaApplication.MASTER_KEY_LOCKED_NOTIFICATION_ID,
                notificationCompat.build()
            )
        }

        val serviceManager = ThreemaApplication.getServiceManager()
        if (serviceManager == null) {
            logger.error("Service manager not available")
            return Result.retry()
        }

        // fixes https://issuetracker.google.com/issues/36951052
        val preferenceService = serviceManager.preferenceService
        // reset feature level
        preferenceService.transmittedFeatureMask = 0

        //auto fix failed sync account
        if (preferenceService.isSyncContacts) {
            val userService = serviceManager.userService
            if (!userService.checkAccount()) {
                //create account
                userService.getAccount(true)
                userService.enableAccountAutoSync(true)
            }
        }

        logger.info("Processing AutoStart - end")
        return Result.success()
    }
}

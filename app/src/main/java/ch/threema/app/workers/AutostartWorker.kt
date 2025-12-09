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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ch.threema.app.R
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.home.HomeActivity
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.MasterKeyManager
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("AutostartWorker")

class AutostartWorker(
    val context: Context,
    workerParameters: WorkerParameters,
) :
    CoroutineWorker(context, workerParameters), KoinComponent {

    private val masterKeyManager: MasterKeyManager by inject()
    private val preferenceService: PreferenceService by inject()
    private val userService: UserService by inject()

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        logger.info("Processing AutoStart - start")

        if (masterKeyManager.isLockedWithPassphrase()) {
            val notificationCompat: NotificationCompat.Builder = NotificationCompat.Builder(
                context,
                NotificationChannels.NOTIFICATION_CHANNEL_NOTICE,
            )
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle(context.getString(R.string.master_key_locked))
                .setContentText(context.getString(R.string.master_key_locked_notify_description))
                .setTicker(context.getString(R.string.master_key_locked))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
            val notificationIntent = IntentDataUtil.createActionIntentHideAfterUnlock(
                Intent(
                    context,
                    HomeActivity::class.java,
                ),
            )
            notificationIntent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )
            notificationCompat.setContentIntent(pendingIntent)
            NotificationManagerCompat.from(context).notify(
                NotificationIDs.MASTER_KEY_LOCKED_NOTIFICATION_ID,
                notificationCompat.build(),
            )
        }

        awaitAppFullyReadyWithTimeout(timeout = 20.seconds)
            ?: return Result.retry()

        // reset feature level
        preferenceService.transmittedFeatureMask = 0

        // auto fix failed sync account
        if (preferenceService.isSyncContacts) {
            if (!userService.checkAccount()) {
                // create account
                userService.getAccount(true)
                userService.enableAccountAutoSync(true)
            }
        }

        logger.info("Processing AutoStart - end")
        return Result.success()
    }
}

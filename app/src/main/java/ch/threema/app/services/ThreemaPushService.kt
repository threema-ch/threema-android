/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.app.services

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.DummyActivity
import ch.threema.app.activities.ThreemaPushNotificationInfoActivity
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE
import ch.threema.app.webclient.services.SessionAndroidService
import ch.threema.base.utils.LoggingUtil
import org.slf4j.Logger

private val logger = LoggingUtil.getThreemaLogger("ThreemaPushService")

class ThreemaPushService : Service() {
    // Threema services
    private var lifetimeService: LifetimeService? = null

    @Synchronized
    override fun onCreate() {
        logger.debug("onCreate")
        super.onCreate()

        // Create intent triggered by notification
        val intent = Intent(this, ThreemaPushNotificationInfoActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PENDING_INTENT_FLAG_IMMUTABLE
        )

        createNotificationChannel()

        // Create notification
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            this,
            NotificationChannels.NOTIFICATION_CHANNEL_THREEMA_PUSH)
            .setContentTitle(getString(R.string.threema_push))
            .setContentText(getString(R.string.threema_push_notification_text))
            .setSmallIcon(R.drawable.ic_notification_push)
            .setLocalOnly(true)
            .setContentIntent(contentIntent)
        ServiceCompat.startForeground(
            this,
            THREEMA_PUSH_ACTIVE_NOTIFICATION_ID,
            builder.build(),
            FG_SERVICE_TYPE)
        logger.info("startForeground called")

        // Get lifetime service
        //
        // Initialization may lock the app for a while, so we display the above notification
        // *before* getting the service to avoid a "Context.startForegroundService() did not
        // then call Service.startForeground()" exception.
        val serviceManager = ThreemaApplication.getServiceManager()
        if (serviceManager == null) {
            logger.error("Service Manager not available (passphrase locked?). Can't start Threema Push.")
            stopSelf()
            return
        }
        val lifetimeService = serviceManager.lifetimeService
        this.lifetimeService = lifetimeService

        // Acquire unpauseable connection while the service is running
        lifetimeService.acquireUnpauseableConnection(LIFETIME_SERVICE_TAG)
        isRunning = true
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @Synchronized
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.trace("onStartCommand")
        if (intent == null || intent.action == null) {
            return START_NOT_STICKY
        }
        if (isStopping) {
            // Already stopping
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> logger.info("ACTION_START")
            ACTION_STOP -> {
                logger.info("ACTION_STOP")
                isRunning = false
                isStopping = true
                stopSelf()
            }
            else -> {
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        logger.trace("onDestroy")

        // Release connection
        lifetimeService?.releaseConnection(LIFETIME_SERVICE_TAG)

        // Remove notificatoin
        removeNotification()

        // Stop foreground service
        stopForeground(true)
        logger.info("stopForeground")

        // Done
        isRunning = false
        super.onDestroy()
        isStopping = false
        logger.info("Service destroyed")
    }

    override fun onLowMemory() {
        logger.info("onLowMemory")
        super.onLowMemory()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        logger.info("onTaskRemoved")
        val intent = Intent(this, DummyActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * Remove the persistent notification.
     */
    private fun removeNotification() {
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        notificationManagerCompat.cancel(THREEMA_PUSH_ACTIVE_NOTIFICATION_ID)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (!ConfigUtils.supportsNotificationChannels()) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(this)
        val notificationChannel = NotificationChannel(
                NotificationChannels.NOTIFICATION_CHANNEL_THREEMA_PUSH,
                getString(R.string.threema_push),
                NotificationManager.IMPORTANCE_LOW
        )
        notificationChannel.description = getString(R.string.threema_push_service_description)
        notificationChannel.enableLights(false)
        notificationChannel.enableVibration(false)
        notificationChannel.setShowBadge(false)
        notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationChannel.setSound(null, null)

        notificationManager.createNotificationChannel(notificationChannel)
    }

    companion object {
        private const val THREEMA_PUSH_ACTIVE_NOTIFICATION_ID = 27392
        private const val LIFETIME_SERVICE_TAG = "threemaPushService"
        private val FG_SERVICE_TYPE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0

        // Intent actions
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"

        // State variables
        var isRunning = false
            private set
        private var isStopping = false

        /**
         * Try to start this service. Will start if:
         *
         * - Service is enabled in shared preferences
         * - ServiceManager is available (-> MasterKey must be unlocked or disabled)
         */
        @JvmStatic
        fun tryStart(callerLogger: Logger, appContext: Context): Boolean {
            // Open shared preferences directly. This can be used in situations where we don't know
            // whether the MasterKey is unlocked already.
            val rawSharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)

            // Start the Threema Push Service
            // Note: Not using preferenceService here, because MasterKey
            //       may not be unlocked.
            if (ConfigUtils.useThreemaPush(rawSharedPreferences, appContext)) {
                if (!isRunning) {
                    val intent = Intent(appContext, ThreemaPushService::class.java)
                    intent.action = SessionAndroidService.ACTION_START
                    callerLogger.info("Starting ThreemaPushService")
                    try {
                        ContextCompat.startForegroundService(appContext, intent)
                        return true
                    } catch (e: Exception) {
                        logger.error("Unable to start foreground service", e)
                    }
                }
            }
            return false
        }
    }
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import ch.threema.app.backuprestore.csv.BackupService
import ch.threema.app.receivers.ConnectivityChangeReceiver
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.services.LifetimeService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("GlobalBroadcastReceivers")

object GlobalBroadcastReceivers {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val lifetimeService: LifetimeService?
        get() = ThreemaApplication.getServiceManager()?.lifetimeService

    @JvmStatic
    fun registerBroadcastReceivers(context: Context) {
        registerConnectivityChangeReceiver(context)
        registerDeviceIdleModeChangedReceiver(context)
        registerNotificationChannelGroupBlockStateChangedReceiver(context)
        registerAppRestrictionsChangeReceiver(context)
    }

    private fun registerConnectivityChangeReceiver(context: Context) {
        // This is called when a change in network connectivity has occurred.
        // Note: This is deprecated on API 28+!
        context.registerReceiver(
            ConnectivityChangeReceiver(),
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
        )
    }

    private fun registerDeviceIdleModeChangedReceiver(context: Context) {
        // This is called when the state of isDeviceIdleMode() changes.
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val powerManager = context.getSystemService<PowerManager>() ?: return
                    if (powerManager.isDeviceIdleMode) {
                        logger.info("*** Device going to deep sleep")

                        GlobalAppState.isDeviceIdle = true

                        coroutineScope.launch {
                            try {
                                // Pause connection
                                lifetimeService?.pause()
                            } catch (e: Exception) {
                                logger.error("Exception while pausing connection", e)
                            }

                            if (BackupService.isRunning()) {
                                context.stopService(Intent(context, BackupService::class.java))
                            }
                        }
                    } else {
                        logger.info("*** Device waking up")
                        coroutineScope.launch {
                            try {
                                lifetimeService?.unpause()
                            } catch (e: Exception) {
                                logger.error("Exception while unpausing connection", e)
                            }
                        }
                        GlobalAppState.isDeviceIdle = false
                    }
                }
            },
            IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED),
        )
    }

    private fun registerNotificationChannelGroupBlockStateChangedReceiver(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }
        // This is called when a NotificationChannelGroup is blocked or unblocked.
        // This broadcast is only sent to the app that owns the channel group that has changed.
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    try {
                        val blockedState = intent.getBooleanExtra(NotificationManager.EXTRA_BLOCKED_STATE, false)
                        val groupName = intent.getStringExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_GROUP_ID)
                        logger.info(
                            "*** Channel group {} blocked: {}",
                            groupName ?: "<not specified>",
                            blockedState,
                        )
                    } catch (e: Exception) {
                        logger.error("Could not get data from intent", e)
                    }
                }
            },
            IntentFilter(NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED),
        )
    }

    private fun registerAppRestrictionsChangeReceiver(context: Context) {
        if (ConfigUtils.isWorkRestricted()) {
            context.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        logger.info("Restrictions have changed. Updating restrictions")
                        AppRestrictionService.getInstance().reload()
                    }
                },
                IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            )
        }
    }
}

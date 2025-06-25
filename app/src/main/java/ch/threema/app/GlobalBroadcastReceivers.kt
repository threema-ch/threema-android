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

import android.annotation.TargetApi
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ch.threema.app.backuprestore.csv.BackupService
import ch.threema.app.receivers.ConnectivityChangeReceiver
import ch.threema.app.receivers.PinningFailureReportBroadcastReceiver
import ch.threema.app.receivers.ShortcutAddedReceiver
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import com.datatheorem.android.trustkit.reporting.BackgroundReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val logger = LoggingUtil.getThreemaLogger("GlobalBroadcastReceivers")

object GlobalBroadcastReceivers {

    @JvmStatic
    fun registerBroadcastReceivers(context: Context) {
        registerConnectivityChangeReceiver(context)
        registerDeviceIdleModeChangedReceiver(context)
        registerNotificationChannelGroupBlockStateChangedReceiver(context)
        registerPinningFailureReportReceiver(context)
        registerAppRestrictionsChangeReceiver(context)
        registerShortcutAddedReceiver(context)
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        // This is called when the state of isDeviceIdleMode() changes.
        context.registerReceiver(
            object : BroadcastReceiver() {
                @TargetApi(Build.VERSION_CODES.M)
                override fun onReceive(context: Context, intent: Intent) {
                    val serviceManager = ThreemaApplication.getServiceManager()

                    val powerManager = context.getSystemService<PowerManager>()
                    if (powerManager != null && powerManager.isDeviceIdleMode) {
                        logger.info("*** Device going to deep sleep")

                        GlobalAppState.isDeviceIdle = true

                        try {
                            // Pause connection
                            serviceManager?.lifetimeService?.pause()
                        } catch (e: Exception) {
                            logger.error("Exception while pausing connection", e)
                        }

                        if (BackupService.isRunning()) {
                            context.stopService(Intent(context, BackupService::class.java))
                        }
                    } else {
                        logger.info("*** Device waking up")
                        if (serviceManager != null) {
                            CoroutineScope(Dispatchers.Default).launch {
                                try {
                                    serviceManager.lifetimeService.unpause()
                                } catch (e: Exception) {
                                    logger.error("Exception while unpausing connection", e)
                                }
                            }
                            GlobalAppState.isDeviceIdle = false
                        } else {
                            logger.info("Service manager unavailable")
                            val masterKey = ThreemaApplication.getMasterKey()
                            if (!masterKey.isLocked) {
                                ThreemaApplication.onMasterKeyUnlocked(masterKey)
                            }
                        }
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

    private fun registerPinningFailureReportReceiver(context: Context) {
        val receiver = PinningFailureReportBroadcastReceiver()
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, IntentFilter(BackgroundReporter.REPORT_VALIDATION_EVENT))
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

    private fun registerShortcutAddedReceiver(context: Context) {
        // register a receiver for shortcuts that have been added to the launcher
        ContextCompat.registerReceiver(
            context,
            ShortcutAddedReceiver(),
            IntentFilter(AppConstants.INTENT_ACTION_SHORTCUT_ADDED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}

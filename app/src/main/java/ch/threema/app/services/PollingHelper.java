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

package ch.threema.app.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;
import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.receivers.FetchMessagesBroadcastReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.connection.ConnectionState;
import ch.threema.domain.taskmanager.QueueSendCompleteListener;

import ch.threema.app.utils.IntentDataUtil;

/**
 * Helper class to simplify polling (both time-based and FCM based) by handling all the gory details
 * of connecting for new messages and ensuring that the connection is released once the server
 * has sent all new messages.
 */
public class PollingHelper implements QueueSendCompleteListener {
    private static final Logger logger = getThreemaLogger("PollingHelper");

    private static final int CONNECTION_TIMEOUT = 1000 * 120;       /* maximum time to stay connected for each poll (usually the connection will be terminated earlier as the server signals the end of the queue) */
    private static final int CONNECTION_TIMEOUT_ALREADY_CONNECTED = 1000 * 60;      /* same, but timeout to use if we're already connected when polling */
    static final int CONNECTION_LINGER = 1000 * 5;          /* linger a bit to allow outgoing delivery receipts to be sent */

    private static volatile Timer timer;     /* same timer for all instances */
    private static final Object timerLock = new Object();

    private final @NonNull Context context;
    private final @NonNull String lifetimeServiceTag;

    private boolean connectionAcquired;
    private PowerManager.WakeLock wakeLock;
    private TimerTask timeoutTask;

    public PollingHelper(@NonNull Context context, @NonNull String lifetimeServiceTag) {
        this.context = context;
        this.lifetimeServiceTag = lifetimeServiceTag;
    }

    /**
     * Return whether polling was successful.
     */
    public synchronized boolean poll(final boolean useWakeLock) {
        logger.info("Fetch attempt. Source = {}", lifetimeServiceTag);

        final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            return false;
        }

        // If the device is not online, there's no use in trying to connect.
        boolean isOnline = serviceManager.getDeviceService().isOnline();
        if (!isOnline) {
            logger.info("Not polling, device is offline");
            return false;
        }

        if (!connectionAcquired) {
            // Check current backup state. If a backup or restore is running, don't poll.
            if (RestoreService.isRunning()) {
                return false;
            }
            if (BackupService.isRunning()) {
                return false;
            }

            // If requested, acquire a wakelock
            if (useWakeLock) {
                logger.info("Aquiring wakelock");
                if (wakeLock == null) {
                    PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
                    wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        BuildConfig.APPLICATION_ID + ":PollingHelper"
                    );
                }
                wakeLock.acquire(DateUtils.MINUTE_IN_MILLIS * 10);
            }

            // We want to be notified when the server signals that the message queue was flushed completely
            serviceManager.getTaskManager().addQueueSendCompleteListener(this);

            // Determine timeout duration. If we're already connected it can be shorter.
            long timeout = CONNECTION_TIMEOUT;
            if (serviceManager.getConnection().getConnectionState() == ConnectionState.LOGGEDIN) {
                logger.info("Already connected");
                timeout = CONNECTION_TIMEOUT_ALREADY_CONNECTED;
            }

            // Acquire a connection to the Threema server
            final LifetimeService lifetimeService = serviceManager.getLifetimeService();
            lifetimeService.acquireConnection(this.lifetimeServiceTag);

            if (!lifetimeService.isActive()) {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(this.context, 0,
                    new Intent(this.context, FetchMessagesBroadcastReceiver.class), IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE);

                // cancel pending alarms
                alarmManager.cancel(pendingIntent);

                // try again in two minutes
                logger.info("Schedule another fetching attempt in two minutes");
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + timeout, pendingIntent);

                // return false
                connectionAcquired = false;
                return false;
            }

            // Polling was successful
            connectionAcquired = true;

            /* schedule a TimerTask so we will release this connection if it's taking too long to receive the queue completion message */
            if (timeoutTask != null)
                timeoutTask.cancel();

            timeoutTask = new TimerTask() {
                @Override
                public void run() {
                    logger.warn("Timeout fetching message. Releasing connection");
                    releaseConnection();
                }
            };

            this.schedule(timeoutTask, timeout);

            return true;
        } else {
            logger.info("Fetch attempt. Connection already acquired.");

            return true;
        }
    }

    private void schedule(TimerTask timerTask, long timeout) {
        if (timer == null) {
            synchronized (timerLock) {
                if (timer == null) {
                    timer = new Timer("PollingHelper");
                }
            }
        }
        timer.schedule(timerTask, timeout);
    }

    public synchronized boolean poll() {
        return poll(true);
    }

    @Override
    public synchronized void queueSendComplete() {
        logger.info("Received queue send complete message from server");
        releaseConnection();
    }

    private synchronized void releaseConnection() {
        logger.debug("release connection");

        if (connectionAcquired) {
            ServiceManager serviceManager = ThreemaApplication.getServiceManager();

            if (serviceManager != null) {
                serviceManager.getTaskManager().removeQueueSendCompleteListener(this);

                final LifetimeService lifetimeService = serviceManager.getLifetimeService();
                lifetimeService.releaseConnectionLinger(this.lifetimeServiceTag, CONNECTION_LINGER);
                connectionAcquired = false;

                if (timeoutTask != null) {
                    timeoutTask.cancel();
                    timeoutTask = null;
                }

                if (wakeLock != null && wakeLock.isHeld()) {
                    logger.info("Releasing wakelock");
                    wakeLock.release();
                }
            } else {
                connectionAcquired = false;
            }
        }
    }
}

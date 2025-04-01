/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.app.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.util.Date;

import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.PollingHelper;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.connection.ServerConnection;
import ch.threema.domain.protocol.connection.ConnectionState;

import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE;

public class AlarmManagerBroadcastReceiver extends BroadcastReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AlarmManagerBroadcastReceiver");

    private static PendingIntent requireLoggedInConnectionIntent = null;

    public final static String EXTRA_REQUEST_CODE = "requestCode";
    public final static String EXTRA_REQUIRE_LOGGED_IN_CONNECTION = "requireLoggedInConnection";
    public final static String EXTRA_NEXT_CHECK = "nextCheck";

    @Override
    public void onReceive(Context context, Intent intent) {
        int requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0);

        logger.info("Alarm type {} received", requestCode);

        if (ThreemaApplication.getServiceManager() != null) {
            try {
                new Thread(() -> {
                    PowerManager.WakeLock wakeLock = null;
                    try {
                        wakeLock = ((PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":AlarmManagerBroadcastReceiver");
                        wakeLock.acquire(DateUtils.MINUTE_IN_MILLIS * 2);
                    } catch (Exception ignore) {
                    }

                    if (intent.hasExtra(EXTRA_REQUIRE_LOGGED_IN_CONNECTION) && intent.getBooleanExtra(EXTRA_REQUIRE_LOGGED_IN_CONNECTION, false)) {
                        final PollingHelper p = new PollingHelper(context, "requireLoggedInConnection");
                        cancelLoggedInConnection(context);
                        if (p.poll(true)) {
                            //recheck!
                            requireLoggedInConnection(context, intent.getIntExtra(EXTRA_NEXT_CHECK, (int) DateUtils.MINUTE_IN_MILLIS) * 2);
                        }
                    } else {
                        long time = System.currentTimeMillis();
                        logger.info("Alarm type {} dispatch to LifetimeService START", requestCode);
                        ThreemaApplication.getServiceManager().getLifetimeService().alarm(intent);
                        logger.info("Alarm type {} dispatch to LifetimeService STOP. Duration = {}ms",
                            requestCode, System.currentTimeMillis() - time);
                    }

                    if (wakeLock != null && wakeLock.isHeld()) {
                        wakeLock.release();
                    }
                }, "AlarmOnReceive").start();
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    public static AlarmManager getAlarmManager(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public static void cancelLoggedInConnection(final Context context) {
        if (requireLoggedInConnectionIntent != null) {
            AlarmManager alarmManager = getAlarmManager(context);
            if (alarmManager != null) {
                logger.debug("cancel cancelLoggedInConnection");
                alarmManager.cancel(requireLoggedInConnectionIntent);
                requireLoggedInConnectionIntent = null;
            }
        }
    }

    public static void requireLoggedInConnection(final Context context, final int milliseconds) {
        logger.debug("requireLoggedInConnection");
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            final Date now = new Date();
            ServerConnection connection = serviceManager.getConnection();
            LifetimeService lifetimeService = serviceManager.getLifetimeService();

            if (connection != null && connection.getConnectionState() != ConnectionState.LOGGEDIN) {
                if (lifetimeService != null) {
                    lifetimeService.addListener(() -> {
                        if (ThreemaApplication.getLastLoggedIn() == null ||
                            ThreemaApplication.getLastLoggedIn().before(now)) {
                            //schedule a alarm!
                            logger.info("could not login to threema server, try again in {} milliseconds", milliseconds * 2);

                            //cancel all other pending intents
                            cancelLoggedInConnection(context);

                            //set alarm
                            AlarmManager alarmManager = getAlarmManager(context);
                            Intent intent = new Intent(context, AlarmManagerBroadcastReceiver.class);
                            intent.putExtra(EXTRA_REQUIRE_LOGGED_IN_CONNECTION, true);
                            intent.putExtra(EXTRA_NEXT_CHECK, milliseconds * 2);

                            requireLoggedInConnectionIntent = PendingIntent.getBroadcast(context, 0, intent, PENDING_INTENT_FLAG_MUTABLE);
                            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + milliseconds, requireLoggedInConnectionIntent);
                        }

                        return true;
                    });
                }
            }
        }
    }

}

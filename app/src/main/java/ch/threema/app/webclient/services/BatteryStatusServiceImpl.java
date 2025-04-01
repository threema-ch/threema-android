/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.webclient.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import androidx.core.content.ContextCompat;
import ch.threema.app.utils.BatteryStatusUtil;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.WebClientSessionModel;

/**
 * Handling the WebClient battery status subscription.
 * <p>
 * On change, the BatteryStatusListeners will be notified.
 */
@WorkerThread
public class BatteryStatusServiceImpl implements BatteryStatusService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BatteryStatusServiceImpl");

    // State
    @NonNull
    private final Context appContext;
    @NonNull
    private final List<Integer> acquiredSessionIds = new ArrayList<>();
    private boolean subscribed = false;

    /**
     * Battery status broadcast receiver.
     */
    @NonNull
    private final BroadcastReceiver batteryStatusReceiver = new BroadcastReceiver() {
        // Battery info
        private int batteryPercent = -1;
        @Nullable
        private Boolean isCharging = null;

        @Override
        public void onReceive(Context context, Intent intent) {
            // Process data
            final Boolean charging = BatteryStatusUtil.isCharging(intent);
            final Integer percent = BatteryStatusUtil.getPercent(intent);
            if (charging == null || percent == null) {
                return;
            }

            // Determine whether there was a relative change
            final boolean percentChanged = (percent != this.batteryPercent);
            final boolean isChargingChanged = (charging != this.isCharging);

            // If it is, notify listeners
            if (percentChanged || isChargingChanged) {
                WebClientListenerManager.batteryStatusListener.handle(
                    listener -> listener.onChange(percent, charging));
                this.batteryPercent = percent;
                this.isCharging = charging;
            }
        }
    };

    @AnyThread
    public BatteryStatusServiceImpl(@NonNull Context appContext) {
        this.appContext = appContext;
    }

    /**
     * Subscribe to the battery status broadcast.
     */
    @Override
    public void acquire(WebClientSessionModel session) {
        logger.debug("Acquire webclient battery status subscription for session {}", session.getId());
        if (!this.acquiredSessionIds.contains(session.getId())) {
            this.acquiredSessionIds.add(session.getId());
        }
        this.execute();
    }

    /**
     * Unsubscribe from the battery status broadcast.
     */
    @Override
    public void release(WebClientSessionModel session) {
        logger.debug("Release webclient battery status subscription for session {}", session.getId());
        if (this.acquiredSessionIds.contains(session.getId())) {
            this.acquiredSessionIds.remove((Integer) session.getId());
        }
        this.execute();
    }

    private void execute() {
        if (!this.acquiredSessionIds.isEmpty()) {
            if (this.subscribed) {
                logger.debug("Already subscribed");
            } else {
                ContextCompat.registerReceiver(
                    this.appContext,
                    this.batteryStatusReceiver,
                    getBatteryStatusIntentFilter(),
                    // Note that for some system broadcasts like this one, the receiver does not
                    // need to be exported.
                    ContextCompat.RECEIVER_NOT_EXPORTED
                );
                this.subscribed = true;
                logger.debug("Subscribed");
            }
        } else {
            if (this.subscribed) {
                this.appContext.unregisterReceiver(this.batteryStatusReceiver);
                this.subscribed = false;
                logger.debug("Unsubscribed");
            } else {
                logger.debug("Already unsubscribed");
            }
        }
    }

    /**
     * Return the intent filter for subscribing to battery changes.
     */
    public static IntentFilter getBatteryStatusIntentFilter() {
        final IntentFilter batteryStatusFilter = new IntentFilter();
        batteryStatusFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        batteryStatusFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        batteryStatusFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        batteryStatusFilter.addAction(Intent.ACTION_BATTERY_LOW);
        batteryStatusFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        return batteryStatusFilter;
    }

}

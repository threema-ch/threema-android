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


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;

import org.slf4j.Logger;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.workers.ConnectivityChangeWorker;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.ThreemaApplication.WORKER_CONNECTIVITY_CHANGE;

public class ConnectivityChangeReceiver extends BroadcastReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ConnectivityChangeReceiver");

    @Override
    public void onReceive(Context context, Intent intent) {
        logger.debug("Connectivity change broadcast received");
        try {
            String networkState = "UNKNOWN";
            Bundle extras = intent.getExtras();
            if (extras != null) {
                NetworkInfo networkInfo = (NetworkInfo) extras.get(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    networkState = networkInfo.toString();
                    logger.info(networkState);
                }
            }

            OneTimeWorkRequest workRequest = ConnectivityChangeWorker.Companion.buildOneTimeWorkRequest(networkState);
            WorkManager.getInstance(ThreemaApplication.getAppContext()).enqueueUniqueWork(WORKER_CONNECTIVITY_CHANGE, ExistingWorkPolicy.REPLACE, workRequest);
        } catch (IllegalStateException e) {
            logger.error("Unable to schedule connectivity change work", e);
        }
    }
}

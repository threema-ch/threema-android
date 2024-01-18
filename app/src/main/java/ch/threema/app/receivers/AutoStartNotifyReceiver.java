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

import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

import ch.threema.app.workers.AutostartWorker;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.ThreemaApplication.WORKER_AUTOSTART;

public class AutoStartNotifyReceiver extends BroadcastReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("AutoStartNotifyReceiver");

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			logger.info("*** Phone rebooted - AutoStart");
			OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(AutostartWorker.class)
				.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
				.build();
			WorkManager.getInstance(context).enqueueUniqueWork(WORKER_AUTOSTART, ExistingWorkPolicy.REPLACE, workRequest);
		}
	}
}

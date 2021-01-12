/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.app.jobs;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.RequiresApi;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.PollingHelper;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ReConnectJobService extends JobService {
	private static final Logger logger = LoggerFactory.getLogger(ReConnectJobService.class);

	private PollingHelper pollingHelper = null;

	@Override
	public boolean onStartJob(final JobParameters jobParameters) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				logger.info("Scheduling poll on reconnect");

				if (pollingHelper == null) {
					pollingHelper = new PollingHelper(ReConnectJobService.this, "reConnect");
				}

				boolean success = pollingHelper.poll(true) || (ThreemaApplication.getMasterKey() != null && ThreemaApplication.getMasterKey().isLocked());
				jobFinished(jobParameters, !success);
			}
		}, "ReConnectJobService").start();

		return true;
	}

	@Override
	public boolean onStopJob(JobParameters jobParameters) {
		return false;
	}
}

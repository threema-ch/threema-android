/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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

import androidx.work.Configuration;

import org.slf4j.Logger;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.PollingHelper;
import ch.threema.base.utils.LoggingUtil;

public class ReConnectJobService extends JobService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ReConnectJobService");
	private static boolean isStopped;
	private PollingHelper pollingHelper = null;

	public ReConnectJobService() {
		new Configuration.Builder().setJobSchedulerJobIdRange(20000, 21000).build();
	}

	@Override
	public boolean onStartJob(final JobParameters jobParameters) {
		logger.info("Reconnect job {} started", jobParameters.getJobId());

		isStopped = false;

		new Thread(new Runnable() {
			@Override
			public void run() {
				logger.info("Scheduling poll on reconnect");

				if (pollingHelper == null) {
					pollingHelper = new PollingHelper(ReConnectJobService.this, "reconnectJobService");
				}

				if (!isStopped) {
					boolean success = pollingHelper.poll(true) || (ThreemaApplication.getMasterKey() != null && ThreemaApplication.getMasterKey().isLocked());

					if (!isStopped) {
						try {
							jobFinished(jobParameters, !success);
							logger.info("Reconnect job {} finished. Success = {}", jobParameters.getJobId(), success);
						} catch (Exception e) {
							logger.error("Exception while finishing ReConnectJob", e);
						}
					}
				}
			}
		}, "ReConnectJobService").start();

		return true;
	}

	@Override
	public boolean onStopJob(JobParameters jobParameters) {
		isStopped = true;
		logger.info("Reconnect job {} stopped", jobParameters.getJobId());

		return false;
	}
}

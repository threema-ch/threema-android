/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.services.ShortcutService;

public class ShareTargetShortcutUpdateJobService extends JobService {
	private static final Logger logger = LoggerFactory.getLogger(ShareTargetShortcutUpdateJobService.class);

	@Override
	public boolean onStartJob(JobParameters jobParameters) {
		if (BackupService.isRunning()) {
			jobFinished(jobParameters, false);

			return true;
		}

		new Thread(() -> {
			logger.info("Updating share target shortcuts");

			ShortcutService shortcutService;
			try {
				shortcutService = ThreemaApplication.getServiceManager().getShortcutService();
				if (shortcutService != null) {
					shortcutService.deleteAllShareTargetShortcuts();
					shortcutService.publishRecentChatsAsShareTargets();
				}
			} catch (Exception e) {
				logger.error("Exception, failed to update share target shortcuts", e);
			}

			jobFinished(jobParameters, false);
		}, "ShareTargetShortcutUpdateJobService").start();

		return true;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		return false;
	}
}

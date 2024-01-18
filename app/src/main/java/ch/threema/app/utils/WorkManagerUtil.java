/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

package ch.threema.app.utils;

import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import ch.threema.base.utils.LoggingUtil;

public class WorkManagerUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("WorkManagerUtil");

	/**
	 * Check if periodic work with provided tag is already scheduled or running and has the same schedule period.
	 * Cancel existing work in case of error
	 * @param workManager An instance of the WorkManager
	 * @param tag Unique work name
	 * @param schedulePeriod scheduled period of this work
	 * @return true if no periodic work with the same tag exists or the existing work has a different schedule period;
	 *      false if the work already exists and has the same schedule period
	 */
	public static boolean shouldScheduleNewWorkManagerInstance(WorkManager workManager, String tag, long schedulePeriod) {
		// check if work is already scheduled or running, if yes, do not attempt launch a new request
		ListenableFuture<List<WorkInfo>> workInfos = workManager.getWorkInfosForUniqueWork(tag);
		try {
			List<WorkInfo> workInfoList = workInfos.get();
			for (WorkInfo workInfo : workInfoList) {
				WorkInfo.State state = workInfo.getState();
				if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
					logger.debug("a job of the same name is already running or queued");
					Set<String> tags = workInfo.getTags();
					if (tags.size() > 0 && tags.contains(String.valueOf(schedulePeriod))) {
						logger.debug("job has same schedule period");
						return false;
					} else {
						logger.debug("job has a different schedule period");
						break;
					}
				}
			}
		} catch (Exception e) {
			logger.info("WorkManager Exception");
			workManager.cancelUniqueWork(tag);
		}
		return true;
	}

	public static boolean isWorkManagerInstanceScheduled(WorkManager workManager, String tag) {
		ListenableFuture<List<WorkInfo>> workInfos = workManager.getWorkInfosForUniqueWork(tag);
		try {
			List<WorkInfo> workInfoList = workInfos.get();
			for (WorkInfo workInfo : workInfoList) {
				WorkInfo.State state = workInfo.getState();
				if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED) {
					return true;
				}
			}
		} catch (ExecutionException | InterruptedException e) {
			logger.error("Could not get work info", e);
			return false;
		}
		return false;
	}

}

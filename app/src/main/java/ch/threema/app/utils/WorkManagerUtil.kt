/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.utils

import android.content.Context
import android.text.format.DateUtils
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import ch.threema.base.utils.LoggingUtil
import java.util.concurrent.ExecutionException

private val logger = LoggingUtil.getThreemaLogger("WorkManagerUtil")

object WorkManagerUtil {
    @JvmStatic
    fun cancelUniqueWork(context: Context, uniqueWorkName: String): Operation {
        logger.info("Cancel unique work '{}'", uniqueWorkName)
        return WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
    }

    suspend fun cancelUniqueWorkAwait(context: Context, uniqueWorkName: String) {
        logger.info("Cancel result = {}", cancelUniqueWork(context, uniqueWorkName).await())
    }

    /**
     * Check if periodic work with provided [uniqueWorkName] is already scheduled or running and has the same schedule period.
     * Cancel existing work in case of error
     *
     * @param workManager An instance of the WorkManager
     * @param uniqueWorkName Unique work name
     * @param schedulePeriod scheduled period of this work
     * @return true if no periodic work with the same tag exists or the existing work has a different schedule period;
     *      false if the work already exists and has the same schedule period
     */
    @JvmStatic
    fun shouldScheduleNewWorkManagerInstance(workManager: WorkManager, uniqueWorkName: String, schedulePeriod: Long): Boolean {
        return try {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get().none {
                val state = it.state
                if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                    logger.debug("A job of the same name is already running or queued")
                    if (it.tags.contains(schedulePeriod.toString())) {
                        logger.debug("Job has same schedule period")
                        true
                    } else {
                        logger.debug("Job has a different schedule period")
                        false
                    }
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            logger.info("WorkManager Exception")
            workManager.cancelUniqueWork(uniqueWorkName)
            true
        }
    }

    @JvmStatic
    fun isWorkManagerInstanceScheduled(workManager: WorkManager, uniqueWorkName: String): Boolean {
        return try {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get().any {
                val state = it.state
                state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED
            }
        } catch (e: Exception) {
            when (e) {
                is ExecutionException, is InterruptedException -> logger.error("Could not get work info", e)
                else -> throw e
            }
            false
        }
    }

    /**
     * Normalize a schedule period in seconds to milliseconds:
     *
     * When [schedulePeriodS] is <= 0, a period of one day is returned.
     * Otherwise the [schedulePeriodS] is converted to milliseconds.
     *
     * @return The normalized schedule period in milliseconds
     */
    @JvmStatic
    fun normalizeSchedulePeriod(schedulePeriodS: Int): Long {
        return when {
            schedulePeriodS <= 0 -> DateUtils.DAY_IN_MILLIS
            else -> schedulePeriodS * DateUtils.SECOND_IN_MILLIS
        }
    }
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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

package ch.threema.app.workers

import android.content.Context
import android.content.Intent
import androidx.work.*
import ch.threema.base.utils.LoggingUtil
import java.util.concurrent.TimeUnit

class RestartWorker(val appContext: Context, workerParameters: WorkerParameters) :
        Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        logger.debug("Scheduling restart")
        val restartIntent: Intent? = appContext.packageManager
                .getLaunchIntentForPackage(appContext.packageName)
        return if (restartIntent != null) {
            restartIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            appContext.startActivity(restartIntent)
            logger.info("Restart scheduled")
            Result.success()
        } else {
            logger.info("Scheduling restart failed")
            Result.failure()
        }
    }

    companion object {
        fun buildOneTimeWorkRequest(delayMs: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<RestartWorker>()
                    .apply {
                        setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    }
                    .build()
        }
        private val logger = LoggingUtil.getThreemaLogger("RestartWorker")
    }
}

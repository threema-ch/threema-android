/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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
import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.TimeUnit

private val logger = getThreemaLogger("RestartWorker")

class RestartWorker(
    private val appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        logger.info("Restarting the app")
        val restartIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)!!
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        appContext.startActivity(restartIntent)
        return Result.success()
    }

    companion object {
        fun buildOneTimeWorkRequest(delayMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<RestartWorker>()
                .apply {
                    if (delayMs > 0) {
                        setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    }
                }
                .build()
    }
}

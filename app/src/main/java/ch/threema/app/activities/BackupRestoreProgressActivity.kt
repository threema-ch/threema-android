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

package ch.threema.app.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.backuprestore.csv.BackupService
import ch.threema.app.backuprestore.csv.RestoreService
import ch.threema.app.utils.ConfigUtils

/**
 * This activity is shown when the user opens Threema while a backup is being created or restored.
 * This is useful to get a hint about the progress if notifications are not allowed or activated.
 */
class BackupRestoreProgressActivity : AppCompatActivity() {

    private lateinit var titleTextView: TextView
    private lateinit var infoTextView: TextView
    private lateinit var durationDelimiter: View
    private lateinit var durationText: TextView
    private lateinit var backupRestoreProgress: ProgressBar
    private lateinit var closeButton: Button
    private lateinit var progressType: ProgressType

    private enum class ProgressType {
        BACKUP,
        RESTORE,
    }

    private val backupReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra(BackupService.BACKUP_PROGRESS, -1) ?: -1
            val maxSteps = intent?.getIntExtra(BackupService.BACKUP_PROGRESS_STEPS, -1) ?: -1
            val progressMessage = intent?.getStringExtra(BackupService.BACKUP_PROGRESS_MESSAGE)
            val errorMessage = intent?.getStringExtra(BackupService.BACKUP_PROGRESS_ERROR_MESSAGE)

            onProgressUpdate(progress, maxSteps, progressMessage, errorMessage)
        }
    }

    private val restoreReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra(RestoreService.RESTORE_PROGRESS, -1) ?: -1
            val maxSteps = intent?.getIntExtra(RestoreService.RESTORE_PROGRESS_STEPS, -1) ?: -1
            val progressMessage = intent?.getStringExtra(RestoreService.RESTORE_PROGRESS_MESSAGE)
            val errorMessage = intent?.getStringExtra(RestoreService.RESTORE_PROGRESS_ERROR_MESSAGE)

            onProgressUpdate(progress, maxSteps, progressMessage, errorMessage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ConfigUtils.configureSystemBars(this)

        setContentView(R.layout.activity_backup_restore_progress)

        titleTextView = findViewById(R.id.backup_restore_info_title)
        infoTextView = findViewById(R.id.backup_restore_info_summary)
        durationDelimiter = findViewById(R.id.duration_delimiter)
        durationText = findViewById(R.id.duration_text)
        backupRestoreProgress = findViewById(R.id.backup_restore_progress)
        closeButton = findViewById(R.id.close_button)

        closeButton.setOnClickListener { finish() }

        if (BackupService.isRunning()) {
            titleTextView.text = getString(R.string.backup_data_title)
            infoTextView.text = getString(R.string.backup_in_progress)
            backupRestoreProgress.isIndeterminate = true
            progressType = ProgressType.BACKUP
        } else if (RestoreService.isRunning()) {
            titleTextView.text = getString(R.string.restore)
            infoTextView.text = getString(R.string.restoring_backup)
            backupRestoreProgress.isIndeterminate = true
            progressType = ProgressType.RESTORE
        } else {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()

        LocalBroadcastManager.getInstance(ThreemaApplication.getAppContext()).apply {
            registerReceiver(backupReceiver, IntentFilter(BackupService.BACKUP_PROGRESS_INTENT))
            registerReceiver(restoreReceiver, IntentFilter(RestoreService.RESTORE_PROGRESS_INTENT))
        }

        // This is necessary because we might pause the activity while backup/restore and therefore
        // miss the broadcasts.
        if (hasFinished()) {
            onBackupRestoreFinished(null)
        }
    }

    override fun onPause() {
        super.onPause()

        LocalBroadcastManager.getInstance(ThreemaApplication.getAppContext()).apply {
            unregisterReceiver(backupReceiver)
            unregisterReceiver(restoreReceiver)
        }
    }

    private fun hasFinished() = when (progressType) {
        ProgressType.BACKUP -> !BackupService.isRunning()
        ProgressType.RESTORE -> !RestoreService.isRunning()
    }

    private fun onProgressUpdate(
        progress: Int,
        maxSteps: Int,
        progressMessage: String?,
        errorMessage: String?
    ) {
        if (progress >= 0 && maxSteps > 0 && progress <= maxSteps) {
            backupRestoreProgress.isIndeterminate = false
            backupRestoreProgress.progress = progress
            backupRestoreProgress.max = maxSteps
        } else {
            backupRestoreProgress.isIndeterminate = true
        }

        showProgressMessage(progressMessage)

        if (hasFinished() || errorMessage != null) {
            onBackupRestoreFinished(errorMessage)
        }
    }

    private fun showProgressMessage(progressMessage: String?) {
        if (progressMessage != null) {
            durationDelimiter.visibility = View.VISIBLE
            durationText.visibility = View.VISIBLE
            durationText.text = progressMessage
        } else {
            durationDelimiter.visibility = View.INVISIBLE
            durationText.visibility = View.INVISIBLE
        }
    }

    private fun onBackupRestoreFinished(errorMessage: String?) {
        backupRestoreProgress.visibility = View.INVISIBLE

        infoTextView.text = errorMessage
            ?: when (progressType) {
                ProgressType.BACKUP -> getString(R.string.backup_or_restore_success_body)
                ProgressType.RESTORE -> getString(R.string.restore_success_body)
            }

        if (errorMessage != null) {
            closeButton.setText(R.string.close)
            closeButton.setOnClickListener {
                finish()
                cancelCompleteNotification()
            }
        } else {
            closeButton.setText(R.string.ipv6_restart_now)
            closeButton.setOnClickListener {
                ConfigUtils.recreateActivity(this)
                cancelCompleteNotification()
            }
        }
    }

    private fun cancelCompleteNotification() {
        ThreemaApplication.getServiceManager()?.notificationService?.cancel(
            when (progressType) {
                ProgressType.BACKUP -> BackupService.BACKUP_COMPLETION_NOTIFICATION_ID
                ProgressType.RESTORE -> RestoreService.RESTORE_COMPLETION_NOTIFICATION_ID
            }
        )
    }

}

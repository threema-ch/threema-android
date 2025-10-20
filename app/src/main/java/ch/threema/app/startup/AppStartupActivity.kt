/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.startup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.threema.app.R
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.passphrase.PassphraseUnlockContract
import ch.threema.app.startup.components.AppStartupScreen
import ch.threema.app.startup.components.ErrorState
import ch.threema.app.startup.components.LoadingState
import ch.threema.app.startup.components.UnlockRetryDialog
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.ShareUtil
import ch.threema.app.utils.buildActivityIntent
import ch.threema.app.utils.disableEnterTransition
import ch.threema.app.utils.disableExitTransition
import ch.threema.app.utils.getParcelable
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.utils.showToast
import ch.threema.base.utils.LoggingUtil
import ch.threema.localcrypto.MasterKeyManager
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = LoggingUtil.getThreemaLogger("AppStartupActivity")

/**
 * This activity is shown when the app is not (yet) ready to display the screen that the user requested,
 * e.g. because database or system updates need to wrap up first, the master key is locked, or because an irrecoverable error occurred.
 */
class AppStartupActivity : AppCompatActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val appStartupMonitor: AppStartupMonitor by inject()
    private val masterKeyManager: MasterKeyManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableEnterTransition()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                logger.info("Waiting for app to be ready")
                appStartupMonitor.awaitAll()
                logger.info("App is ready")

                returnToOriginalActivityIfNeeded()
            }
        }

        setContent {
            var showLoading by rememberSaveable { mutableStateOf(false) }
            var showUnlockRetryDialog by rememberSaveable { mutableStateOf(false) }
            val masterKeyUnlocker = rememberLauncherForActivityResult(PassphraseUnlockContract) { unlocked ->
                showLoading = false
                if (!unlocked) {
                    showUnlockRetryDialog = true
                }
            }
            val pendingSystems by appStartupMonitor.observePendingSystems().collectAsStateWithLifecycle()
            val errors by appStartupMonitor.observeErrors().collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                if (errors.isEmpty() && !showUnlockRetryDialog && masterKeyManager.isProtected() && masterKeyManager.isLockedWithPassphrase()) {
                    masterKeyUnlocker.launch()
                }
            }

            ThreemaTheme {
                AppStartupScreen {
                    when {
                        showUnlockRetryDialog -> {
                            UnlockRetryDialog(
                                onConfirm = {
                                    showUnlockRetryDialog = false
                                    masterKeyUnlocker.launch()
                                },
                                onDismissRequest = {
                                    showUnlockRetryDialog = false
                                    finish()
                                },
                            )
                        }
                        errors.isNotEmpty() -> {
                            ErrorState(
                                errors = errors,
                                onClickedExportLogs = ::exportLogs,
                                onClickedRetryRemoteSecrets = RemoteSecretMonitorRetryController::requestRetry,
                            )
                        }
                        pendingSystems.isNotEmpty() -> {
                            LaunchedEffect(showLoading) {
                                if (!showLoading) {
                                    // We delay the displaying of the loading state a bit to avoid it flickering up,
                                    // as in most cases it would only be visible very briefly anyway.
                                    delay(500.milliseconds)
                                    showLoading = true
                                }
                            }
                            if (showLoading) {
                                LoadingState(pendingSystems)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun returnToOriginalActivityIfNeeded() {
        try {
            val originalIntent = intent.getOriginalIntent()
            if (!IntentDataUtil.hideAfterUnlock(originalIntent)) {
                startActivity(originalIntent)
            }
        } catch (e: Exception) {
            // In some cases, re-invoking the original intent isn't so straightforward. E.g. when a file was shared with the app,
            // by the time the app is ready we might no longer have permission to access the shared file, which would lead to
            // a SecurityException here. Under the assumption that the AppStartupActivity only has to be shown very rarely,
            // we accept this shortcoming for now, meaning that the user might have to share the file again or navigate
            // to the original activity manually.
            logger.error("Failed to return to original activity", e)
        }
        finish()
        disableExitTransition()
    }

    private fun exportLogs() {
        val success = ShareUtil.shareLogfile(this)
        if (!success) {
            showToast(R.string.try_again)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (appStartupMonitor.hasErrors()) {
            exitProcess(2)
        }
    }

    companion object {
        private const val EXTRA_ORIGINAL_INTENT = "original_intent"

        fun createIntent(context: Context, originalIntent: Intent) =
            buildActivityIntent<AppStartupActivity>(context) {
                putExtra(EXTRA_ORIGINAL_INTENT, originalIntent)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        fun Intent.getOriginalIntent(): Intent = getParcelable(EXTRA_ORIGINAL_INTENT)!!
    }
}

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
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.DialogProperties
import ch.threema.android.buildActivityIntent
import ch.threema.android.context
import ch.threema.android.disableEnterTransition
import ch.threema.app.R
import ch.threema.app.activities.EnterSerialActivity
import ch.threema.app.compose.common.buttons.ButtonPrimary
import ch.threema.app.compose.common.rememberLinkifyWeb
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.framework.EventHandler
import ch.threema.app.framework.WithViewState
import ch.threema.app.startup.components.AppStartupScreen
import ch.threema.app.startup.components.ErrorState
import ch.threema.app.startup.components.LoadingState
import ch.threema.app.startup.models.RemoteSecretUpdateStatus
import ch.threema.app.startup.models.RemoteSecretUpdateType
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import org.koin.androidx.compose.koinViewModel

private val logger = getThreemaLogger("RemoteSecretProtectionUpdateActivity")

/**
 * This activity is started when the Remote Secret feature needs to be activated or deactivated.
 * It shows a loading spinner or error message, and restarts the app once the operation is completed successfully.
 */
class RemoteSecretProtectionUpdateActivity : AppCompatActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableEnterTransition()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel = koinViewModel<RemoteSecretProtectionUpdateViewModel>()

            EventHandler(viewModel) { event ->
                when (event) {
                    RemoteSecretProtectionUpdateViewModelEvent.Done -> {
                        ConfigUtils.scheduleAppRestart(context)
                    }
                    RemoteSecretProtectionUpdateViewModelEvent.PromptForCredentials -> {
                        startActivity(EnterSerialActivity.createIntent(context))
                        finish()
                    }
                }
            }

            ThreemaTheme {
                RemoteSecretActivationScreen(viewModel)
            }
        }
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<RemoteSecretProtectionUpdateActivity>(context)
    }
}

@Composable
private fun RemoteSecretActivationScreen(
    viewModel: RemoteSecretProtectionUpdateViewModel,
) {
    WithViewState(viewModel) { state ->
        if (state != null) {
            RemoteSecretActivationScreen(
                updateType = state.updateType,
                status = state.status,
                onClickedRetry = {
                    logger.info("Retry button clicked")
                    viewModel.onClickedRetry()
                },
                onDismissedDialog = {
                    logger.info("Info dialog dismissed")
                    viewModel.onDismissedDialog()
                },
            )
        }
    }
}

@Composable
private fun RemoteSecretActivationScreen(
    updateType: RemoteSecretUpdateType,
    status: RemoteSecretUpdateStatus,
    onClickedRetry: () -> Unit,
    onDismissedDialog: () -> Unit,
) {
    AppStartupScreen {
        when (status) {
            RemoteSecretUpdateStatus.FAILED -> ErrorState(
                message = when (updateType) {
                    RemoteSecretUpdateType.ACTIVATING -> stringResource(R.string.remote_secret_activating_failed)
                    RemoteSecretUpdateType.DEACTIVATING -> stringResource(R.string.remote_secret_deactivating_failed)
                },
            ) {
                ButtonPrimary(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClickedRetry,
                    text = stringResource(R.string.retry),
                    maxLines = 2,
                )
            }
            RemoteSecretUpdateStatus.SUCCEEDED -> {
                val faqEntryUrl = stringResource(R.string.remote_secret_learn_more_url)

                when (updateType) {
                    RemoteSecretUpdateType.ACTIVATING -> InfoDialog(
                        title = stringResource(R.string.remote_secret_activated_notification_title),
                        text = stringResource(R.string.remote_secret_activated_dialog_content)
                            .rememberLinkifyWeb(faqEntryUrl),
                        onDismissRequest = onDismissedDialog,
                    )
                    RemoteSecretUpdateType.DEACTIVATING -> InfoDialog(
                        title = stringResource(R.string.remote_secret_deactivated_notification_title),
                        text = stringResource(R.string.remote_secret_deactivated_dialog_content)
                            .rememberLinkifyWeb(faqEntryUrl),
                        onDismissRequest = onDismissedDialog,
                    )
                }
            }
            RemoteSecretUpdateStatus.IN_PROGRESS -> LoadingState(
                message = when (updateType) {
                    RemoteSecretUpdateType.ACTIVATING -> stringResource(R.string.remote_secret_activating)
                    RemoteSecretUpdateType.DEACTIVATING -> stringResource(R.string.remote_secret_deactivating)
                },
            )
            RemoteSecretUpdateStatus.IDLE -> Unit
        }
    }
}

@Composable
private fun InfoDialog(
    title: String,
    text: AnnotatedString,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        properties = DialogProperties(
            dismissOnClickOutside = false,
        ),
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text(text = stringResource(R.string.ok))
            }
        },
        title = {
            Text(title)
        },
        text = {
            Text(text)
        },
    )
}

@PreviewThreemaAll
@Composable
private fun RemoteSecretActivationScreen_Preview_Activating() = ThreemaThemePreview {
    RemoteSecretActivationScreen(
        updateType = RemoteSecretUpdateType.ACTIVATING,
        status = RemoteSecretUpdateStatus.IN_PROGRESS,
        onClickedRetry = {},
        onDismissedDialog = {},
    )
}

@PreviewThreemaAll
@Composable
private fun RemoteSecretActivationScreen_Preview_Activated() = ThreemaThemePreview {
    RemoteSecretActivationScreen(
        updateType = RemoteSecretUpdateType.ACTIVATING,
        status = RemoteSecretUpdateStatus.SUCCEEDED,
        onClickedRetry = {},
        onDismissedDialog = {},
    )
}

@PreviewThreemaAll
@Composable
private fun RemoteSecretActivationScreen_Preview_Error() = ThreemaThemePreview {
    RemoteSecretActivationScreen(
        updateType = RemoteSecretUpdateType.ACTIVATING,
        status = RemoteSecretUpdateStatus.FAILED,
        onClickedRetry = {},
        onDismissedDialog = {},
    )
}

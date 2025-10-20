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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.threema.app.R
import ch.threema.app.activities.EnterSerialActivity
import ch.threema.app.compose.common.buttons.ButtonPrimary
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.framework.EventHandler
import ch.threema.app.framework.WithViewState
import ch.threema.app.startup.components.AppStartupScreen
import ch.threema.app.startup.components.ErrorState
import ch.threema.app.startup.components.LoadingState
import ch.threema.app.startup.models.RemoteSecretUpdateType
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.buildActivityIntent
import ch.threema.app.utils.context
import ch.threema.app.utils.disableEnterTransition
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil
import org.koin.androidx.compose.koinViewModel

private val logger = LoggingUtil.getThreemaLogger("RemoteSecretProtectionUpdateActivity")

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
                        ConfigUtils.scheduleAppRestart(context, 1000)
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
                hasFailed = state.hasFailed,
                onClickedRetry = {
                    logger.info("Retry button clicked")
                    viewModel.onClickedRetry()
                },
            )
        }
    }
}

@Composable
private fun RemoteSecretActivationScreen(
    updateType: RemoteSecretUpdateType,
    hasFailed: Boolean,
    onClickedRetry: () -> Unit,
) {
    AppStartupScreen {
        when {
            hasFailed -> ErrorState(
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
            else -> LoadingState(
                message = when (updateType) {
                    RemoteSecretUpdateType.ACTIVATING -> stringResource(R.string.remote_secret_activating)
                    RemoteSecretUpdateType.DEACTIVATING -> stringResource(R.string.remote_secret_deactivating)
                },
            )
        }
    }
}

@PreviewThreemaAll
@Composable
private fun RemoteSecretActivationScreen_Preview_Activating() = ThreemaThemePreview {
    RemoteSecretActivationScreen(
        updateType = RemoteSecretUpdateType.ACTIVATING,
        hasFailed = false,
        onClickedRetry = {},
    )
}

@PreviewThreemaAll
@Composable
private fun RemoteSecretActivationScreen_Preview_Error() = ThreemaThemePreview {
    RemoteSecretActivationScreen(
        updateType = RemoteSecretUpdateType.ACTIVATING,
        hasFailed = true,
        onClickedRetry = {},
    )
}

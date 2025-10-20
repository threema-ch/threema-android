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

package ch.threema.app.startup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.DynamicSpacerSize4
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonPrimary
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.startup.AppStartupError
import ch.threema.app.utils.ConfigUtils

@Composable
fun ErrorState(
    errors: Set<AppStartupError>,
    onClickedExportLogs: () -> Unit,
    onClickedRetryRemoteSecrets: () -> Unit,
) {
    val errorCodes = errors.mapNotNull { error -> (error as? AppStartupError.Unexpected)?.code }

    if (errorCodes.isNotEmpty()) {
        UnexpectedErrorState(
            errorCodes = errorCodes,
            onClickedExportLogs = onClickedExportLogs,
        )
    } else if (AppStartupError.BlockedByAdmin in errors) {
        BlockedByAdminState()
    } else if (AppStartupError.FailedToFetchRemoteSecret in errors) {
        FailedToFetchRemoteSecretState(
            onClickedRetry = onClickedRetryRemoteSecrets,
        )
    }
}

@Composable
private fun UnexpectedErrorState(
    errorCodes: Collection<String>,
    onClickedExportLogs: () -> Unit,
) {
    ErrorState(
        message = stringResource(R.string.an_error_occurred),
        details = errorCodes.joinToString(),
    ) {
        ButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickedExportLogs,
            text = stringResource(R.string.prefs_exportlog),
            maxLines = 2,
        )
    }
}

@Composable
private fun BlockedByAdminState() {
    ErrorState(
        showIcon = false,
        message = stringResource(R.string.remote_secrets_blocked_by_admin),
    )
}

@Composable
private fun FailedToFetchRemoteSecretState(
    onClickedRetry: () -> Unit,
) {
    ErrorState(
        message = stringResource(R.string.remote_secrets_failed_to_fetch),
    ) {
        ButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickedRetry,
            text = stringResource(R.string.retry),
            maxLines = 2,
        )
    }
}

@Composable
fun ErrorState(
    message: String,
    details: String? = null,
    showIcon: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showIcon) {
            Icon(
                modifier = Modifier.size(120.dp),
                painter = painterResource(R.drawable.ic_error_rounded),
                contentDescription = null,
                tint = Color(ConfigUtils.getColorFromAttribute(context, R.attr.colorError)),
            )

            Spacer(Modifier.height(GridUnit.x6))
        }

        ThemedText(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (details != null) {
            Spacer(modifier = Modifier.height(GridUnit.x2))
            ThemedText(
                text = details,
                maxLines = 2,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        content?.let {
            Spacer(modifier = Modifier.height(GridUnit.x2))
            content()
        }

        DynamicSpacerSize4()
    }
}

@PreviewThreemaAll
@Composable
private fun AppStartupScreen_Preview_BlockedByAdmin() = ThreemaThemePreview {
    AppStartupScreen {
        ErrorState(
            errors = setOf(AppStartupError.BlockedByAdmin),
            onClickedRetryRemoteSecrets = {},
            onClickedExportLogs = {},
        )
    }
}

@PreviewThreemaAll
@Composable
private fun AppStartupScreen_Preview_FailedToFetchRemoteSecrets() = ThreemaThemePreview {
    AppStartupScreen {
        ErrorState(
            errors = setOf(AppStartupError.FailedToFetchRemoteSecret),
            onClickedRetryRemoteSecrets = {},
            onClickedExportLogs = {},
        )
    }
}

@PreviewThreemaAll
@Composable
private fun AppStartupScreen_Preview_UnexpectedErrors() = ThreemaThemePreview {
    AppStartupScreen {
        ErrorState(
            errors = setOf(
                AppStartupError.Unexpected("PW-123"),
                AppStartupError.Unexpected("ABC"),
            ),
            onClickedRetryRemoteSecrets = {},
            onClickedExportLogs = {},
        )
    }
}

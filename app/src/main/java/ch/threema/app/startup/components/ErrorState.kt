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
import ch.threema.app.compose.common.buttons.primary.ButtonPrimary
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.startup.AppStartupError
import ch.threema.app.utils.ConfigUtils

@Composable
fun ErrorState(
    errors: Set<AppStartupError>,
    onClickExportLogs: () -> Unit,
    onClickCloseApp: () -> Unit,
    onClickRetryRemoteSecrets: () -> Unit,
) {
    val errorCodes = errors.mapNotNull { error -> (error as? AppStartupError.Unexpected)?.code }

    if (errorCodes.isNotEmpty()) {
        UnexpectedErrorState(
            errorCodes = errorCodes,
            onClickExportLogs = onClickExportLogs,
        )
    } else if (AppStartupError.BlockedByAdmin in errors) {
        BlockedByAdminState(
            onClickCloseApp = onClickCloseApp,
        )
    } else if (AppStartupError.FailedToFetchRemoteSecret in errors) {
        FailedToFetchRemoteSecretState(
            onClickRetry = onClickRetryRemoteSecrets,
        )
    }
}

@Composable
private fun UnexpectedErrorState(
    errorCodes: Collection<String>,
    onClickExportLogs: () -> Unit,
) {
    ErrorState(
        message = stringResource(R.string.an_error_occurred),
        details = errorCodes.joinToString(),
    ) {
        ButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickExportLogs,
            text = stringResource(R.string.prefs_exportlog),
            maxLines = 2,
        )
    }
}

@Composable
private fun BlockedByAdminState(
    onClickCloseApp: () -> Unit,
) {
    ErrorState(
        showIcon = false,
        message = stringResource(R.string.remote_secrets_blocked_by_admin),
    ) {
        ButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickCloseApp,
            text = stringResource(R.string.remote_secrets_close_app),
            maxLines = 2,
        )
    }
}

@Composable
private fun FailedToFetchRemoteSecretState(
    onClickRetry: () -> Unit,
) {
    ErrorState(
        message = stringResource(R.string.remote_secrets_failed_to_fetch),
    ) {
        ButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickRetry,
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
            onClickRetryRemoteSecrets = {},
            onClickCloseApp = {},
            onClickExportLogs = {},
        )
    }
}

@PreviewThreemaAll
@Composable
private fun AppStartupScreen_Preview_FailedToFetchRemoteSecrets() = ThreemaThemePreview {
    AppStartupScreen {
        ErrorState(
            errors = setOf(AppStartupError.FailedToFetchRemoteSecret),
            onClickRetryRemoteSecrets = {},
            onClickCloseApp = {},
            onClickExportLogs = {},
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
            onClickRetryRemoteSecrets = {},
            onClickCloseApp = {},
            onClickExportLogs = {},
        )
    }
}

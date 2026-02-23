package ch.threema.app.startup.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ch.threema.app.R
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaThemePreview

@Composable
fun UnlockRetryDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(text = stringResource(R.string.try_again))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.master_key_locked),
            )
        },
        text = {
            Text(
                text = stringResource(R.string.master_key_locked_want_exit),
            )
        },
    )
}

@PreviewThreemaAll
@Composable
private fun UnlockRetryDialog_Preview() = ThreemaThemePreview {
    AppStartupScreen {
        UnlockRetryDialog(
            onDismissRequest = {},
            onConfirm = {},
        )
    }
}

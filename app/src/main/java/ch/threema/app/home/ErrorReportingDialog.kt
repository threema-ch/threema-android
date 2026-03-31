package ch.threema.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.view.isVisible
import ch.threema.app.R
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.errorreporting.ErrorReportingHelper
import ch.threema.app.preference.service.PreferenceService
import kotlinx.coroutines.launch

class ErrorReportingDialog(
    private val errorReportingHelper: ErrorReportingHelper,
    private val preferenceService: PreferenceService,
) {
    fun showDialog(activity: HomeActivity) {
        activity.findViewById<ComposeView>(R.id.error_report_dialog_compose_view).apply {
            isVisible = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var dialogVisible by rememberSaveable {
                    mutableStateOf(true)
                }
                var rememberChoice by rememberSaveable {
                    mutableStateOf(
                        preferenceService.getErrorReportingState() != PreferenceService.ErrorReportingState.ALWAYS_ASK,
                    )
                }
                if (!dialogVisible) {
                    return@setContent
                }

                val coroutineScope = rememberCoroutineScope()
                ThreemaTheme {
                    ErrorReportingDialog(
                        rememberChoice = rememberChoice,
                        onChangeRememberChoice = { newValue ->
                            rememberChoice = newValue
                        },
                        onConfirm = {
                            dialogVisible = false
                            preferenceService.setErrorReportingState(
                                if (rememberChoice) {
                                    PreferenceService.ErrorReportingState.ALWAYS_SEND
                                } else {
                                    PreferenceService.ErrorReportingState.ALWAYS_ASK
                                },
                            )
                            coroutineScope.launch {
                                errorReportingHelper.confirmRecordsAndScheduleSending()
                            }
                        },
                        onDismiss = {
                            dialogVisible = false
                            preferenceService.setErrorReportingState(
                                if (rememberChoice) {
                                    PreferenceService.ErrorReportingState.NEVER_SEND
                                } else {
                                    PreferenceService.ErrorReportingState.ALWAYS_ASK
                                },
                            )
                            coroutineScope.launch {
                                errorReportingHelper.deletePendingRecords()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorReportingDialog(
    rememberChoice: Boolean,
    onChangeRememberChoice: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(
                stringResource(R.string.error_detected_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.crash_detected_dialog_text),
                    style = MaterialTheme.typography.bodyMedium,
                )

                SpacerVertical(height = GridUnit.x2)

                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(
                            value = rememberChoice,
                            onValueChange = { onChangeRememberChoice(!rememberChoice) },
                            role = Role.Checkbox,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(GridUnit.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = null,
                    )
                    Text(
                        text = stringResource(R.string.crash_detected_dialog_remember_choice),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(text = stringResource(R.string.crash_detected_dialog_send_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}

@PreviewThreemaAll
@Composable
private fun ErrorReportingDialog_Preview() {
    ThreemaThemePreview {
        ErrorReportingDialog(
            rememberChoice = false,
            onChangeRememberChoice = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

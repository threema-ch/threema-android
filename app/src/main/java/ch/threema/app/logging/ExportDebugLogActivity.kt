package ch.threema.app.logging

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import ch.threema.android.disableEnterTransition
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.primary.ButtonPrimary
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.usecases.ShareDebugLogUseCase
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ExportDebugLogActivity")

class ExportDebugLogActivity : AppCompatActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val shareDebugLogUseCase: ShareDebugLogUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableEnterTransition()
        super.onCreate(savedInstanceState)

        setContent {
            ThreemaTheme {
                ExportDebugLogScreen(
                    onClickExportLogs = ::exportLogs,
                )
            }
        }
    }

    private fun exportLogs() {
        logger.info("Export logs button clicked")
        lifecycleScope.launch {
            try {
                shareDebugLogUseCase.call()
            } catch (e: Exception) {
                logger.error("Failed to export debug log", e)
                showToast(R.string.an_error_occurred)
            }
        }
    }
}

@Composable
private fun ExportDebugLogScreen(
    onClickExportLogs: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = dimensionResource(R.dimen.grid_unit_x4))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GridUnit.x4, Alignment.CenterVertically),
    ) {
        ThemedText(
            text = stringResource(R.string.export_logs_screen_warning),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        ButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickExportLogs,
            text = stringResource(R.string.prefs_exportlog),
        )
    }
}

@Preview
@Composable
@PreviewThreemaAll
private fun ExportDebugLogScreen_Preview() = ThreemaThemePreview {
    ExportDebugLogScreen(
        onClickExportLogs = {},
    )
}

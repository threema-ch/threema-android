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
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.utils.ConfigUtils

@Composable
fun ErrorState(
    errorCodes: Set<AppStartupMonitor.AppStartupError>,
    onClickedExportLogs: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier.size(120.dp),
            painter = painterResource(R.drawable.ic_error_rounded),
            contentDescription = null,
            tint = Color(ConfigUtils.getColorFromAttribute(context, R.attr.colorError)),
        )

        Spacer(Modifier.height(GridUnit.x6))

        ThemedText(
            text = stringResource(R.string.an_error_occurred),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(GridUnit.x2))

        ThemedText(
            text = errorCodes.joinToString { it.code },
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(GridUnit.x2))

        ButtonPrimary(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickedExportLogs,
            text = stringResource(R.string.prefs_exportlog),
            maxLines = 2,
        )

        DynamicSpacerSize4()
    }
}

@PreviewThreemaAll
@Composable
private fun AppStartupScreenOneErrorPreview() = ThreemaThemePreview {
    AppStartupScreen {
        ErrorState(
            errorCodes = setOf(AppStartupMonitor.AppStartupError("PW-123")),
            onClickedExportLogs = {},
        )
    }
}

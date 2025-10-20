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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.startup.models.AppSystem

@Composable
fun LoadingState(
    pendingSystems: Set<AppSystem>,
) {
    assert(pendingSystems.isNotEmpty())
    LoadingState(message = getMessage(pendingSystems))
}

@Composable
fun LoadingState(
    message: String? = null,
) {
    if (message != null) {
        Column(
            modifier = Modifier.widthIn(max = 260.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(GridUnit.x2))

            Text(
                text = stringResource(R.string.please_wait),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }

    CircularProgressIndicator(
        modifier = Modifier.size(300.dp),
    )
}

@Composable
private fun getMessage(pendingSystems: Set<AppSystem>): String? =
    when (pendingSystems.minOf { it }) {
        AppSystem.REMOTE_SECRET -> stringResource(R.string.fetching_remote_secret)
        AppSystem.SERVICE_MANAGER -> null
        // We use the same message for database and system updates, as their distinction is not meaningful to the user
        AppSystem.DATABASE_UPDATES,
        AppSystem.SYSTEM_UPDATES,
        -> stringResource(R.string.updating_system)
    }

@PreviewThreemaAll
@Composable
private fun AppStartupScreenLoadingPreview() = ThreemaThemePreview {
    AppStartupScreen {
        LoadingState(
            pendingSystems = setOf(AppSystem.DATABASE_UPDATES),
        )
    }
}

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

package ch.threema.app.dialogs.loadingtimeout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.threema.app.R
import ch.threema.app.compose.preview.PreviewThreemaPhone
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.ElevationValues
import ch.threema.app.compose.theme.dimens.GridUnit
import kotlin.time.Duration
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingWithTimeoutDialogScreen(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    timeout: Duration,
    @StringRes titleText: Int,
    @StringRes messageText: Int,
    @StringRes messageTextTimeout: Int = messageText,
    @StringRes timeoutButtonText: Int,
) {
    val viewModel: LoadingWithTimeoutDialogViewModel = koinViewModel()

    LaunchedEffect(Unit) {
        viewModel.awaitTimeout(timeout)
    }

    val timeoutReached: Boolean by viewModel.timeoutReached.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
    ) { paddingValues ->

        BasicAlertDialog(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnBackPress = timeoutReached,
                dismissOnClickOutside = timeoutReached,
            ),
        ) {
            LoadingWithTimeoutDialogContent(
                timeoutReached = timeoutReached,
                titleText = titleText,
                messageText = messageText,
                messageTextTimeout = messageTextTimeout,
                timeoutButtonText = timeoutButtonText,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

/**
 *  Once we are not using [LoadingWithTimeoutDialogXml] anymore, this implementation can be removed.
 */
@Composable
fun LoadingWithTimeoutDialog(
    modifier: Modifier = Modifier,
    viewModelStoreOwner: ViewModelStoreOwner,
    onDismissRequest: () -> Unit,
    onTimeoutReachedChanged: (Boolean) -> Unit,
    timeout: Duration,
    @StringRes titleText: Int,
    @StringRes messageText: Int,
    @StringRes messageTextTimeout: Int = messageText,
    @StringRes timeoutButtonText: Int,
) {
    val viewModel: LoadingWithTimeoutDialogViewModel = koinViewModel(
        viewModelStoreOwner = viewModelStoreOwner,
    )

    LaunchedEffect(Unit) {
        viewModel.awaitTimeout(timeout)
    }

    val timeoutReached: Boolean by viewModel.timeoutReached.collectAsStateWithLifecycle()

    LaunchedEffect(timeoutReached) {
        onTimeoutReachedChanged(timeoutReached)
    }

    LoadingWithTimeoutDialogContent(
        modifier = modifier,
        timeoutReached = timeoutReached,
        titleText = titleText,
        messageText = messageText,
        messageTextTimeout = messageTextTimeout,
        timeoutButtonText = timeoutButtonText,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun LoadingWithTimeoutDialogContent(
    modifier: Modifier = Modifier,
    timeoutReached: Boolean,
    @StringRes titleText: Int,
    @StringRes messageText: Int,
    @StringRes messageTextTimeout: Int,
    @StringRes timeoutButtonText: Int,
    onDismissRequest: () -> Unit,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = ElevationValues.Level3,
            pressedElevation = ElevationValues.Level3,
            focusedElevation = ElevationValues.Level4,
            hoveredElevation = ElevationValues.Level4,
            draggedElevation = ElevationValues.Level4,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    top = GridUnit.x3,
                    bottom = GridUnit.x2,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(titleText),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GridUnit.x3),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(GridUnit.x2))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GridUnit.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(GridUnit.x3),
                    strokeWidth = 3.dp,
                )
                Spacer(modifier = Modifier.width(GridUnit.x3))
                Text(
                    text = stringResource(
                        when (timeoutReached) {
                            true -> messageTextTimeout
                            false -> messageText
                        },
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(GridUnit.x2))
            if (timeoutReached) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GridUnit.x2),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                    ) {
                        Text(
                            stringResource(timeoutButtonText),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@PreviewThreemaPhone
@Composable
private fun LoadingWithTimeoutDialogContent_Preview_NoTimeout() {
    ThreemaThemePreview {
        Surface(
            color = Color.Black.copy(alpha = 0.2f),
        ) {
            Box(modifier = Modifier.padding(32.dp)) {
                LoadingWithTimeoutDialogContent(
                    timeoutReached = false,
                    titleText = R.string.creating_group,
                    messageText = R.string.please_wait,
                    messageTextTimeout = R.string.please_wait_timeout,
                    timeoutButtonText = R.string.close,
                    onDismissRequest = {},
                )
            }
        }
    }
}

@PreviewThreemaPhone
@Composable
private fun LoadingWithTimeoutDialogContent_Preview_Timeout() {
    ThreemaThemePreview {
        Surface(
            color = Color.Black.copy(alpha = 0.2f),
        ) {
            Box(modifier = Modifier.padding(32.dp)) {
                LoadingWithTimeoutDialogContent(
                    timeoutReached = true,
                    titleText = R.string.creating_group,
                    messageText = R.string.please_wait_timeout,
                    messageTextTimeout = R.string.please_wait_timeout,
                    timeoutButtonText = R.string.close,
                    onDismissRequest = {},
                )
            }
        }
    }
}

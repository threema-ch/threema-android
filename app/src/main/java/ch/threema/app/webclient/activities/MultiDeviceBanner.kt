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

package ch.threema.app.webclient.activities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit

@Composable
fun MultiDeviceBanner(
    onClick: () -> Unit,
    onClickDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(GridUnit.x2),
        onClick = onClick,
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(
                    start = GridUnit.x2,
                ),
            ) {
                Icon(
                    modifier = Modifier
                        .padding(vertical = GridUnit.x2)
                        .align(Alignment.CenterVertically),
                    painter = painterResource(R.drawable.ic_new_feature),
                    contentDescription = null,
                    tint = LocalContentColor.current,
                )

                Spacer(Modifier.width(GridUnit.x1_5))

                ThemedText(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = GridUnit.x2)
                        .align(Alignment.CenterVertically),
                    text = stringResource(R.string.threema_web_link_to_multi_device_feature_banner_title),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = LocalContentColor.current,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.width(GridUnit.x1))

                IconButton(
                    modifier = Modifier.padding(
                        top = GridUnit.x1,
                        end = GridUnit.x1,
                    ),
                    onClick = onClickDismiss,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_rounded),
                        contentDescription = stringResource(R.string.accessibility_dismiss_hint),
                        tint = LocalContentColor.current,
                    )
                }
            }

            ThemedText(
                modifier = Modifier.padding(
                    start = GridUnit.x2,
                    end = GridUnit.x2,
                    bottom = GridUnit.x2,
                ),
                text = stringResource(R.string.threema_web_link_to_multi_device_feature_banner_body),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@PreviewThreemaAll
@Composable
private fun MultiDeviceBanner_Preview() {
    ThreemaThemePreview {
        MultiDeviceBanner(
            onClick = {},
            onClickDismiss = {},
        )
    }
}

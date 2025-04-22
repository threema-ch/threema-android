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

package ch.threema.app.compose.common.buttons

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.color.AlphaValues
import ch.threema.app.compose.theme.dimens.GridUnit

@Composable
fun ButtonPrimary(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
) {
    Button(
        modifier = Modifier
            .heightIn(min = GridUnit.x6)
            .widthIn(max = 800.dp)
            .fillMaxWidth()
            .then(modifier),
        colors = ButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = AlphaValues.DISABLED_CONTAINER),
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = AlphaValues.DISABLED_ON_CONTAINER),
        ),
        shape = RoundedCornerShape(GridUnit.x1),
        onClick = onClick,
    ) {
        ThemedText(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

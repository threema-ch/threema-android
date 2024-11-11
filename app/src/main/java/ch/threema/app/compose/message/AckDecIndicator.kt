/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.compose.message

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.activities.ContactAckDecState
import ch.threema.app.activities.GroupAckDecState
import ch.threema.app.activities.UserReaction
import ch.threema.app.compose.theme.AppTypography
import ch.threema.app.compose.theme.customColorScheme

@Composable
fun ContactAckDecIndicator(ackDecState: ContactAckDecState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (ackDecState) {
            ContactAckDecState.ACK -> AckIndicator(true)
            ContactAckDecState.DEC -> DecIndicator(true)
            ContactAckDecState.NONE -> Unit
        }
    }
}

@Composable
fun GroupAckDecIndicator(
    ackState: GroupAckDecState,
    decState: GroupAckDecState,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ackState.count > 0) {
            AckIndicator(ackState.userReaction == UserReaction.REACTED)
            Spacer(modifier = Modifier.width(2.dp))
            AckDecCountLabel(ackState.count, MaterialTheme.customColorScheme.ackTint)
        }
        Spacer(modifier = Modifier.width(2.dp))
        if (decState.count > 0) {
            DecIndicator(decState.userReaction == UserReaction.REACTED)
            Spacer(modifier = Modifier.width(2.dp))
            AckDecCountLabel(decState.count, MaterialTheme.customColorScheme.decTint)
        }
    }
}

@Composable
private fun AckIndicator(filled: Boolean) {
    val resource = if (filled) {
        R.drawable.ic_thumb_up_filled
    } else {
        R.drawable.ic_thumb_up_grey600_24dp
    }
    Icon(
        modifier = Modifier
            .padding(bottom = 1.dp)
            .size(16.dp),
        painter = painterResource(resource),
        tint = MaterialTheme.customColorScheme.ackTint,
        contentDescription = stringResource(R.string.cd_ack_icon)
    )
}

@Composable
private fun DecIndicator(filled: Boolean) {
    val resource = if (filled) {
        R.drawable.ic_thumb_down_filled
    } else {
        R.drawable.ic_thumb_down_grey600_24dp
    }
    Icon(
        modifier = Modifier
            .size(16.dp),
        painter = painterResource(resource),
        tint = MaterialTheme.customColorScheme.decTint,
        contentDescription = stringResource(R.string.cd_dec_icon)
    )
}

@Composable
private fun AckDecCountLabel(count: Int, color: Color) {
    Text(
        modifier = stringResource(R.string.cd_ack_dec_group_count).let { Modifier.semantics { it.format(count) } },
        text = count.toString(),
        style = AppTypography.bodySmall,
        color = color,
    )
}

@Composable
@Preview
private fun GroupAckDecIndicatorPreview() {
    GroupAckDecIndicator(GroupAckDecState(4, UserReaction.REACTED), GroupAckDecState(3, UserReaction.NONE))
}

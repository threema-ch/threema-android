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

package ch.threema.app.contactdetails

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.threema.app.R
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.theme.AppTypography
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import kotlinx.coroutines.flow.MutableStateFlow

class ContactAvailabilityView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private val statusFlow = MutableStateFlow(AvailabilityStatus.AVAILABLE)

    fun setStatus(status: AvailabilityStatus) {
        statusFlow.value = status
    }

    @Composable
    override fun Content() {
        val status by statusFlow.collectAsStateWithLifecycle()

        ThreemaTheme {
            ContactAvailability(status)
        }
    }
}

@Composable
private fun ContactAvailability(status: AvailabilityStatus) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = GridUnit.x1),
        horizontalArrangement = Arrangement.spacedBy(GridUnit.x0_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (status) {
                AvailabilityStatus.AVAILABLE -> Icons.Outlined.CheckCircle
                AvailabilityStatus.UNAVAILABLE -> Icons.Outlined.Block
            },
            tint = when (status) {
                AvailabilityStatus.AVAILABLE -> colorResource(R.color.availability_status_available)
                AvailabilityStatus.UNAVAILABLE -> colorResource(R.color.availability_status_unavailable)
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )

        ThemedText(
            text = when (status) {
                AvailabilityStatus.AVAILABLE -> stringResource(R.string.contact_availability_status_available)
                AvailabilityStatus.UNAVAILABLE -> stringResource(R.string.contact_availability_status_unavailable)
            },
            style = AppTypography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview
@Composable
private fun ContactAvailability_Preview_Available() {
    ThreemaThemePreview {
        ContactAvailability(status = AvailabilityStatus.AVAILABLE)
    }
}

@Preview
@Composable
private fun ContactAvailability_Preview_UnAvailable() {
    ThreemaThemePreview {
        ContactAvailability(status = AvailabilityStatus.UNAVAILABLE)
    }
}

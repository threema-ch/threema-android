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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import ch.threema.app.activities.AckUiModel
import ch.threema.app.activities.ContactAckUiModel
import ch.threema.app.activities.GroupAckUiModel

@Composable
fun MessageStateIndicator(
    ackUiModel: AckUiModel? = null,
    @DrawableRes deliveryIconRes: Int? = null,
    @StringRes deliveryIconContentDescriptionRes: Int? = null,
) {
    when (ackUiModel) {
        is ContactAckUiModel -> {
            ContactAckDecIndicator(ackUiModel.ackDecState)
        }
        is GroupAckUiModel -> {
            GroupAckDecIndicator(ackUiModel.ackState, ackUiModel.decState)
        }

        null -> {
            if (deliveryIconRes != null && deliveryIconContentDescriptionRes != null) {
                DeliveryIndicator(deliveryIconRes = deliveryIconRes, deliveryIconContentDescriptionRes = deliveryIconContentDescriptionRes)
            }
        }
    }

}

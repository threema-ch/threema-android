/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.compose.common.interop

import androidx.compose.ui.platform.ComposeView
import ch.threema.app.activities.MessageDetailsUiModel
import ch.threema.app.activities.MessageTimestampsUiModel
import ch.threema.app.activities.toUiModel
import ch.threema.app.compose.message.CombinedMessageDetailsList
import ch.threema.app.compose.message.MessageBubble
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.webclient.activities.MultiDeviceBanner
import ch.threema.storage.models.AbstractMessageModel

object ComposeJavaBridge {
    fun setContentMessageDetails(
        composeView: ComposeView,
        messageTimestampsUiModel: MessageTimestampsUiModel,
        messageDetailsUiModel: MessageDetailsUiModel,
    ) {
        composeView.setContent {
            ThreemaTheme {
                CombinedMessageDetailsList(
                    messageTimestampsUiModel,
                    messageDetailsUiModel,
                )
            }
        }
    }

    fun setEditModeMessageBubble(
        composeView: ComposeView,
        model: AbstractMessageModel,
    ) {
        val messageBubbleUiState = model.toUiModel()
        composeView.setContent {
            ThreemaTheme {
                MessageBubble(
                    text = messageBubbleUiState.text,
                    isOutbox = messageBubbleUiState.isOutbox,
                )
            }
        }
    }

    fun setMultiDeviceBanner(
        composeView: ComposeView,
        onClick: () -> Unit,
        onClickDismiss: () -> Unit,
    ) {
        composeView.setContent {
            ThreemaTheme {
                MultiDeviceBanner(
                    onClick = onClick,
                    onClickDismiss = onClickDismiss,
                )
            }
        }
    }
}

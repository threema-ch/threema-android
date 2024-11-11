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

package ch.threema.app.compose.common.interop

import androidx.compose.ui.platform.ComposeView
import androidx.preference.PreferenceManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.MessageDetailsUiModel
import ch.threema.app.activities.MessageTimestampsUiModel
import ch.threema.app.activities.toUiModel
import ch.threema.app.compose.message.CombinedMessageDetailsList
import ch.threema.app.compose.message.MessageBubble
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.storage.models.AbstractMessageModel

object ComposeJavaBridge {

    fun setContentMessageDetails(
        composeView: ComposeView,
        messageTimestampsUiModel: MessageTimestampsUiModel,
        messageDetailsUiModel: MessageDetailsUiModel,
    ) {
        composeView.setContent {
            ThreemaTheme(dynamicColor = shouldUseDynamicColors()) {
                CombinedMessageDetailsList(
                    messageTimestampsUiModel,
                    messageDetailsUiModel
                )
            }
        }
    }

    fun setEditModeMessageBubble(
        composeView: ComposeView,
        model: AbstractMessageModel,
        myIdentity: String
    ) {
        val messageBubbleUiState = model.toUiModel(myIdentity)
        composeView.setContent {
            ThreemaTheme(dynamicColor = shouldUseDynamicColors()) {
                MessageBubble(
                    text = messageBubbleUiState.text,
                    isOutbox = messageBubbleUiState.isOutbox,
                )
            }
        }
    }

    private fun shouldUseDynamicColors(): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext())
        return sharedPreferences.getBoolean("pref_dynamic_color", false)
    }
}

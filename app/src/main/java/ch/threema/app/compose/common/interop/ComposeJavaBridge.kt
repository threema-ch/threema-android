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

import androidx.annotation.StringRes
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow
import androidx.core.view.isVisible
import ch.threema.app.R
import ch.threema.app.compose.message.MessageBubble
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.dialogs.loadingtimeout.LoadingWithTimeoutDialogScreen
import ch.threema.app.messagedetails.toUiModel
import ch.threema.app.webclient.activities.MultiDeviceBanner
import ch.threema.storage.models.AbstractMessageModel
import kotlin.time.Duration.Companion.seconds

object ComposeJavaBridge {

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

    /**
     *  Displays (or hides) a blocking alert dialog (can not be dismissed) as long as the timeout has not yet been reached.
     *  After the timeout was reached the alert dialog can be dismissed by the user.
     *
     *  Note: Since the composable [LoadingWithTimeoutDialogScreen] is not designed to ba a child of a pooling container, such as `RecyclerView` we
     *  are good to go with just the [DisposeOnDetachedFromWindow] strategy.
     *
     *  @param isVisible Sadly it is not enough to call `composeView.setVisibility(View.GONE)` from outside to hide the displayed dialog window.
     *  This is why we have to effectively call [ComposeView.setContent] with an empty composable. You could also argue that this is the
     *  compose-way of changing the state of the UI.
     *
     *  @param onDismissRequest Hands over the responsibility of dismissing this dialog to the caller.
     *
     *  @throws IllegalArgumentException if [timeoutSeconds] is negative
     */
    @JvmStatic
    fun setLoadingWithTimeoutDialog(
        composeView: ComposeView,
        @StringRes titleText: Int,
        timeoutSeconds: Long,
        onDismissRequest: () -> Unit,
        isVisible: Boolean,
    ) {
        check(timeoutSeconds >= 0L) {
            "Argument timeoutSeconds can not be negative"
        }
        composeView.setViewCompositionStrategy(
            strategy = DisposeOnDetachedFromWindow,
        )
        composeView.setContent {
            if (isVisible) {
                ThreemaTheme {
                    LoadingWithTimeoutDialogScreen(
                        onDismissRequest = onDismissRequest,
                        timeout = timeoutSeconds.seconds,
                        titleText = titleText,
                        messageText = R.string.please_wait,
                        messageTextTimeout = R.string.please_wait_timeout,
                        timeoutButtonText = R.string.close,
                    )
                }
            }
        }
        composeView.isVisible = isVisible
    }
}

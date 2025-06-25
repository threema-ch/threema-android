/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelStoreOwner
import ch.threema.app.R
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.dialogs.ThreemaDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.time.Duration.Companion.seconds

/**
 * This is a hack to use the old [AlertDialog] from the XML world with the contents of [LoadingWithTimeoutDialog].
 *
 * If you can, please use the [LoadingWithTimeoutDialogScreen] directly in your screen.
 */
class LoadingWithTimeoutDialogXml : ThreemaDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): AppCompatDialog {
        val timeoutSeconds: Long? = arguments?.getLong(ARGUMENT_TIMEOUT_SECONDS, -1L)
            ?.takeUnless { it == -1L }
        checkNotNull(timeoutSeconds) { "Argument $ARGUMENT_TIMEOUT_SECONDS is missing" }
        val titleTextRes: Int? = arguments?.getInt(ARGUMENT_TITLE_TEXT, -1)
            ?.takeUnless { it == -1 }
        checkNotNull(titleTextRes) { "Argument $ARGUMENT_TITLE_TEXT is missing" }

        val composeView = ComposeView(requireContext())

        return MaterialAlertDialogBuilder(requireActivity(), theme)
            .setView(composeView)
            .setCancelable(false)
            .create()
            .also { dialog ->
                composeView.setContent {
                    ThreemaTheme {
                        LoadingWithTimeoutDialog(
                            viewModelStoreOwner = context as ViewModelStoreOwner,
                            onDismissRequest = {
                                dialog.dismiss()
                            },
                            onTimeoutReachedChanged = { timeoutReached ->
                                dialog.setCancelable(timeoutReached)
                            },
                            timeout = timeoutSeconds.seconds,
                            titleText = titleTextRes,
                            messageText = R.string.please_wait,
                            messageTextTimeout = R.string.please_wait_timeout,
                            timeoutButtonText = R.string.close,
                        )
                    }
                }
            }
    }

    companion object {

        private const val ARGUMENT_TIMEOUT_SECONDS = "timeout_seconds"
        private const val ARGUMENT_TITLE_TEXT = "title_text"

        @JvmStatic
        fun newInstance(
            timeoutSeconds: Long,
            @StringRes titleText: Int,
        ): LoadingWithTimeoutDialogXml {
            check(timeoutSeconds >= 0L) {
                "Argument timeoutSeconds can not be negative"
            }
            return LoadingWithTimeoutDialogXml().apply {
                arguments = bundleOf(
                    ARGUMENT_TIMEOUT_SECONDS to timeoutSeconds,
                    ARGUMENT_TITLE_TEXT to titleText,
                )
            }
        }
    }
}

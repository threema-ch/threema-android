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

import android.os.Build
import android.text.TextUtils
import android.view.View
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import ch.threema.app.R
import ch.threema.app.emojis.EmojiConversationTextView
import ch.threema.app.ui.CustomTextSelectionCallback
import ch.threema.app.utils.LinkifyUtil

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InteropEmojiConversationTextView(
    text: String,
    @StyleRes textAppearanceRes: Int,
    contentColor: Color,
    shouldMarkupText: Boolean = true,
    isTextSelectable: Boolean = false,
    maxLines: Int = Integer.MAX_VALUE,
    textSelectionCallback: CustomTextSelectionCallback? = null,
) {
    var textViewRef: EmojiConversationTextView? by remember { mutableStateOf(null) }

    // Box is a workaround to make semantics work when merging descendants
    Box(modifier = Modifier.semantics { contentDescription = text }) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInteropFilter {
                    if (isTextSelectable) { // TODO(ANDR-3193)
                        return@pointerInteropFilter false
                    }
                    textViewRef?.onTouchEvent(it) ?: false
                },
            factory = { context ->
                val textView = EmojiConversationTextView(ContextThemeWrapper(context, R.style.AppBaseTheme))
                TextViewCompat.setTextAppearance(textView, textAppearanceRes)
                textView.setTextColor(contentColor.toArgb())

                textView.apply {
                    setMaxLines(maxLines)
                    ellipsize = TextUtils.TruncateAt.END
                    // TODO(ANDR-3193)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // do not add on lollipop or lower due to this bug: https://issuetracker.google.com/issues/36937508
                        textSelectionCallback?.let { callback ->
                            customSelectionActionModeCallback = callback
                            textSelectionCallback.setTextViewRef(textView)
                        }
                    }
                    setTextIsSelectable(isTextSelectable)
                    // disable accessibility since we set it for the Box parent
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }

                textViewRef = textView

                textView
            },
            update = { textView ->
                textView.apply {
                    setIgnoreMarkup(!shouldMarkupText)
                    setText(text)
                    setMaxLines(maxLines)
                    ellipsize = TextUtils.TruncateAt.END
                    if (shouldMarkupText) {
                        LinkifyUtil.getInstance().linkify(context,null, null, textView, null, true, false, null)
                    }
                }
            }
        )
    }
}

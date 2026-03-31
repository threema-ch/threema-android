package ch.threema.app.compose.common.interop

import android.text.TextUtils
import android.view.View
import androidx.annotation.StyleRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun InteropEmojiConversationTextView(
    text: String,
    @StyleRes textAppearanceRes: Int,
    contentColor: Color,
    linkifyListener: LinkifyUtil.LinkifyListener,
    shouldMarkupText: Boolean = true,
    isTextSelectable: Boolean = false,
    maxLines: Int = Integer.MAX_VALUE,
    textSelectionCallback: CustomTextSelectionCallback? = null,
) {
    var textViewRef: EmojiConversationTextView? by remember { mutableStateOf(null) }

    // Box is a workaround to make semantics work when merging descendants
    Box(
        modifier = Modifier.semantics {
            contentDescription = text
        },
    ) {
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
                EmojiConversationTextView(context).apply {
                    TextViewCompat.setTextAppearance(this, textAppearanceRes)
                    setTextColor(contentColor.toArgb())
                    setLinkTextColor(context.getColorStateList(R.color.bubble_text_link_colorstatelist))
                    setMaxLines(maxLines)
                    ellipsize = TextUtils.TruncateAt.END
                    // TODO(ANDR-3193)
                    textSelectionCallback?.let { callback ->
                        customSelectionActionModeCallback = callback
                        textSelectionCallback.setTextViewRef(this)
                    }
                    setTextIsSelectable(isTextSelectable)
                    // disable accessibility since we set it for the Box parent
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }.also {
                    textViewRef = it
                }
            },
            update = { textView ->
                textView.apply {
                    setIgnoreMarkup(!shouldMarkupText)
                    setText(text)
                    setMaxLines(maxLines)
                    ellipsize = TextUtils.TruncateAt.END
                    if (shouldMarkupText) {
                        LinkifyUtil.getInstance().linkify(
                            textView,
                            null,
                            true,
                            null,
                            linkifyListener,
                        )
                    }
                }
            },
        )
    }
}

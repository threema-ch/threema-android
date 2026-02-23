package ch.threema.app.compose.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import ch.threema.app.compose.common.extensions.spNoScale
import ch.threema.app.compose.conversation.models.UnreadState
import ch.threema.app.compose.theme.ThreemaThemePreview

/**
 *  We try to draw a perfect circle with this diameter.
 *  However, if the count is too big we increase the width.
 */
private const val DIAMETER_PREFERRED = 24

/**
 *  Marks the value from where a notation of `99+` is used to display the value.
 */
private const val MAX_VALUE = 99

@Composable
internal fun UnreadCounter(
    modifier: Modifier = Modifier,
    unreadState: UnreadState,
) {
    val countText: String = remember(unreadState) {
        when (unreadState) {
            is UnreadState.Messages -> {
                if (unreadState.count <= MAX_VALUE) {
                    unreadState.count.toString()
                } else {
                    "$MAX_VALUE+"
                }
            }

            is UnreadState.JustMarked -> ""
        }
    }
    val paddingHorizontal = remember(countText) {
        when {
            countText.length < 3 -> 0.dp
            else -> 4.dp
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(100))
            .background(MaterialTheme.colorScheme.error)
            .sizeIn(
                minWidth = DIAMETER_PREFERRED.dp,
                minHeight = DIAMETER_PREFERRED.dp,
                maxHeight = DIAMETER_PREFERRED.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = Modifier.padding(
                horizontal = paddingHorizontal,
            ),
            text = countText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 14.spNoScale,
                lineHeight = 14.spNoScale,
                platformStyle = PlatformTextStyle(
                    includeFontPadding = false,
                ),
            ),
            color = MaterialTheme.colorScheme.onError,
        )
    }
}

@Preview(group = "Normal Font Scale")
@Preview(group = "High Font Scale", fontScale = 2.0f)
@Composable
private fun UnreadCounterPreview(
    @PreviewParameter(ParamUnreadStatePreviewProvider::class) unreadState: UnreadState,
) {
    ThreemaThemePreview {
        UnreadCounter(
            unreadState = unreadState,
        )
    }
}

private class ParamUnreadStatePreviewProvider : PreviewParameterProvider<UnreadState> {
    override val values = sequenceOf(
        UnreadState.JustMarked,
        UnreadState.Messages(5L),
        UnreadState.Messages(12L),
        UnreadState.Messages(230L),
        UnreadState.Messages(1200L),
    )
}

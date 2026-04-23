package ch.threema.app.availabilitystatus

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.threema.app.R
import ch.threema.app.compose.common.extensions.get
import ch.threema.app.compose.common.text.conversation.ConversationText
import ch.threema.app.compose.common.text.conversation.EmojiSettings
import ch.threema.app.compose.common.text.conversation.MentionFeature
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.data.datatypes.AvailabilityStatus
import kotlinx.coroutines.flow.MutableStateFlow

class AvailabilityStatusContactBannerView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private val state = MutableStateFlow(
        AvailabilityStatusContactBannerState(
            availabilityStatusSet = null,
            emojiStyle = PreferenceService.EMOJI_STYLE_ANDROID,
        ),
    )

    fun setState(availabilityStatusSet: AvailabilityStatus.Set?, @EmojiStyle emojiStyle: Int) {
        state.value = AvailabilityStatusContactBannerState(
            availabilityStatusSet = availabilityStatusSet,
            emojiStyle = emojiStyle,
        )
    }

    @Composable
    override fun Content() {
        val state: AvailabilityStatusContactBannerState by state.collectAsStateWithLifecycle()
        state.availabilityStatusSet?.let { availabilityStatusSet ->
            ThreemaTheme {
                AvailabilityStatusContactBanner(
                    status = availabilityStatusSet,
                    emojiStyle = state.emojiStyle,
                )
            }
        }
    }
}

private data class AvailabilityStatusContactBannerState(
    val availabilityStatusSet: AvailabilityStatus.Set?,
    @EmojiStyle val emojiStyle: Int,
)

@Composable
private fun AvailabilityStatusContactBanner(
    modifier: Modifier = Modifier,
    status: AvailabilityStatus.Set,
    @EmojiStyle emojiStyle: Int,
) {
    ConversationText(
        modifier = modifier
            .padding(
                top = GridUnit.x1,
                bottom = dimensionResource(R.dimen.notice_views_vertical_margin),
                start = GridUnit.x1,
                end = GridUnit.x1,
            )
            .fillMaxWidth()
            .clip(
                shape = RoundedCornerShape(
                    size = dimensionResource(R.dimen.cardview_border_radius),
                ),
            )
            .background(
                color = status.containerColor(),
            )
            .padding(
                vertical = GridUnit.x1,
                horizontal = GridUnit.x2,
            ),
        rawInput = status.displayText().get(),
        textStyle = MaterialTheme.typography.bodyMedium,
        color = status.onContainerColor(),
        maxLines = 2,
        textAlign = TextAlign.Center,
        emojiSettings = EmojiSettings(
            style = emojiStyle,
        ),
        mentionFeature = MentionFeature.Off,
        markupEnabled = false,
    )
}

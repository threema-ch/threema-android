package ch.threema.app.availabilitystatus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.extensions.get
import ch.threema.app.compose.common.text.conversation.ConversationText
import ch.threema.app.compose.common.text.conversation.EmojiSettings
import ch.threema.app.compose.common.text.conversation.MentionFeature
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.data.datatypes.AvailabilityStatus

@Composable
fun AvailabilityStatusOwnBanner(
    modifier: Modifier = Modifier,
    status: AvailabilityStatus.Set,
    onClickEdit: () -> Unit,
    @EmojiStyle emojiStyle: Int,
) {
    Row(
        modifier = modifier
            .clip(
                shape = RoundedCornerShape(
                    size = dimensionResource(R.dimen.cardview_border_radius),
                ),
            )
            .background(
                color = status.containerColor(),
            )
            .padding(
                horizontal = GridUnit.x2,
                vertical = GridUnit.x0_5,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides status.onContainerColor()) {
            Icon(
                painter = painterResource(status.iconRes()),
                contentDescription = null,
                tint = status.iconColor(),
            )
            SpacerHorizontal(GridUnit.x1)
            ConversationText(
                modifier = Modifier.weight(1f),
                rawInput = status.displayText().get(),
                textStyle = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current,
                maxLines = 1,
                emojiSettings = EmojiSettings(
                    style = emojiStyle,
                ),
                mentionFeature = MentionFeature.Off,
                markupEnabled = false,
            )
            SpacerHorizontal(GridUnit.x2)
            Button(
                colors = ButtonDefaults.outlinedButtonColors()
                    .copy(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                contentPadding = PaddingValues(
                    start = GridUnit.x2,
                    top = ButtonDefaults.ContentPadding.calculateTopPadding(),
                    end = GridUnit.x2,
                    bottom = ButtonDefaults.ContentPadding.calculateBottomPadding(),
                ),
                onClick = onClickEdit,
                shape = RoundedCornerShape(12.dp),
            ) {
                ThemedText(
                    text = stringResource(R.string.edit),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun Preview_AvailabilityStatusOwnBanner(
    @PreviewParameter(PreviewProviderAvailabilityStatusOwnBanner::class)
    availabilityStatus: AvailabilityStatus.Set,
) {
    ThreemaThemePreview {
        Surface {
            Box(
                modifier = Modifier
                    .padding(all = GridUnit.x2),
            ) {
                AvailabilityStatusOwnBanner(
                    modifier = Modifier.fillMaxWidth(),
                    status = availabilityStatus,
                    onClickEdit = {},
                    emojiStyle = PreferenceService.EMOJI_STYLE_ANDROID,
                )
            }
        }
    }
}

private class PreviewProviderAvailabilityStatusOwnBanner : PreviewParameterProvider<AvailabilityStatus.Set> {

    override val values: Sequence<AvailabilityStatus.Set> = sequenceOf(
        AvailabilityStatus.Busy(),
        AvailabilityStatus.Busy(
            description = "In a short coffee break \u2615\uD83D\uDE42\u200D\u2195\uFE0F",
        ),
        AvailabilityStatus.Busy(
            description = "I cant keep my status description short because I am a person that likes to talk a lot.",
        ),
        AvailabilityStatus.Unavailable(),
        AvailabilityStatus.Unavailable(
            description = "Free day today",
        ),
        AvailabilityStatus.Unavailable(
            description = "I am on vacation and want to base jump mount everest. Hope to see you all when I make it back.",
        ),
    )
}

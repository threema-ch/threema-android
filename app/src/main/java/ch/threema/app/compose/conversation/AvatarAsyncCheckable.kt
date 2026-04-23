package ch.threema.app.compose.conversation

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import ch.threema.app.R
import ch.threema.app.compose.common.avatar.AvatarAsync
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.immutables.toImmutableBitmap
import ch.threema.app.compose.preview.PreviewData
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.usecases.conversations.AvatarIteration
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier

private const val FLIP_ANIMATION_DURATION_MILLIS = 400

/**
 *  Animates with a horizontal card-flip animation between the two [isChecked] states.
 */
@Composable
fun AvatarAsyncCheckable(
    modifier: Modifier = Modifier,
    avatarIteration: AvatarIteration = AvatarIteration.initial,
    receiverIdentifier: ReceiverIdentifier,
    bitmapProvider: suspend (ReceiverIdentifier) -> ImmutableBitmap?,
    contentDescription: String?,
    @DrawableRes fallbackIcon: Int,
    showWorkBadge: Boolean,
    availabilityStatus: AvailabilityStatus?,
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    val rotation = animateFloatAsState(
        targetValue = when (isChecked) {
            true -> 180f
            false -> 0f
        },
        animationSpec = tween(
            durationMillis = FLIP_ANIMATION_DURATION_MILLIS,
            easing = FastOutSlowInEasing,
        ),
    )

    Box(
        modifier = modifier
            .size(GridUnit.x5)
            .graphicsLayer {
                rotationY = rotation.value
            },
    ) {
        if (rotation.value <= 90f) {
            AvatarAsync(
                modifier = Modifier.fillMaxSize(),
                avatarIteration = avatarIteration,
                receiverIdentifier = receiverIdentifier,
                bitmapProvider = bitmapProvider,
                contentDescription = contentDescription,
                fallbackIcon = fallbackIcon,
                showWorkBadge = showWorkBadge,
                availabilityStatus = availabilityStatus,
                onClick = onClick,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = 180f
                    },
            ) {
                CheckedIndicator(
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CheckedIndicator(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(color = MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(GridUnit.x3),
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@PreviewLightDark
@Composable
private fun AvatarAsyncCheckable_Preview_Unchecked() {
    ThreemaThemePreview {
        Surface {
            val context = LocalContext.current
            val assetRes = R.drawable.ic_emoji_1f98a
            val avatarBitmap: ImmutableBitmap? = remember(assetRes) {
                ContextCompat.getDrawable(context, assetRes)?.toBitmap()?.toImmutableBitmap()
            }
            AvatarAsyncCheckable(
                modifier = Modifier,
                receiverIdentifier = ContactReceiverIdentifier(
                    identity = PreviewData.IDENTITY_OTHER_1.value,
                ),
                bitmapProvider = { avatarBitmap },
                contentDescription = null,
                fallbackIcon = R.drawable.ic_contact,
                showWorkBadge = true,
                availabilityStatus = AvailabilityStatus.Busy(),
                isChecked = false,
                onClick = {},
            )
        }
    }
}

@PreviewDynamicColors
@Composable
private fun AvatarAsyncCheckable_Preview_Checked() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        Surface {
            AvatarAsyncCheckable(
                modifier = Modifier,
                receiverIdentifier = ContactReceiverIdentifier(
                    identity = PreviewData.IDENTITY_OTHER_1.value,
                ),
                bitmapProvider = { null },
                contentDescription = null,
                fallbackIcon = R.drawable.ic_contact,
                showWorkBadge = true,
                availabilityStatus = AvailabilityStatus.Busy(),
                isChecked = true,
                onClick = {},
            )
        }
    }
}

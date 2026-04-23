package ch.threema.app.compose.common.avatar

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import ch.threema.app.R
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.immutables.toImmutableBitmap
import ch.threema.app.compose.preview.PreviewData
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.usecases.conversations.AvatarIteration
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier

/**
 *  Loads the avatar of the passed [receiverIdentifier] using the [bitmapProvider] asynchronously.
 *  In the meantime or if no avatar exists, the [fallbackIcon] will be displayed.
 *
 *  @param bitmapProvider Is responsible for loading the bitmap from disk/cache
 */
@Composable
fun AvatarAsync(
    modifier: Modifier = Modifier,
    avatarIteration: AvatarIteration = AvatarIteration.initial,
    receiverIdentifier: ReceiverIdentifier,
    bitmapProvider: suspend (ReceiverIdentifier) -> ImmutableBitmap?,
    contentDescription: String?,
    @DrawableRes fallbackIcon: Int,
    showWorkBadge: Boolean,
    availabilityStatus: AvailabilityStatus?,
    onClick: (() -> Unit)? = null,
) {
    // A new bitmap asset will be requested from bitmapProvider if one of these values change
    val avatarBitmapKeys = arrayOf(receiverIdentifier, avatarIteration.value)
    var avatarBitmap: ImmutableBitmap? by remember(*avatarBitmapKeys) {
        mutableStateOf(null)
    }
    LaunchedEffect(*avatarBitmapKeys) {
        avatarBitmap = bitmapProvider(receiverIdentifier)
    }

    Avatar(
        modifier = modifier,
        bitmap = avatarBitmap,
        contentDescription = contentDescription,
        fallbackIcon = fallbackIcon,
        showWorkBadge = showWorkBadge,
        availabilityStatus = availabilityStatus,
        onClick = onClick,
    )
}

@Preview
@Composable
private fun AvatarAsync_Preview() {
    ThreemaThemePreview {
        Surface {
            val context = LocalContext.current
            val assetRes = R.drawable.ic_emoji_1f438
            val avatarBitmap: ImmutableBitmap? = remember(assetRes) {
                ContextCompat.getDrawable(context, assetRes)?.toBitmap()?.toImmutableBitmap()
            }
            AvatarAsync(
                receiverIdentifier = ContactReceiverIdentifier(
                    identity = PreviewData.IDENTITY_OTHER_1.value,
                ),
                bitmapProvider = { avatarBitmap },
                contentDescription = null,
                fallbackIcon = R.drawable.ic_contact,
                showWorkBadge = true,
                availabilityStatus = AvailabilityStatus.None,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun AvatarAsync_Preview_NoAvatar() {
    ThreemaThemePreview {
        Surface {
            AvatarAsync(
                receiverIdentifier = ContactReceiverIdentifier(
                    identity = PreviewData.IDENTITY_OTHER_1.value,
                ),
                bitmapProvider = { null },
                contentDescription = null,
                fallbackIcon = R.drawable.ic_contact,
                showWorkBadge = true,
                availabilityStatus = AvailabilityStatus.Unavailable(),
                onClick = {},
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AvatarAsync_Preview_Dark() {
    ThreemaThemePreview {
        Surface {
            val context = LocalContext.current
            val assetRes = R.drawable.ic_emoji_1f98a
            val avatarBitmap: ImmutableBitmap? = remember(assetRes) {
                ContextCompat.getDrawable(context, assetRes)?.toBitmap()?.toImmutableBitmap()
            }
            AvatarAsync(
                receiverIdentifier = ContactReceiverIdentifier(
                    identity = PreviewData.IDENTITY_OTHER_1.value,
                ),
                bitmapProvider = { avatarBitmap },
                contentDescription = null,
                fallbackIcon = R.drawable.ic_contact,
                showWorkBadge = true,
                availabilityStatus = AvailabilityStatus.Busy(),
                onClick = {},
            )
        }
    }
}

package ch.threema.app.compose.common.avatar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import ch.threema.app.R
import ch.threema.app.compose.common.extensions.thenIf
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    bitmap: ImmutableBitmap?,
    contentDescription: String?,
    @DrawableRes fallbackIcon: Int = R.drawable.ic_contact,
    showWorkBadge: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.size(GridUnit.x5),
    ) {
        val avatarModifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .thenIf(
                apply = onClick != null,
            ) {
                Modifier.clickable(onClick = onClick ?: {})
            }
        if (bitmap != null) {
            Image(
                modifier = avatarModifier,
                bitmap = bitmap.imageBitmap,
                contentDescription = contentDescription,
            )
        } else {
            Icon(
                modifier = avatarModifier,
                painter = painterResource(fallbackIcon),
                contentDescription = contentDescription,
                tint = LocalContentColor.current.copy(
                    alpha = 0.6f,
                ),
            )
        }
        if (showWorkBadge) {
            Image(
                modifier = Modifier
                    .fillMaxSize(0.4f)
                    .align(Alignment.BottomStart),
                painter = painterResource(R.drawable.ic_badge_work),
                contentDescription = null,
            )
        }
    }
}

@Preview
@Composable
private fun Avatar_Preview() {
    ThreemaThemePreview {
        Avatar(
            bitmap = null,
            contentDescription = null,
            showWorkBadge = true,
            onClick = {},
        )
    }
}

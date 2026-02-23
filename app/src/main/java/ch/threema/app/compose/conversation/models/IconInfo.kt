package ch.threema.app.compose.conversation.models

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable

@Stable
data class IconInfo(
    @DrawableRes val icon: Int,
    @StringRes val contentDescription: Int?,
    @ColorInt val tintOverride: Int? = null,
)

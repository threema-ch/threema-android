package ch.threema.app.compose.conversation.models

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class IconInfo(
    @DrawableRes val res: Int,
    @StringRes val contentDescription: Int?,
    @ColorInt val tintOverride: Int? = null,
)

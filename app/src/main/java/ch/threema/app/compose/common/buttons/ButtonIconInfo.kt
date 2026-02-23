package ch.threema.app.compose.common.buttons

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable

@Stable
data class ButtonIconInfo(
    @DrawableRes val icon: Int,
    @StringRes val contentDescription: Int?,
)

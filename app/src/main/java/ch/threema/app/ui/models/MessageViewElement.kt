package ch.threema.app.ui.models

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

@Immutable
data class MessageViewElement(
    @JvmField @DrawableRes val icon: Int?,
    @JvmField val placeholder: String?,
    @JvmField val text: String?,
    @JvmField val contentDescription: String?,
    @JvmField @ColorRes val color: Int?,
)

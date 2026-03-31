package ch.threema.app.pinlock

import androidx.compose.runtime.Immutable
import ch.threema.android.ResolvableString

@Immutable
data class PinLockViewState(
    val pin: String = "",
    val pinEntryEnabled: Boolean = true,
    val error: ResolvableString? = null,
)

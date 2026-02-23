package ch.threema.app.archive

import androidx.compose.runtime.Immutable

@Immutable
data class ReallyDeleteConversationsDialogContent(
    val title: String,
    val message: String,
)

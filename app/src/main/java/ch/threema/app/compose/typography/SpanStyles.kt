package ch.threema.app.compose.typography

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration

object SpanStyles {
    private val linkBase: SpanStyle
        get() = SpanStyle(
            textDecoration = TextDecoration.Underline,
        )

    val linkPrimary: SpanStyle
        @Composable
        @ReadOnlyComposable
        get() = linkBase.copy(
            color = MaterialTheme.colorScheme.primary,
        )
}

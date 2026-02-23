package ch.threema.app.compose.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.theme.AppTypography
import ch.threema.app.compose.theme.ThreemaThemePreview

@Composable
fun HintText(text: String) {
    Row(
        modifier = Modifier.padding(
            horizontal = 2.dp,
            vertical = 4.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_info_rounded),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        ThemedText(
            modifier = Modifier.weight(1f),
            text = text,
            style = AppTypography.bodySmall,
        )
    }
}

@Preview
@Composable
private fun HintText_Preview() {
    ThreemaThemePreview {
        HintText(
            text = "This is an informative text, it may span multiple lines if it " +
                "is long enough.",
        )
    }
}

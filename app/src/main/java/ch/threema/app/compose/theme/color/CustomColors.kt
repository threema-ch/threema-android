package ch.threema.app.compose.theme.color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import ch.threema.app.R

object CustomColors {

    val listItemHighlightedContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.settings_multipane_selection_bg)
}

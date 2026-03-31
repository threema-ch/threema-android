package ch.threema.app.compose.common

import android.content.res.Resources
import androidx.annotation.AttrRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.getColorOrThrow
import androidx.core.content.res.use

/**
 *  Retrieve the resolved color from the given [referenceId]
 *
 *  @param referenceId A theme reference pointing to a color value
 *
 *  @throws Resources.NotFoundException if [referenceId] could not be found or resolved as a color value in the current theme
 *
 *  @see androidx.compose.ui.res.colorResource
 */
@Composable
@ReadOnlyComposable
fun colorReferenceResource(@AttrRes referenceId: Int): Color =
    LocalContext.current.theme.obtainStyledAttributes(intArrayOf(referenceId))
        .use { typedArray ->
            runCatching {
                Color(typedArray.getColorOrThrow(0))
            }.getOrElse {
                throw Resources.NotFoundException("Failed to find color attr $referenceId in current theme")
            }
        }

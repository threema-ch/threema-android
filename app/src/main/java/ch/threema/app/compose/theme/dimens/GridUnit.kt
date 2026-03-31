package ch.threema.app.compose.theme.dimens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val GRID_BASE = 8

@Suppress("unused")
object GridUnit {
    val x0 = 0.dp
    val x0_25 = GRID_BASE.dp / 4 // 2
    val x0_5 = GRID_BASE.dp / 2 // 4
    val x1 = GRID_BASE.dp // 8
    val x1_5 = GRID_BASE.dp * 1.5f // 12
    val x2 = GRID_BASE.dp * 2 // 16
    val x2_5 = GRID_BASE.dp * 2.5f // 20
    val x3 = GRID_BASE.dp * 3 // 24
    val x4 = GRID_BASE.dp * 4 // 32
    val x5 = GRID_BASE.dp * 5 // 40
    val x6 = GRID_BASE.dp * 6 // 48
    val x7 = GRID_BASE.dp * 7 // 56
    val x8 = GRID_BASE.dp * 8 // 64
    val x9 = GRID_BASE.dp * 9 // 72
    val x10 = GRID_BASE.dp * 10 // 80
    val x15 = GRID_BASE.dp * 15 // 120
}

/**
 *  If the current window width reaches a defined fixed value of 600 [Dp], the receiver [Dp] value will be doubled.
 */
val Dp.responsive: Dp
    @Composable
    @ReadOnlyComposable
    get() {
        val screenWidthDp: Int = LocalConfiguration.current.screenWidthDp
        val factor: Float = when {
            screenWidthDp >= 600 -> 2.0f
            else -> 1.0f
        }
        return this.times(factor)
    }

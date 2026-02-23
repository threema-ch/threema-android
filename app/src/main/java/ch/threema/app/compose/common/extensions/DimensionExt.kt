package ch.threema.app.compose.common.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun Dp.dpToPx() = with(LocalDensity.current) { this@dpToPx.toPx() }

@Composable
fun Float.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }

fun Dp.dpToPx(density: Density): Float {
    return density.run { this@dpToPx.toPx() }
}

fun Float.pxToDp(density: Density): Dp {
    return density.run { this@pxToDp.toDp() }
}

val Int.spNoScale: TextUnit
    @Composable @ReadOnlyComposable
    get() = with(LocalDensity.current) { this@spNoScale.dp.toSp() }

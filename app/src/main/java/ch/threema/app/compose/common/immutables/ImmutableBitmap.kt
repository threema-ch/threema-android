package ch.threema.app.compose.common.immutables

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 *  A wrapper for [Bitmap] to make it a stable parameter for Compose
 *
 *  ###### Contract
 *  The developer guarantees that the underlying [bitmap] *must not be mutated* after being wrapped. For any changes, a new [ImmutableBitmap]
 *  instance must be created, upholding the [Immutable] contract.
 */
@Immutable
data class ImmutableBitmap(
    val bitmap: Bitmap,
) {
    val imageBitmap: ImageBitmap
        get() = bitmap.asImageBitmap()
}

fun Bitmap.toImmutableBitmap(): ImmutableBitmap = ImmutableBitmap(this)

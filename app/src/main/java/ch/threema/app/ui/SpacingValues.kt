package ch.threema.app.ui

import android.content.Context
import androidx.annotation.DimenRes

data class SpacingValues(
    @DimenRes private val top: Int? = null,
    @DimenRes private val right: Int? = null,
    @DimenRes private val bottom: Int? = null,
    @DimenRes private val left: Int? = null,
) {

    fun topAsPixels(context: Context): Int =
        top?.let { context.resources.getDimensionPixelSize(top) } ?: 0

    fun rightAsPixels(context: Context): Int =
        right?.let { context.resources.getDimensionPixelSize(right) } ?: 0

    fun bottomAsPixels(context: Context): Int =
        bottom?.let { context.resources.getDimensionPixelSize(bottom) } ?: 0

    fun leftAsPixels(context: Context): Int =
        left?.let { context.resources.getDimensionPixelSize(left) } ?: 0

    companion object {

        @JvmStatic
        fun all(@DimenRes value: Int) = SpacingValues(
            top = value,
            right = value,
            bottom = value,
            left = value,
        )

        @JvmStatic
        fun symmetric(@DimenRes vertical: Int, @DimenRes horizontal: Int) = SpacingValues(
            top = vertical,
            right = horizontal,
            bottom = vertical,
            left = horizontal,
        )

        @JvmStatic
        fun vertical(@DimenRes vertical: Int) = SpacingValues(
            top = vertical,
            bottom = vertical,
        )

        @JvmStatic
        fun horizontal(@DimenRes horizontal: Int) = SpacingValues(
            right = horizontal,
            left = horizontal,
        )

        @JvmStatic
        fun top(@DimenRes top: Int) = SpacingValues(top = top)

        @JvmStatic
        fun bottom(@DimenRes bottom: Int) = SpacingValues(bottom = bottom)

        @JvmStatic
        val zero
            get() = SpacingValues()
    }
}

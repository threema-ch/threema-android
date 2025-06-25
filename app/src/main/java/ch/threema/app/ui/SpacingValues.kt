/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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

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

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("ViewExtensions")

/**
 * Gets the coordinates of this view in the coordinate space of the window that contains the view.
 */
fun View.getLocation(xOffset: Int = 0, yOffset: Int = 0): IntArray {
    val location = IntArray(2)
    getLocationInWindow(location)
    location[0] += xOffset
    location[1] += yOffset
    return location
}

fun View.getTopCenterLocation(): IntArray =
    getLocation(xOffset = width / 2)

fun View.getBottomCenterLocation(): IntArray =
    getLocation(xOffset = width / 2, yOffset = height)

data class InsetSides(
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
    val left: Boolean = false,
) {

    companion object {

        @JvmStatic
        fun all() = InsetSides(top = true, right = true, bottom = true, left = true)

        @JvmStatic
        fun vertical() = InsetSides(top = true, bottom = true)

        @JvmStatic
        fun horizontal() = InsetSides(right = true, left = true)

        @JvmStatic
        fun top() = InsetSides(top = true)

        @JvmStatic
        fun bottom() = InsetSides(bottom = true)

        @JvmStatic
        fun ltr() = InsetSides(left = true, top = true, right = true)

        @JvmStatic
        fun lbr() = InsetSides(left = true, bottom = true, right = true)
    }
}

/**
 *  Apply the [systemBars] *or* [displayCutout] margin to the desired [insetSides] depending on which value is bigger.
 *  This way it is assured that no system bar (status-bar, navigation-bar and caption-bar) and no display cutout will overlap [this] view.
 *
 *  Note that *every* margin that was set to this view beforehand (for example via XML) will be overwritten.
 *
 *  @param insetSides Specifies the desired sides where margin will be applied. The default value of [InsetSides] does not apply it to any side.
 *  See [InsetSides.all] or [InsetSides.vertical].
 *
 *  @param ownMargin Adds additional margin to this view. If this parameter specified a margin for a window side that is not enabled via
 *  [insetSides], this margin value will be applied nevertheless.
 *
 *  @see View.applyDeviceInsetsAsPadding
 */
@JvmOverloads
fun View?.applyDeviceInsetsAsMargin(
    insetSides: InsetSides = InsetSides.all(),
    ownMargin: SpacingValues = SpacingValues.zero,
) {
    this ?: return
    ViewCompat.setOnApplyWindowInsetsListener(this) { view: View, windowInsets: WindowInsetsCompat ->
        val insets = windowInsets.getInsets(
            systemBars() or displayCutout(),
        )
        view.layoutParams = (view.layoutParams as MarginLayoutParams).apply {
            topMargin = when {
                insetSides.top -> ownMargin.topAsPixels(context) + insets.top
                else -> ownMargin.topAsPixels(context)
            }
            rightMargin = when {
                insetSides.right -> ownMargin.rightAsPixels(context) + insets.right
                else -> ownMargin.rightAsPixels(context)
            }
            bottomMargin = when {
                insetSides.bottom -> ownMargin.bottomAsPixels(context) + insets.bottom
                else -> ownMargin.bottomAsPixels(context)
            }
            leftMargin = when {
                insetSides.left -> ownMargin.leftAsPixels(context) + insets.left
                else -> ownMargin.leftAsPixels(context)
            }
        }
        windowInsets
    }
}

/**
 *  Apply the [systemBars] *or* [displayCutout] padding to the desired [insetSides] depending on which value is bigger.
 *  This way it is assured that no system-bar (status-bar, navigation-bar and caption-bar) and no display cutout will overlap [this] view.
 *
 *  Note that *every* padding that was set to this view beforehand (for example via XML) will be overwritten.
 *
 *  @param insetSides Specifies the desired sides where padding will be applied. The default value of [InsetSides] does not apply it to any side.
 *  See [InsetSides.all] or [InsetSides.vertical].
 *
 *  @param ownPadding Adds additional padding to this view. If this parameter specified a padding for a window side that is not enabled via
 *  [insetSides], this padding value will be applied nevertheless.
 *
 *  @see View.applyDeviceInsetsAsMargin
 */
@JvmOverloads
fun View?.applyDeviceInsetsAsPadding(
    insetSides: InsetSides,
    ownPadding: SpacingValues = SpacingValues.zero,
) {
    this ?: return
    ViewCompat.setOnApplyWindowInsetsListener(this) { view: View, windowInsets: WindowInsetsCompat ->
        val insets = windowInsets.getInsets(
            systemBars() or displayCutout(),
        )
        view.setPadding(
            when {
                insetSides.left -> ownPadding.leftAsPixels(context) + insets.left
                else -> ownPadding.leftAsPixels(context)
            },
            when {
                insetSides.top -> ownPadding.topAsPixels(context) + insets.top
                else -> ownPadding.topAsPixels(context)
            },
            when {
                insetSides.right -> ownPadding.rightAsPixels(context) + insets.right
                else -> ownPadding.rightAsPixels(context)
            },
            when {
                insetSides.bottom -> ownPadding.bottomAsPixels(context) + insets.bottom
                else -> ownPadding.bottomAsPixels(context)
            },
        )
        windowInsets
    }
}

fun View?.setMargin(left: Int, top: Int, right: Int, bottom: Int) {
    this ?: return
    try {
        layoutParams = (layoutParams as MarginLayoutParams).apply {
            leftMargin = left
            topMargin = top
            rightMargin = right
            bottomMargin = bottom
        }
    } catch (castException: ClassCastException) {
        logger.error("Could not set margins", castException)
    }
}

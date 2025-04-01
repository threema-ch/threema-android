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

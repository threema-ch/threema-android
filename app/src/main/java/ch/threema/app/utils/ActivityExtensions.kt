/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.utils

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.InsetsType

fun Activity.findRootView(): View? {
    var rootView: View? = window.decorView.rootView
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        try {
            val decorView = window.decorView as ViewGroup
            if (decorView.childCount == 1) {
                rootView = decorView.getChildAt(0)
            }
        } catch (e: Exception) {
            rootView = null
        }
    }
    return rootView
}

/**
 *  @return The insets of this activities root view at the current time. If the root view or its insets could not be determined, [Insets.NONE] is returned.
 */
fun Activity.getCurrentInsets(@InsetsType types: Int): Insets {
    val rootView: View = findRootView() ?: return Insets.NONE
    val windowInsetsCompat: WindowInsetsCompat = ViewCompat.getRootWindowInsets(rootView) ?: return Insets.NONE
    return windowInsetsCompat.getInsets(types)
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

import android.os.Build
import android.view.View
import android.view.WindowInsets
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

/**
 *  Calls the passed callback `onInsetsAvailable` with the `WindowInsets` **once** when they are provided by the view renderer.
 *
 *  After the first callback, the listener gets removed from the view.
 *
 *  @param rootViewId Should be the top most view element of the activities layout.
 *  @throws NoSuchElementException If the view could not be found in the current activities view-tree.
 */
@Throws(NoSuchElementException::class)
inline fun AppCompatActivity.withCurrentWindowInsets(
    @IdRes rootViewId: Int,
    crossinline onInsetsAvailable: (View, WindowInsets) -> Unit,
) {
    val rootView: View = findViewById(rootViewId)
        ?: throw NoSuchElementException("View with id $rootViewId not found.")
    rootView.setOnApplyWindowInsetsListener { view, insets ->
        rootView.setOnApplyWindowInsetsListener(null)
        onInsetsAvailable(view, insets)
        insets
    }
}

/**
 *  Calls the passed callback `onInsetsAvailable` with the `WindowInsets` **once** when they are provided by the view renderer.
 *
 *  After the first callback, the listener gets removed from the view.
 *
 *  @throws IllegalStateException If the fragments view was not created or is already destroyed.
 */
@Throws(IllegalStateException::class)
inline fun Fragment.withCurrentWindowInsets(
    crossinline onInsetsAvailable: (View, WindowInsets) -> Unit,
) {
    val fragmentsView: View = view ?: throw IllegalStateException("Fragments view not present")
    fragmentsView.setOnApplyWindowInsetsListener { rootView, insets ->
        rootView.setOnApplyWindowInsetsListener(null)
        onInsetsAvailable(rootView, insets)
        insets
    }
}

/**
 * @return The height of the system status bar in pixels.
 */
fun WindowInsets.getStatusBarHeightPxCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getInsets(WindowInsets.Type.systemBars()).top
    } else {
        @Suppress("DEPRECATION")
        systemWindowInsetTop
    }

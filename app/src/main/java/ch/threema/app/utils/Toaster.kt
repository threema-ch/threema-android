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

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import ch.threema.app.ThreemaApplication
import java.lang.ref.WeakReference

class Toaster(
    context: Context,
) {
    private val contextReference = WeakReference(context)

    @JvmOverloads
    fun showToast(@StringRes message: Int, duration: Duration = Duration.SHORT) {
        val context = contextReference.get() ?: return
        showToast(context.getString(message), duration)
    }

    @JvmOverloads
    fun showToast(message: String, duration: Duration = Duration.SHORT) {
        val context = contextReference.get() ?: return
        RuntimeUtil.runOnUiThread {
            Toast.makeText(context, message, duration.toastLength).show()
        }
    }

    enum class Duration(val toastLength: Int) {
        SHORT(Toast.LENGTH_SHORT),
        LONG(Toast.LENGTH_LONG),
    }

    companion object {
        private val globalToaster = Toaster(ThreemaApplication.getAppContext())

        @JvmOverloads
        fun showToast(@StringRes message: Int, duration: Duration = Duration.SHORT) {
            globalToaster.showToast(message, duration)
        }

        @JvmOverloads
        fun showToast(message: String, duration: Duration = Duration.SHORT) {
            globalToaster.showToast(message, duration)
        }
    }
}

@JvmOverloads
fun Context.showToast(@StringRes message: Int, duration: Toaster.Duration = Toaster.Duration.SHORT) {
    Toaster(this).showToast(message, duration)
}

@JvmOverloads
fun Context.showToast(message: String, duration: Toaster.Duration = Toaster.Duration.SHORT) {
    Toaster(this).showToast(message, duration)
}

@JvmOverloads
fun Fragment.showToast(@StringRes message: Int, duration: Toaster.Duration = Toaster.Duration.SHORT) {
    context?.showToast(message, duration)
}

@JvmOverloads
fun Fragment.showToast(message: String, duration: Toaster.Duration = Toaster.Duration.SHORT) {
    context?.showToast(message, duration)
}

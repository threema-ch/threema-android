/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import ch.threema.app.R

/**
 * A version of Toast that allows for more than two lines of text which can be too restrictive depending on the language.
 * NOTE: If the app is backgrounded, a regular toast (limited to the usual two lines) will be shown instead.
 * Don't use this from Services - it's useless
 */
object LongToast {
    @SuppressLint("InflateParams")
    @JvmStatic
    fun makeText(
        context: Context?,
        text: CharSequence?,
        duration: Int
    ): Toast {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isAppInForeground()) {
            val longToastView: View =
                LayoutInflater.from(context).inflate(R.layout.toast_long, null)
            val longToastTextView = longToastView.findViewById<TextView>(R.id.toast_text)
            longToastTextView.text = text
            val longToast = Toast(context)
            longToast.view = longToastView
            longToast.duration = duration
            longToast
        } else {
            Toast.makeText(context, text, duration)
        }
    }

    @JvmStatic
    fun makeText(
        context: Context,
        @StringRes textRes: Int,
        duration: Int
    ): Toast {
        return makeText(context, context.getString(textRes), duration)
    }

    private fun isAppInForeground(
    ): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}


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

package ch.threema.app.crashreporting

import androidx.appcompat.app.AppCompatActivity
import ch.threema.app.R
import ch.threema.app.dialogs.GenericAlertDialog

object CrashReportingDialog {
    @JvmStatic
    fun showDialog(activity: AppCompatActivity, tag: String) {
        // TODO(ANDR-4339): The message and buttons in this dialog need to be adjusted and translated
        GenericAlertDialog.newInstance(
            /* titleString = */
            "Crash detected",
            /* messageString = */
            "It looks like the app has crashed. Automatic crash reporting is not yet supported. " +
                "Please inform the Android team. Thanks.",
            /* positive = */
            R.string.ok,
            /* negative = */
            0,
        )
            .show(activity.supportFragmentManager, tag)
    }
}

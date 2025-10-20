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

package ch.threema.app.startup

import android.app.Activity
import org.koin.android.ext.android.get

/**
 * Checks whether the app still needs to get ready (e.g. by wrapping up database migrations) before the
 * calling activity can be displayed.
 *
 * Must be called from an activity's onCreate method, as early as possibly but after the super class's onCreate was called.
 * If true is returned, the calling activity must immediately stop its own initialization and must not access any services.
 * In this case the current activity will be finished and instead the [AppStartupActivity] will be shown.
 * Once the app is ready, the calling activity will be recreated.
 */
fun Activity.finishAndRestartLaterIfNotReady(): Boolean {
    if (get<AppStartupMonitor>().isReady()) {
        return false
    }

    startActivity(AppStartupActivity.createIntent(this, intent))
    finish()
    return true
}

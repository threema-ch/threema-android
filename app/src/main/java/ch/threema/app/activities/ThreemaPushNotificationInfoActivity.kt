/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.activities

import android.os.Bundle
import android.view.View
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("ThreemaPushNotificationInfoActivity")

/**
 * Activity that is shown when the user taps on the persistent Threema Push notification.
 */
class ThreemaPushNotificationInfoActivity : ThreemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        logger.debug("onCreate")

        ConfigUtils.configureActivityTheme(this)

        super.onCreate(savedInstanceState)

        // Load layout
        setContentView(R.layout.activity_threema_push_notification_info)

        // Set up click handlers
        findViewById<View>(R.id.close_button).setOnClickListener { finish() }
    }
}

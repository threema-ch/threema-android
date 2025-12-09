/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.SpacingValues
import ch.threema.app.ui.applyDeviceInsetsAsMargin
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import com.google.android.material.button.MaterialButton

private val logger = getThreemaLogger("ThreemaPushNotificationInfoActivity")

/**
 * Activity that is shown when the user taps on the persistent Threema Push notification.
 */
class ThreemaPushNotificationInfoActivity : ThreemaActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.debug("onCreate")

        super.onCreate(savedInstanceState)

        // Load layout
        setContentView(R.layout.activity_threema_push_notification_info)

        // Set up click handlers
        findViewById<View>(R.id.close_button).setOnClickListener { finish() }

        findViewById<ScrollView>(R.id.scroll_container).applyDeviceInsetsAsPadding(
            insetSides = InsetSides.ltr(),
            ownPadding = SpacingValues.all(R.dimen.grid_unit_x2),
        )
        findViewById<MaterialButton>(R.id.close_button).applyDeviceInsetsAsMargin(
            insetSides = InsetSides.lbr(),
            ownMargin = SpacingValues(
                right = R.dimen.grid_unit_x2,
                bottom = R.dimen.grid_unit_x2,
            ),
        )
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<ThreemaPushNotificationInfoActivity>(context)
    }
}

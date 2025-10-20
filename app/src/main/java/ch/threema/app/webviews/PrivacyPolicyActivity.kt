/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.webviews

import android.content.Context
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.buildActivityIntent
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("PrivacyPolicyActivity")

class PrivacyPolicyActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun getWebViewTitle() = R.string.privacy_policy

    override fun getWebViewUrl() = ConfigUtils.getPrivacyPolicyURL(this)

    companion object {
        @JvmStatic
        fun createIntent(context: Context, forceDarkTheme: Boolean = false) = buildActivityIntent<PrivacyPolicyActivity>(context) {
            if (forceDarkTheme) {
                putExtra(FORCE_DARK_THEME, true)
            }
        }
    }
}

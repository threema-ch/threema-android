/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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
import android.os.Bundle
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.buildActivityIntent
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("WorkExplainActivity")

class WorkExplainActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (ConfigUtils.isAppInstalled(WORK_PACKAGE_NAME)) {
            val launchIntent = packageManager.getLaunchIntentForPackage(WORK_PACKAGE_NAME)
            if (launchIntent != null) {
                startActivity(launchIntent)
                overridePendingTransition(0, 0)
            }
            finish()
        }
        super.onCreate(savedInstanceState)
    }

    override fun getWebViewTitle() = R.string.threema_work

    override fun getWebViewUrl() = ConfigUtils.getWorkExplainURL(this)

    companion object {
        private const val WORK_PACKAGE_NAME = "ch.threema.app.work"

        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<WorkExplainActivity>(context)
    }
}

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

import androidx.core.net.toUri
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.LocaleUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SupportActivity")

class SupportActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val userService: UserService by inject()

    override fun requiresConnection() = true

    override fun requiresJavaScript() = true

    override fun getWebViewTitle() = R.string.support

    override fun getWebViewUrl(): String =
        getBaseUrl().toUri()
            .buildUpon()
            .appendQueryParameter("lang", LocaleUtil.getAppLanguage())
            .appendQueryParameter("version", ConfigUtils.getDeviceInfo(true))
            .appendQueryParameter("identity", userService.identity)
            .build()
            .toString()

    private fun getBaseUrl(): String {
        if (ConfigUtils.isWorkBuild()) {
            val customSupportUrl = preferenceService.customSupportUrl
            if (!customSupportUrl.isNullOrEmpty()) {
                return customSupportUrl
            }
        }
        return getString(R.string.support_url)
    }
}

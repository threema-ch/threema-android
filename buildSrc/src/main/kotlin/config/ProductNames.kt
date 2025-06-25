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

package config

import com.android.build.api.dsl.VariantDimension
import utils.stringResValue

/**
 * Configure the names used for the product flavor.
 *
 * @param appName The full name of the app, e.g. "Threema Libre". Used in places where we refer to the app itself,
 * as opposed to a feature, the Threema service or the company.
 * @param shortAppName The short version of the app name, e.g. "Threema". This will also be used in terms like "Threema ID", "Threema Safe", etc.
 * @param companyName The name of the company that operates the app's servers and/or distributes the app.
 * @param appNameDesktop The name of the corresponding desktop client (multi-device)
 */
fun VariantDimension.setProductNames(
    appName: String,
    shortAppName: String = if ("Threema" in appName) "Threema" else appName,
    companyName: String = "Threema",
    appNameDesktop: String = appName,
) {
    stringResValue("app_name", appName.nonBreaking())
    stringResValue("app_name_short", shortAppName.nonBreaking())
    stringResValue("company_name", companyName.nonBreaking())
    stringResValue("app_name_desktop", appNameDesktop.nonBreaking())
}

private fun String.nonBreaking() =
    replace(" ", "\u00A0")

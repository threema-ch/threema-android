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

package ch.threema.app.files

import ch.threema.common.files.FileHandle

class AppLogoFileHandleProvider(
    private val appDirectoryProvider: AppDirectoryProvider,
) {
    fun get(theme: Theme): FileHandle {
        val key = when (theme) {
            Theme.LIGHT -> "light"
            Theme.DARK -> "dark"
        }
        return appDirectoryProvider.appDataDirectory.fileHandle("app_logo_$key.png")
            .withFallback(appDirectoryProvider.legacyUserFilesDirectory.fileHandle("appicon_$key.png"))
    }

    enum class Theme {
        LIGHT,
        DARK,
    }
}

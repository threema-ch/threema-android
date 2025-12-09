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

package ch.threema.app.systemupdates.updates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ch.threema.app.files.WallpaperFileHandleProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.MasterKeyProvider
import java.io.File
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion114")

class SystemUpdateToVersion114 : SystemUpdate, KoinComponent {

    private val appContext: Context by inject()
    private val masterKeyProvider: MasterKeyProvider by inject()
    private val wallpaperFileHandleProvider: WallpaperFileHandleProvider by inject()

    override fun run() {
        try {
            val legacyDataDirectory = File(appContext.getExternalFilesDir(null), "data")
            val legacyGlobalWallpaperFile = File(legacyDataDirectory, "wallpaper.jpg")
            if (!legacyGlobalWallpaperFile.exists()) {
                return
            }

            val masterKey = masterKeyProvider.getMasterKey()

            val options = BitmapFactory.Options()
            options.inSampleSize = 1
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val bitmap = legacyGlobalWallpaperFile.inputStream().use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            val isAlreadyEncrypted = bitmap == null
            bitmap?.recycle()

            legacyGlobalWallpaperFile.inputStream()
                .let { inputStream ->
                    if (isAlreadyEncrypted) {
                        masterKey.decrypt(inputStream)
                    } else {
                        inputStream
                    }
                }
                .use { inputStream ->
                    wallpaperFileHandleProvider.getGlobal().write().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

            legacyGlobalWallpaperFile.delete()
        } catch (e: Exception) {
            logger.warn("Failed to migrate global wallpaper", e)
        }
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "move (and encrypt) global wallpaper file"

    companion object {
        const val VERSION = 114
    }
}

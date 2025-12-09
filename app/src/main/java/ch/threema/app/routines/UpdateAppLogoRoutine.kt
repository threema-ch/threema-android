/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

package ch.threema.app.routines

import android.provider.MediaStore
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConfigUtils.AppThemeSetting
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.Http
import ch.threema.common.TimeProvider
import ch.threema.common.buildRequest
import ch.threema.common.copyIntoFile
import ch.threema.common.execute
import ch.threema.common.getExpiration
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.common.plus
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.days
import okhttp3.OkHttpClient

private val logger = getThreemaLogger("UpdateAppLogoRoutine")

class UpdateAppLogoRoutine(
    private val fileService: FileService,
    private val preferenceService: PreferenceService,
    private val okHttpClient: OkHttpClient,
    private val lightUrl: String?,
    private val darkUrl: String?,
    private val forceUpdate: Boolean,
    private val timeProvider: TimeProvider = TimeProvider.default,
) : Runnable {
    override fun run() {
        logger.debug("start update app logo {}, {}", lightUrl, darkUrl)
        updateLogo(lightUrl, ConfigUtils.THEME_LIGHT)
        updateLogo(darkUrl, ConfigUtils.THEME_DARK)
    }

    private fun updateLogo(logoUrl: String?, @AppThemeSetting theme: String) {
        logger.info("Update app logo (forcedUpdate={}, theme={})", forceUpdate, theme)
        if (logoUrl.isNullOrEmpty()) {
            clearLogo(theme)
            return
        }

        val now = timeProvider.get()

        if (!forceUpdate) {
            val expiresAt = preferenceService.getAppLogoExpiresAt(theme)
            if (expiresAt != null && now < expiresAt) {
                logger.info("Logo not expired")
                return
            }
        }

        try {
            logger.info("Downloading {}", logoUrl)
            val request = buildRequest {
                get()
                url(logoUrl)
            }
            okHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> {
                        logger.debug("Logo found. Start download")
                        val temporaryFile = fileService.createTempFile(MediaStore.MEDIA_IGNORE_FILENAME, "appicon")
                        try {
                            val expires = response.getExpiration() ?: (now + 1.days)
                            response.getSuccessBodyOrThrow()
                                .copyIntoFile(temporaryFile)
                            logger.info("Logo downloaded. Expires at {}.", expires)

                            setLogo(logoUrl, temporaryFile, expires, theme)
                        } finally {
                            temporaryFile.delete()
                        }
                    }

                    response.code == Http.StatusCode.NOT_FOUND -> logger.warn("Logo not found")
                    else -> logger.warn("Connection failed with response code {}", response.code)
                }
            }
        } catch (e: IOException) {
            logger.error("Update of app logo failed", e)
        }
    }

    private fun setLogo(url: String, file: File, expires: Instant, @AppThemeSetting theme: String) {
        fileService.saveAppLogo(file, theme)
        preferenceService.setAppLogo(url, theme)
        preferenceService.setAppLogoExpiresAt(expires, theme)
    }

    private fun clearLogo(@AppThemeSetting theme: String) {
        logger.info("Clearing app logo for (forcedUpdate={}, theme={})", forceUpdate, theme)
        fileService.saveAppLogo(null, theme)
        preferenceService.clearAppLogo(theme)
    }
}

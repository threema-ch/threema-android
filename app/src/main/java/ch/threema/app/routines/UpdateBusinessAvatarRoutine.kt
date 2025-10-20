/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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
import androidx.annotation.WorkerThread
import ch.threema.app.services.ApiService
import ch.threema.app.services.ContactService
import ch.threema.app.services.FileService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.Http
import ch.threema.common.HttpResponseException
import ch.threema.common.TimeProvider
import ch.threema.common.buildRequest
import ch.threema.common.copyIntoFile
import ch.threema.common.execute
import ch.threema.common.getExpiration
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.common.plus
import ch.threema.data.models.ContactModel
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.Identity
import java.io.IOException
import kotlin.time.Duration.Companion.days
import okhttp3.OkHttpClient

private val logger = LoggingUtil.getThreemaLogger("UpdateBusinessAvatarRoutine")

class UpdateBusinessAvatarRoutine(
    private val okHttpClient: OkHttpClient,
    private val contactService: ContactService,
    private val fileService: FileService,
    private val apiService: ApiService,
    private val timeProvider: TimeProvider = TimeProvider.default,
) {

    private var isRunning: Boolean = false

    fun run(contactModel: ContactModel, forceUpdate: Boolean) {
        isRunning = true
        try {
            if (!ContactUtil.isGatewayContact(contactModel.identity)) {
                logger.error("Contact is not a business account")
                return
            }
            val now = timeProvider.get()

            if (!forceUpdate) {
                val data = contactModel.data
                if (data == null) {
                    logger.warn("Contact has been deleted")
                    return
                }
                if (!data.isAvatarExpired(now)) {
                    logger.error("Avatar is not expired")
                    return
                }
            }

            logger.debug("Downloading business avatar")

            val request = createRequest(contactModel.identity)
            try {
                okHttpClient.execute(request).use { response ->
                    val expires = response.getExpiration() ?: (now + 1.days)
                    val temporaryFile = createTempFile(contactModel.identity)
                    try {
                        response.getSuccessBodyOrThrow()
                            .copyIntoFile(temporaryFile)

                        // Note that the profile picture of a gateway id is stored as user defined
                        // profile picture on purpose. This prevents that it is overwritten if the
                        // gateway would suddenly start distributing its own profile picture via csp
                        // messages.
                        contactService.setUserDefinedProfilePicture(contactModel.identity, temporaryFile, TriggerSource.LOCAL)

                        contactModel.setLocalAvatarExpires(expires)
                    } finally {
                        temporaryFile.delete()
                    }
                }
            } catch (e: HttpResponseException) {
                when (e.code) {
                    Http.StatusCode.NOT_FOUND -> {
                        logger.debug("Avatar not found")
                        // Remove existing avatar
                        // Note that the profile picture of a gateway id is stored as user defined
                        // profile picture on purpose. This prevents that it is overwritten if the
                        // gateway would suddenly start distributing its own profile picture via csp
                        // messages.
                        fileService.removeUserDefinedProfilePicture(contactModel.identity)

                        contactModel.setLocalAvatarExpires(now + 1.days)
                    }

                    Http.StatusCode.UNAUTHORIZED -> {
                        logger.warn("Unauthorized access to avatar server")

                        // TODO(ANDR-4201): Remove this explicit check and handle it centrally instead
                        if (ConfigUtils.isOnPremBuild()) {
                            logger.info("Invalidating auth token")
                            apiService.invalidateAuthToken()
                        }
                    }

                    else -> {
                        logger.warn("Failed to update business avatar", e)
                    }
                }
            } catch (e: IOException) {
                logger.warn("Failed to download business avatar, will try again later", e)
            }
        } catch (e: Exception) {
            logger.error("An unexpected error occurred when updating a business avatar", e)
        } finally {
            isRunning = false
        }
    }

    private fun createRequest(identity: Identity) =
        buildRequest {
            get()
            url(apiService.getAvatarURL(identity))
            apiService.getAuthToken()?.let { token ->
                header(Http.Header.AUTHORIZATION, "Token $token")
            }
        }

    private fun createTempFile(identity: Identity) =
        fileService.createTempFile(
            MediaStore.MEDIA_IGNORE_FILENAME,
            "avatardownload-${identity.hashCode()}",
        )

    companion object {
        private val runningUpdates = mutableMapOf<Identity, UpdateBusinessAvatarRoutine>()

        /**
         * Update (if necessary) a business avatar
         *
         * @param forceUpdate if true, the expiry date will be ignored
         */
        @JvmStatic
        @JvmOverloads
        @WorkerThread
        fun start(
            okHttpClient: OkHttpClient,
            contactModel: ContactModel,
            fileService: FileService,
            contactService: ContactService,
            apiService: ApiService,
            forceUpdate: Boolean = false,
        ) {
            val key = contactModel.identity
            val routine = synchronized(runningUpdates) {
                if (runningUpdates[key]?.isRunning == true) {
                    return
                }
                val routine = UpdateBusinessAvatarRoutine(
                    okHttpClient = okHttpClient,
                    contactService = contactService,
                    fileService = fileService,
                    apiService = apiService,
                )
                runningUpdates.put(key, routine)
                routine
            }

            try {
                routine.run(contactModel, forceUpdate)
            } finally {
                synchronized(runningUpdates) {
                    runningUpdates.remove(key)
                }
            }
        }
    }
}

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

package ch.threema.app.workers

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.services.ApiService
import ch.threema.app.services.ContactService
import ch.threema.app.services.FileService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.Http
import ch.threema.common.HttpResponseException
import ch.threema.common.TimeProvider
import ch.threema.common.buildRequest
import ch.threema.common.contentEquals
import ch.threema.common.execute
import ch.threema.common.getExpiration
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.common.plus
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.Identity
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("GatewayProfilePicturesWorker")

/**
 * This worker runs periodically to update the profile pictures of the gateway contacts. The worker runs every [REFRESH_INTERVAL] and updates all
 * gateway contact profile pictures that would expire in the next [EXPIRY_TOLERANCE]. This increases the frequency of profile picture fetches as the
 * delay between two executions of this worker can be smaller than [REFRESH_INTERVAL].
 */
class GatewayProfilePicturesWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), KoinComponent {
    private val timeProvider: TimeProvider by inject()
    private val okHttpClient: OkHttpClient by inject()
    private val contactService: ContactService by inject()
    private val contactModelRepository: ContactModelRepository by inject()
    private val fileService: FileService by inject()
    private val apiService: ApiService by inject()

    override suspend fun doWork(): Result {
        logger.info("Running gateway profile picture worker")

        withTimeoutOrNull(timeout = 20.seconds) {
            awaitAppFullyReady()
        }
            ?: return Result.retry()

        val gatewayContacts = contactModelRepository
            .getAll()
            .filter { contactModel -> contactModel.data?.isGatewayContact() == true }

        val allSuccess = gatewayContacts.map { contactModel ->
            val contactModelData = contactModel.data ?: return@map null
            if (!contactModelData.isAvatarExpired(timeProvider.get(), EXPIRY_TOLERANCE)) {
                logger.info("Gateway profile picture is not expired yet")
                return@map null
            }

            try {
                updateGatewayProfilePicture(contactModel)
                true
            } catch (e: IOException) {
                logger.warn("Could not download the gateway profile picture. Retrying later.", e)
                false
            }
        }
            .filterNotNull()
            .all { success -> success }

        return if (allSuccess) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    /**
     * Update the gateway profile picture. Note that this does update the profile picture of the contact even if there is already a profile picture
     * that has not expired yet.
     */
    @Throws(IOException::class)
    @WorkerThread
    private fun updateGatewayProfilePicture(contactModel: ContactModel) {
        if (contactModel.data?.isGatewayContact() != true) {
            logger.warn("Aborting profile picture update for non-gateway contact")
            return
        }

        val request = createRequest(contactModel.identity)
        try {
            okHttpClient.execute(request).use { response ->
                val expires = response.getExpiration() ?: (timeProvider.get() + 1.days)
                val newProfilePictureBytes = response.getSuccessBodyOrThrow().bytes()

                if (!fileService.getUserDefinedProfilePictureStream(contactModel.identity)
                        .contentEquals(newProfilePictureBytes)
                ) {
                    // Note that the profile picture of a gateway id is stored as user defined
                    // profile picture on purpose. This prevents that it is overwritten if the
                    // gateway would suddenly start distributing its own profile picture via csp
                    // messages.
                    contactService.setUserDefinedProfilePicture(
                        contactModel.identity,
                        newProfilePictureBytes,
                        TriggerSource.LOCAL,
                    )

                    logger.info("Refreshed profile picture for gateway contact")
                } else {
                    logger.info("Profile picture for gateway contact did not change")
                }

                contactModel.setLocalAvatarExpires(expires)
            }
        } catch (e: HttpResponseException) {
            when (e.code) {
                Http.StatusCode.NOT_FOUND -> {
                    // Remove existing profile picture
                    // Note that the profile picture of a gateway id is stored as user defined
                    // profile picture on purpose. This prevents that it is overwritten if the
                    // gateway would suddenly start distributing its own profile picture via csp
                    // messages.
                    contactService.removeUserDefinedProfilePicture(contactModel.identity, TriggerSource.LOCAL)

                    contactModel.setLocalAvatarExpires(timeProvider.get() + 1.days)

                    logger.info("Removed profile picture")
                }

                Http.StatusCode.UNAUTHORIZED -> {
                    logger.warn("Unauthorized access to avatar server")

                    // TODO(ANDR-4201): Remove this explicit check and handle it centrally instead
                    if (ConfigUtils.isOnPremBuild()) {
                        logger.info("Invalidating auth token")
                        apiService.invalidateAuthToken()
                    }

                    throw IOException(
                        "Could not get gateway profile picture due to missing authorization",
                        e,
                    )
                }

                else -> {
                    throw IOException("Failed to download gateway profile picture", e)
                }
            }
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

    companion object {
        private val REFRESH_INTERVAL = 24.hours
        private val EXPIRY_TOLERANCE = REFRESH_INTERVAL / 2

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<GatewayProfilePicturesWorker>(
                repeatInterval = REFRESH_INTERVAL.inWholeHours,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    uniqueWorkName = WorkerNames.WORKER_GATEWAY_PROFILE_PICTURES,
                    existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
                    request = periodicWorkRequest,
                )
        }
    }
}

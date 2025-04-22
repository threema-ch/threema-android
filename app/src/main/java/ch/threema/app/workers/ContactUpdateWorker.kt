/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import ch.threema.app.ThreemaApplication
import ch.threema.app.ThreemaApplication.WORKER_CONTACT_UPDATE_PERIODIC_NAME
import ch.threema.app.ThreemaApplication.WORKER_IDENTITY_STATES_PERIODIC_NAME
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ContactService
import ch.threema.app.services.PollingHelper
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.WorkManagerUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ModelDeletedException
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.protocol.api.APIConnector
import java.util.concurrent.TimeUnit

private val logger = LoggingUtil.getThreemaLogger("ContactUpdateWorker")

/**
 * The contact update worker sends the feature mask (if necessary) and updates all contacts. This
 * includes fetching the states, types, and features masks.
 */
class ContactUpdateWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : Worker(context, workerParameters) {
    override fun doWork(): Result {
        val serviceManager = ThreemaApplication.getServiceManager()

        val success = sendFeatureMaskAndUpdateContacts(
            serviceManager?.modelRepositories?.contacts,
            serviceManager?.contactService,
            serviceManager?.apiConnector,
            serviceManager?.userService,
            serviceManager?.preferenceService,
            PollingHelper(context, "contactUpdateWorker"),
        )

        return if (success) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    companion object {
        @JvmStatic
        fun schedulePeriodicSync(context: Context, preferenceService: PreferenceService) {
            // We use the sync interval from the previously named IdentityStatesWorker
            val schedulePeriodMs =
                WorkManagerUtil.normalizeSchedulePeriod(preferenceService.identityStateSyncIntervalS)

            logger.info(
                "Initializing contact update sync. Requested schedule period: {} ms",
                schedulePeriodMs,
            )

            try {
                val workManager = WorkManager.getInstance(context)

                if (WorkManagerUtil.shouldScheduleNewWorkManagerInstance(
                        workManager,
                        WORKER_CONTACT_UPDATE_PERIODIC_NAME,
                        schedulePeriodMs,
                    )
                ) {
                    logger.debug("Scheduling new job")

                    // Cancel the work with the old name as the IdentityStatesWorker class does not
                    // exist anymore.
                    workManager.cancelUniqueWork(WORKER_IDENTITY_STATES_PERIODIC_NAME)

                    // Schedule the start of the service according to schedule period
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val workRequest = PeriodicWorkRequest.Builder(
                        ContactUpdateWorker::class.java,
                        schedulePeriodMs,
                        TimeUnit.MILLISECONDS,
                    )
                        .setConstraints(constraints)
                        .addTag(schedulePeriodMs.toString())
                        .setInitialDelay(1000, TimeUnit.MILLISECONDS)
                        .build()

                    workManager.enqueueUniquePeriodicWork(
                        WORKER_CONTACT_UPDATE_PERIODIC_NAME,
                        ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                        workRequest,
                    )
                }
            } catch (e: IllegalStateException) {
                logger.error("Unable to schedule ContactUpdateWorker", e)
            }
        }

        @JvmStatic
        fun performOneTimeSync(context: Context) {
            val workRequest = OneTimeWorkRequest.Builder(ContactUpdateWorker::class.java)
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }

        fun cancelPeriodicSync(context: Context): Operation {
            return WorkManagerUtil.cancelUniqueWork(context, WORKER_CONTACT_UPDATE_PERIODIC_NAME)
        }

        @WorkerThread
        fun sendFeatureMaskAndUpdateContacts(serviceManager: ServiceManager) =
            sendFeatureMaskAndUpdateContacts(
                serviceManager.modelRepositories.contacts,
                serviceManager.contactService,
                serviceManager.apiConnector,
                serviceManager.userService,
                serviceManager.preferenceService,
                null,
            )

        @WorkerThread
        private fun sendFeatureMaskAndUpdateContacts(
            contactModelRepository: ContactModelRepository?,
            contactService: ContactService?,
            apiConnector: APIConnector?,
            userService: UserService?,
            preferenceService: PreferenceService?,
            pollingHelper: PollingHelper?,
        ): Boolean {
            logger.info("Starting contact update")

            if (contactService == null || apiConnector == null || userService == null || preferenceService == null ||
                contactModelRepository == null
            ) {
                logger.warn("Services not available while updating contact states")
                return false
            }

            if (!userService.hasIdentity()) {
                logger.warn("No identity found. Contact update not needed.")
                // Treat a contact update as successful if no identity is available to prevent
                // unnecessary retries
                return true
            }

            if (!userService.sendFeatureMask()) {
                logger.warn("Feature mask could not be sent. Aborting contact update")
                return false
            }

            // TODO(ANDR-3172): Fetch all contacts using the contact model repository
            val contactModels = contactService.find(object : ContactService.Filter {
                override fun states(): Array<IdentityState> =
                    arrayOf(IdentityState.ACTIVE, IdentityState.INACTIVE)

                override fun requiredFeature() = null

                override fun fetchMissingFeatureLevel() = null

                override fun includeMyself() = true

                override fun includeHidden() = true

                override fun onlyWithReceiptSettings() = false
            }).mapNotNull { contactModelRepository.getByIdentity(it.identity) }

            val success = if (contactModels.isNotEmpty()) {
                fetchAndUpdateContactModels(
                    contactModels,
                    apiConnector,
                    preferenceService,
                )
            } else {
                true
            }

            // Force a quick poll
            pollingHelper?.poll(false)

            logger.debug("Finished contact update; success={}", success)

            return success
        }

        fun fetchAndUpdateContactModels(
            contactModels: List<ContactModel>,
            apiConnector: APIConnector,
            preferenceService: PreferenceService,
        ): Boolean {
            val identities = contactModels.map { it.identity }.toTypedArray()
            val contactModelMap = contactModels.associateBy { it.identity }

            try {
                val result = apiConnector.checkIdentityStates(identities)

                for ((i, identity) in result.identities.withIndex()) {
                    val contactModel = contactModelMap[identity] ?: continue

                    val newState = when (result.states[i]) {
                        IdentityState.ACTIVE.value -> IdentityState.ACTIVE
                        IdentityState.INACTIVE.value -> IdentityState.INACTIVE
                        IdentityState.INVALID.value -> IdentityState.INVALID

                        // In case we receive an unexpected value from the server, we set the new
                        // state to null. We should not abort these steps as this contact update
                        // routine is required to be run when setting up the app. We do not consider
                        // an invalid state to be a failure that should prevent using the app.
                        else -> null
                    }

                    if (newState == null) {
                        logger.warn(
                            "Received invalid state {} for identity {}",
                            result.states[i],
                            identity,
                        )
                    }

                    val newIdentityType = when (result.types[i]) {
                        0 -> IdentityType.NORMAL
                        1 -> IdentityType.WORK
                        else -> {
                            logger.warn(
                                "Received invalid type {} for identity {}",
                                result.types[i],
                                identity,
                            )
                            IdentityType.NORMAL
                        }
                    }

                    val newFeatureMask: Long? = result.featureMasks[i]

                    updateContactModel(
                        contactModel,
                        newState,
                        newIdentityType,
                        newFeatureMask,
                    )

                    if (result.checkInterval > 0) {
                        // Save new interval duration
                        preferenceService.setIdentityStateSyncInterval(result.checkInterval)
                    }
                }

                return true
            } catch (e: Exception) {
                logger.error("Could not fetch contact updates", e)
                return false
            }
        }

        private fun updateContactModel(
            contactModel: ContactModel,
            newState: IdentityState?,
            newIdentityType: IdentityType,
            newFeatureMask: Long?,
        ) {
            try {
                val data = contactModel.data.value ?: return

                // Only update the state if it is a valid state change. Note that changing to null is
                // not allowed and will not result in any change.
                if (newState != null && ContactUtil.allowedChangeToState(
                        data.activityState,
                        newState,
                    )
                ) {
                    contactModel.setActivityStateFromLocal(newState)
                }

                contactModel.setIdentityTypeFromLocal(newIdentityType)

                if (newFeatureMask == null) {
                    logger.warn("Feature mask for contact {} is null", contactModel.identity)
                } else {
                    contactModel.setFeatureMaskFromLocal(newFeatureMask)
                }
            } catch (e: ModelDeletedException) {
                logger.warn(
                    "Could not update contact {} because the model has been deleted",
                    contactModel.identity,
                    e,
                )
            }
        }
    }
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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
import androidx.work.Worker
import androidx.work.WorkerParameters
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ContactService
import ch.threema.app.services.PollingHelper
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.utils.ContactUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.IdentityState
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.storage.models.ContactModel

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
            serviceManager?.contactService,
            serviceManager?.apiConnector,
            serviceManager?.userService,
            serviceManager?.preferenceService,
            PollingHelper(context, "contactUpdateWorker"),
            applicationContext
        )

        return if (success) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    companion object {
        fun sendFeatureMaskAndUpdateContacts(serviceManager: ServiceManager, context: Context) =
            sendFeatureMaskAndUpdateContacts(
                serviceManager.contactService,
                serviceManager.apiConnector,
                serviceManager.userService,
                serviceManager.preferenceService,
                null,
                context,
            )

        private fun sendFeatureMaskAndUpdateContacts(
            contactService: ContactService?,
            apiConnector: APIConnector?,
            userService: UserService?,
            preferenceService: PreferenceService?,
            pollingHelper: PollingHelper?,
            context: Context,
        ): Boolean {
            logger.info("Starting contact update")

            if (contactService == null || apiConnector == null || userService == null || preferenceService == null) {
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

            val contactModels = contactService.find(object : ContactService.Filter {
                override fun states(): Array<ContactModel.State> =
                    arrayOf(ContactModel.State.ACTIVE, ContactModel.State.INACTIVE)

                override fun requiredFeature() = null

                override fun fetchMissingFeatureLevel() = null

                override fun includeMyself() = true

                override fun includeHidden() = true

                override fun onlyWithReceiptSettings() = false
            })

            val success = if (contactModels.isNotEmpty()) {
                fetchAndUpdateContactModels(
                    contactModels,
                    apiConnector,
                    contactService,
                    preferenceService,
                    context,
                )
            } else {
                true
            }

            // Force a quick poll
            pollingHelper?.poll(false)

            logger.debug("Finished contact update; success={}", success)

            return success
        }

        private fun fetchAndUpdateContactModels(
            contactModels: List<ContactModel>,
            apiConnector: APIConnector,
            contactService: ContactService,
            preferenceService: PreferenceService,
            context: Context,
        ): Boolean {
            val identities = contactModels.map { it.identity }.toTypedArray()
            val contactModelMap = contactModels.associateBy { it.identity }

            try {
                val result = apiConnector.checkIdentityStates(identities)

                for ((i, identity) in result.identities.withIndex()) {
                    val contactModel = contactModelMap[identity] ?: continue

                    val newState = when (result.states[i]) {
                        IdentityState.ACTIVE -> ContactModel.State.ACTIVE
                        IdentityState.INACTIVE -> ContactModel.State.INACTIVE
                        IdentityState.INVALID -> ContactModel.State.INVALID

                        // In case we receive an unexpected value from the server, we set the new
                        // state to null. We should not abort these steps as this contact update
                        // routine is required to be run when setting up the app. We do not consider
                        // an invalid state to be a failure that should prevent using the app.
                        else -> null
                    }

                    if (newState == null) {
                        logger.warn(
                            "Received invalid state {} for identity {}", result.states[i], identity
                        )
                    }

                    val newType = result.types[i]

                    val newFeatureMask: Long? = result.featureMasks[i]

                    updateContactModel(
                        contactModel,
                        newState,
                        newType,
                        newFeatureMask,
                        contactService
                    )

                    if (result.checkInterval > 0) {
                        // Save new interval duration
                        preferenceService.setRoutineInterval(
                            context.getString(R.string.preferences__identity_states_check_interval),
                            result.checkInterval
                        )
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
            newState: ContactModel.State?,
            newType: Int,
            newFeatureMask: Long?,
            contactService: ContactService,
        ) {
            var updated = false

            // Only update the state if it is a valid state change. Note that changing to null is
            // not allowed and will not result in any change.
            if (ContactUtil.allowedChangeToState(contactModel, newState)) {
                contactModel.state = newState
                updated = true
            }

            if (contactModel.identityType != newType) {
                contactModel.identityType = newType
                updated = true
            }

            if (newFeatureMask == null) {
                logger.warn("Feature mask for contact {} is null", contactModel.identity)
            } else if (newFeatureMask != contactModel.featureMask) {
                contactModel.featureMask = newFeatureMask
                updated = true
            }

            if (updated) {
                contactService.save(contactModel)
            }
        }
    }
}

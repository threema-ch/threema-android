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

package ch.threema.app.asynctasks

import androidx.annotation.WorkerThread
import ch.threema.app.services.license.LicenseService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.now
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.work.WorkContact
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("AddOrUpdateWorkContactBackgroundTask")

/**
 * Creates the provided contact if it does not exist. If it already exists, then it is updated in
 * case it wasn't a direct work contact that is at least server verified.
 *
 * This task does not do anything if it isn't a work build.
 */
open class AddOrUpdateWorkContactBackgroundTask(
    /**
     * The work contact information of the contact that will be added or updated.
     */
    private val workContact: WorkContact,
    /**
     * The user's identity.
     */
    private val myIdentity: Identity,
    /**
     * The contact model repository.
     */
    private val contactModelRepository: ContactModelRepository,
) : BackgroundTask<ContactModel?> {
    /**
     * Add the work contact if the identity belongs to a work contact.
     *
     * @return the newly inserted contact model or null if it could not be inserted
     */
    @WorkerThread
    fun runSynchronously(): ContactModel? {
        runBefore()

        runInBackground().let {
            runAfter(it)
            return it
        }
    }

    @WorkerThread
    override fun runInBackground(): ContactModel? {
        if (!ConfigUtils.isWorkBuild()) {
            return null
        }

        if (workContact.publicKey.size != NaCl.PUBLIC_KEY_BYTES) {
            // Ignore work contact with invalid public key
            logger.warn(
                "Work contact has invalid public key of size {}",
                workContact.publicKey.size,
            )
            return null
        }

        if (workContact.threemaId == myIdentity) {
            // Do not add our own ID as a contact
            logger.warn("Cannot add the user's identity as work contact")
            return null
        }

        val contactModel = contactModelRepository.getByIdentity(workContact.threemaId)

        return if (contactModel != null) {
            updateContact(contactModel)
            contactModel
        } else {
            createContact()
        }
    }

    @WorkerThread
    private fun createContact(): ContactModel? {
        return runBlocking {
            try {
                contactModelRepository.createFromLocal(
                    ContactModelData(
                        identity = workContact.threemaId,
                        publicKey = workContact.publicKey,
                        createdAt = now(),
                        firstName = workContact.firstName ?: "",
                        lastName = workContact.lastName ?: "",
                        nickname = null,
                        idColor = IdColor.ofIdentity(workContact.threemaId),
                        verificationLevel = VerificationLevel.SERVER_VERIFIED,
                        workVerificationLevel = WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
                        // TODO(ANDR-3159): Fetch identity type
                        identityType = IdentityType.WORK,
                        acquaintanceLevel = AcquaintanceLevel.DIRECT,
                        // TODO(ANDR-3159): Fetch identity state
                        activityState = IdentityState.ACTIVE,
                        syncState = ContactSyncState.INITIAL,
                        // TODO(ANDR-3159): Fetch feature mask
                        featureMask = 0u,
                        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                        isArchived = false,
                        androidContactLookupKey = null,
                        localAvatarExpires = null,
                        isRestored = false,
                        profilePictureBlobId = null,
                        jobTitle = workContact.jobTitle,
                        department = workContact.department,
                        notificationTriggerPolicyOverride = null,
                    ),
                )
            } catch (e: ContactCreateException) {
                logger.error("Could not create work contact", e)
                null
            }
        }
    }

    private fun updateContact(contactModel: ContactModel) {
        val currentContactModelData: ContactModelData = contactModel.data ?: run {
            logger.error("Contact has already been deleted")
            return
        }

        // Update first and last name if the contact is not synchronized
        if (
            currentContactModelData.androidContactLookupKey == null &&
            (workContact.firstName != null || workContact.lastName != null)
        ) {
            contactModel.setNameFromLocal(workContact.firstName ?: "", workContact.lastName ?: "")
        }

        // Update jobTitle if it changed
        if (currentContactModelData.jobTitle != workContact.jobTitle) {
            contactModel.setJobTitleFromLocal(workContact.jobTitle)
        }

        // Update department if it changed
        if (currentContactModelData.department != workContact.department) {
            contactModel.setDepartmentFromLocal(workContact.department)
        }

        // Update work verification level
        contactModel.setWorkVerificationLevelFromLocal(WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED)

        // Update acquaintance level
        contactModel.setAcquaintanceLevelFromLocal(AcquaintanceLevel.DIRECT)

        // Update verification level (except it would be a downgrade)
        if (currentContactModelData.verificationLevel == VerificationLevel.UNVERIFIED) {
            contactModel.setVerificationLevelFromLocal(VerificationLevel.SERVER_VERIFIED)
        }
    }
}

/**
 * This task fetches the information whether the given identity is a work identity. If it is, then
 * a new work contact is created or the existing contact is updated.
 *
 * This task does not do anything if it is not a work build.
 */
class AddOrUpdateWorkIdentityBackgroundTask(
    private val identity: Identity,
    private val myIdentity: Identity,
    private val licenseService: LicenseService<*>,
    private val apiConnector: APIConnector,
    private val contactModelRepository: ContactModelRepository,
) : BackgroundTask<ContactModel?> {
    /**
     * Add the work contact if the identity belongs to a work contact.
     *
     * @return the newly inserted contact model or null if it could not be inserted
     */
    @WorkerThread
    fun runSynchronously(): ContactModel? {
        runBefore()

        runInBackground().let {
            runAfter(it)
            return it
        }
    }

    @WorkerThread
    override fun runInBackground(): ContactModel? {
        if (!ConfigUtils.isWorkBuild()) {
            return null
        }

        val credentials = licenseService.loadCredentials()

        if (credentials !is UserCredentials) {
            logger.error("No user credentials available")
            return null
        }

        val workContact = apiConnector.fetchWorkContacts(
            credentials.username,
            credentials.password,
            arrayOf(identity),
        ).firstOrNull() ?: run {
            logger.info("Identity {} is not a work contact", identity)
            return null
        }

        if (workContact.threemaId != identity) {
            logger.error(
                "Received different identity from server: {} instead of {}",
                workContact.threemaId,
                identity,
            )
            return null
        }

        return AddOrUpdateWorkContactBackgroundTask(
            workContact,
            myIdentity,
            contactModelRepository,
        ).runSynchronously()
    }
}

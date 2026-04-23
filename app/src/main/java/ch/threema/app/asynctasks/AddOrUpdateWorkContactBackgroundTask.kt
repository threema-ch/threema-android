package ch.threema.app.asynctasks

import androidx.annotation.WorkerThread
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.now
import ch.threema.data.datatypes.AvailabilityStatus
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
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.api.work.WorkContact
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import kotlinx.coroutines.runBlocking

private val logger = getThreemaLogger("AddOrUpdateWorkContactBackgroundTask")

/**
 * Creates the provided contact if it does not exist. If it already exists, then it is updated in
 * case it wasn't a direct work contact that is at least server verified.
 *
 * This task does not do anything if it isn't a work build.
 */
class AddOrUpdateWorkContactBackgroundTask(
    /**
     * The work contact information of the contact that will be added or updated.
     */
    private val workContact: WorkContact,
    /**
     * The user's identity.
     */
    private val myIdentity: IdentityString,
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

        runInBackground()
            .let { contactModel: ContactModel? ->
                runAfter(
                    result = contactModel,
                )
                return contactModel
            }
    }

    @WorkerThread
    override fun runInBackground(): ContactModel? {
        logger.info("Adding or updating work contact with identity {}", workContact.threemaId)

        if (!ConfigUtils.isWorkBuild()) {
            logger.error("Cannot add or update work contact in non-work builds")
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
        logger.info("Creating work contact {}", workContact.threemaId)

        return runBlocking {
            try {
                contactModelRepository.createFromLocal(
                    contactModelData = ContactModelData(
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
                        androidContactLookupInfo = null,
                        localAvatarExpires = null,
                        isRestored = false,
                        profilePictureBlobId = null,
                        jobTitle = workContact.jobTitle,
                        department = workContact.department,
                        notificationTriggerPolicyOverride = null,
                        availabilityStatus = workContact.getAvailabilityStatusOrNone(),
                        workLastFullSyncAt = workContact.workLastFullSyncAt,
                    ),
                )
            } catch (e: ContactCreateException) {
                logger.error("Could not create work contact", e)
                null
            }
        }
    }

    @WorkerThread
    private fun updateContact(contactModel: ContactModel) {
        logger.info("Updating work contact {}", contactModel.identity)

        val currentContactModelData: ContactModelData = contactModel.data ?: run {
            logger.error("Contact has already been deleted")
            return
        }

        // Update first and last name if the contact is not synchronized
        if (
            currentContactModelData.androidContactLookupInfo == null &&
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

        // Update the workLastFullSyncAt timestamp
        workContact.workLastFullSyncAt?.let { workLastFullSyncAt ->
            if (currentContactModelData.workLastFullSyncAt != workContact.workLastFullSyncAt) {
                contactModel.setWorkLastFullSyncFromLocal(workLastFullSyncAt)
            }
        }

        // Update availability status
        if (ConfigUtils.supportsAvailabilityStatus()) {
            val workContactAvailabilityStatus = workContact.getAvailabilityStatusOrNone()
            if (currentContactModelData.availabilityStatus != workContactAvailabilityStatus) {
                contactModel.setAvailabilityStatusFromLocal(workContactAvailabilityStatus)
            }
        }
    }

    private fun WorkContact.getAvailabilityStatusOrNone(): AvailabilityStatus =
        availability
            ?.let(AvailabilityStatus::fromProtocolBase64)
            ?: AvailabilityStatus.None
}

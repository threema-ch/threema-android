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

package ch.threema.app.asynctasks

import android.content.Context
import ch.threema.app.R
import ch.threema.app.utils.AppRestrictionUtil
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.APIConnector.FetchIdentityResult
import ch.threema.domain.protocol.api.APIConnector.HttpConnectionException
import ch.threema.domain.protocol.api.APIConnector.NetworkException
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("AddContactBackgroundTask")

/**
 * This background task should be used if a new identity should be added to the contacts. The task
 * will fetch the public key, identity type, activity state, and feature mask from the server.
 *
 * If [expectedPublicKey] is set, this background task verifies that the public key matches before
 * adding the new contact. If the contact already exists, it checks that the public key matches and
 * returns [Failed] if it doesn't match.
 *
 * This task also updates the contact if it already exists. This includes changing the acquaintance
 * level from group to direct or changing the verification level to fully verified.
 *
 * Note that this task can be overridden and the behavior can be adjusted by overwriting [onBefore]
 * and [onFinished].
 */
open class AddOrUpdateContactBackgroundTask(
    protected val identity: String,
    private val myIdentity: String,
    private val apiConnector: APIConnector,
    private val contactModelRepository: ContactModelRepository,
    private val addContactRestrictionPolicy: AddContactRestrictionPolicy,
    private val context: Context,
    private val expectedPublicKey: ByteArray? = null,
) : BackgroundTask<ContactAddResult> {

    final override fun runBefore() {
        onBefore()
    }

    final override fun runInBackground(): ContactAddResult {
        if (identity == myIdentity) {
            return failed(R.string.identity_already_exists)
        }

        // Update contact if it exists
        contactModelRepository.getByIdentity(identity)?.let {
            val data = it.data.value

            if (data != null) {
                return updateContact(it, data, expectedPublicKey)
            }
        }

        // Only proceed if adding contacts is allowed
        if (addContactRestrictionPolicy == AddContactRestrictionPolicy.CHECK
            && AppRestrictionUtil.isAddContactDisabled(context)
        ) {
            return PolicyViolation
        }

        // Fetch the identity
        val result = try {
            apiConnector.fetchIdentity(identity)
        } catch (e: Exception) {
            logger.error("Failed to fetch identity", e)

            when (e) {
                is HttpConnectionException -> {
                    if (e.errorCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        return failed(R.string.invalid_threema_id)
                    } else {
                        return failed(R.string.connection_error)
                    }
                }

                is NetworkException, is ThreemaException -> {
                    return failed(R.string.connection_error)
                }

                else -> {
                    throw e
                }
            }
        }

        // Add the new contact
        return addNewContact(result, expectedPublicKey)
    }

    final override fun runAfter(result: ContactAddResult) {
        onFinished(result)
    }

    /**
     * This will be run before the contact is being fetched from the server.
     */
    open fun onBefore() {}

    /**
     * As soon as the contact has been added or an error occurred, this method is run with the
     * provided result.
     */
    open fun onFinished(result: ContactAddResult) {}

    private fun addNewContact(
        result: FetchIdentityResult,
        expectedPublicKey: ByteArray?,
    ): ContactAddResult {
        val verificationLevel = if (expectedPublicKey != null) {
            if (expectedPublicKey.contentEquals(result.publicKey)) {
                VerificationLevel.FULLY_VERIFIED
            } else {
                return failed(R.string.id_mismatch)
            }
        } else {
            VerificationLevel.UNVERIFIED
        }

        val identityType = when (result.type) {
            0 -> IdentityType.NORMAL
            1 -> IdentityType.WORK
            else -> {
                logger.warn("Identity fetch returned invalid identity type: {}", result.type)
                IdentityType.NORMAL
            }
        }

        val activityState = when (result.state) {
            IdentityState.ACTIVE -> ch.threema.storage.models.ContactModel.State.ACTIVE
            IdentityState.INACTIVE -> ch.threema.storage.models.ContactModel.State.INACTIVE
            IdentityState.INVALID -> ch.threema.storage.models.ContactModel.State.INVALID
            else -> {
                logger.warn("Identity fetch returned invalid identity state: {}", result.state)
                ch.threema.storage.models.ContactModel.State.ACTIVE
            }
        }

        return runBlocking {
            try {
                val contactModel = contactModelRepository.createFromLocal(
                    result.identity,
                    result.publicKey,
                    Date(),
                    identityType,
                    AcquaintanceLevel.DIRECT,
                    activityState,
                    result.featureMask.toULong(),
                    verificationLevel,
                )
                Success(contactModel)
            } catch (e: ContactCreateException) {
                logger.error("Could not insert new contact", e)
                failed(R.string.add_contact_failed)
            }
        }
    }

    private fun updateContact(contactModel: ContactModel, data: ContactModelData, expectedPublicKey: ByteArray?): ContactAddResult {
        var verificationLevelChanged = false
        var contactVerifiedAgain = false
        var acquaintanceLevelChanged = false

        if (expectedPublicKey != null) {
            if (expectedPublicKey.contentEquals(data.publicKey)) {
                if (data.verificationLevel != VerificationLevel.FULLY_VERIFIED) {
                    contactModel.setVerificationLevelFromLocal(VerificationLevel.FULLY_VERIFIED)
                    verificationLevelChanged = true
                } else {
                    contactVerifiedAgain = true
                }
            } else {
                return failed(R.string.id_mismatch)
            }
        }

        if (data.acquaintanceLevel == AcquaintanceLevel.GROUP) {
            contactModel.setAcquaintanceLevelFromLocal(AcquaintanceLevel.DIRECT)
            acquaintanceLevelChanged = true
        }

        return when {
            acquaintanceLevelChanged || verificationLevelChanged -> ContactModified(
                contactModel, acquaintanceLevelChanged, verificationLevelChanged
            )

            contactVerifiedAgain -> AlreadyVerified(contactModel)
            else -> ContactExists(contactModel)
        }
    }

    private fun failed(stringId: Int) = Failed(context.getString(stringId))
}

/**
 * This is used to define whether the contact add restriction should be respected or if a contact
 * should be added anyways.
 */
enum class AddContactRestrictionPolicy {
    /**
     * The add contact restriction must be followed and a contact won't be added if this is
     * prohibited. In this case the result will be of the type [PolicyViolation].
     */
    CHECK,

    /**
     * The add contact restriction won't be respected and the contact will be added anyways. Note
     * that this must only be used in cases where adding the contact is not triggered by the user.
     */
    IGNORE
}

/**
 * The result type of adding a contact.
 */
sealed interface ContactAddResult

/**
 * The contact has been added successfully. The new contact is provided.
 */
data class Success(val contactModel: ContactModel) : ContactAddResult

/**
 * The contact already existed and has now been updated.
 */
data class ContactModified(
    /**
     * The updated contact model.
     */
    val contactModel: ContactModel,
    /**
     * If true, the acquaintance level changed from [AcquaintanceLevel.GROUP] to
     * [AcquaintanceLevel.DIRECT].
     */
    val acquaintanceLevelChanged: Boolean,
    /**
     * If true, the verification level has changed to [VerificationLevel.FULLY_VERIFIED].
     */
    val verificationLevelChanged: Boolean,
) : ContactAddResult

/**
 * The result type when adding the contact has failed.
 */
interface Error : ContactAddResult

/**
 * The contact already exists. This is only returned, if no expected public key is given and the
 * contact already exists. This means, that neither the verification level nor the acquaintance
 * level did change.
 */
data class ContactExists(val contactModel: ContactModel) : Error

/**
 * The contact already exists and has been fully verified before.
 */
data class AlreadyVerified(val contactModel: ContactModel) : Error

/**
 * Adding the contact failed. The [message] contains a (translated) error message that can be shown
 * to the user.
 */
data class Failed(val message: String) : Error

/**
 * The contact could not be added since adding contacts is restricted.
 */
object PolicyViolation : Error

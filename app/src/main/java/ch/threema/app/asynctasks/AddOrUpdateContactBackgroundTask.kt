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

import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import ch.threema.app.R
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.Http
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
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.APIConnector.FetchIdentityResult
import ch.threema.domain.protocol.api.APIConnector.HttpConnectionException
import ch.threema.domain.protocol.api.APIConnector.NetworkException
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("AddOrUpdateContactBackgroundTask")

/**
 * This background task should be used if a new identity should be added to the contacts. The task
 * will fetch the public key, identity type, activity state, and feature mask from the server.
 *
 * If [expectedPublicKey] is set, this background task verifies that the public key matches before
 * adding the new contact. If the contact already exists, it checks that the public key matches and
 * returns [Failed] if it doesn't match.
 *
 * This task also updates the contact if it already exists. This includes changing the acquaintance
 * level to [acquaintanceLevel] and the verification level to fully verified (if [expectedPublicKey]
 * is provided and matches).
 *
 * Note that this task can be overridden and the behavior can be adjusted by overwriting [onBefore],
 * [onContactAdded], and [onFinished]. For tasks that do not need to perform any additional
 * background work, the [BasicAddOrUpdateContactBackgroundTask] can be used.
 */
abstract class AddOrUpdateContactBackgroundTask<T>(
    protected val identity: Identity,
    protected val acquaintanceLevel: AcquaintanceLevel,
    private val myIdentity: Identity,
    private val apiConnector: APIConnector,
    private val contactModelRepository: ContactModelRepository,
    private val addContactRestrictionPolicy: AddContactRestrictionPolicy,
    private val context: Context,
    private val expectedPublicKey: ByteArray? = null,
) : BackgroundTask<T> {
    /**
     * Run this task synchronously on the same thread. Note that this performs network communication
     * and must not be run on the main thread.
     */
    @WorkerThread
    fun runSynchronously(): T {
        runBefore()

        val result = runInBackground()

        runAfter(result)

        return result
    }

    /**
     * Do not call this method directly. This should only be called by the background executor.
     */
    final override fun runBefore() {
        onBefore()
    }

    /**
     * Do not call this method directly. This should only be called by the background executor. If
     * the task should be run on the same thread, use [runSynchronously].
     */
    final override fun runInBackground(): T {
        val result = checkAndAddNewContact()

        return onContactAdded(result)
    }

    /**
     * Do not call this method directly. This should only be called by the background executor.
     */
    final override fun runAfter(result: T) {
        onFinished(result)
    }

    /**
     * This will be run before the contact is being fetched from the server.
     */
    open fun onBefore() = Unit

    /**
     * As soon as the contact has been added or an error occurred, this method is run with the
     * provided result. Note that this method is run on the executor's background thread. The result
     * of it will be passed to [onFinished].
     */
    abstract fun onContactAdded(result: ContactResult): T

    /**
     * This method is run on the UI thread after [onContactAdded] has been executed. Override this
     * method for making UI changes after the contact has been added and processed.
     */
    open fun onFinished(result: T) = Unit

    private fun checkAndAddNewContact(): ContactResult {
        if (identity == myIdentity) {
            return UserIdentity(context)
        }

        // Update contact if it exists
        contactModelRepository.getByIdentity(identity)?.let {
            val data = it.data

            if (data != null) {
                return updateContact(it, data, expectedPublicKey)
            }
        }

        // Only proceed if adding contacts is allowed
        if (addContactRestrictionPolicy == AddContactRestrictionPolicy.CHECK &&
            AppRestrictionUtil.isAddContactDisabled(context)
        ) {
            return PolicyViolation(context)
        }

        // Fetch the identity
        val result = try {
            apiConnector.fetchIdentity(identity)
        } catch (e: Exception) {
            logger.error("Failed to fetch identity", e)

            when (e) {
                is HttpConnectionException -> {
                    return if (e.errorCode == Http.StatusCode.NOT_FOUND) {
                        InvalidThreemaId(context)
                    } else {
                        ConnectionError(context)
                    }
                }

                is NetworkException, is ThreemaException -> {
                    return ConnectionError(context)
                }

                else -> {
                    throw e
                }
            }
        }

        // Add the new contact
        return addNewContact(result, expectedPublicKey)
    }

    private fun addNewContact(
        result: FetchIdentityResult,
        expectedPublicKey: ByteArray?,
    ): ContactResult {
        val verificationLevel = if (expectedPublicKey != null) {
            if (expectedPublicKey.contentEquals(result.publicKey)) {
                VerificationLevel.FULLY_VERIFIED
            } else {
                return RemotePublicKeyMismatch(context)
            }
        } else {
            VerificationLevel.UNVERIFIED
        }

        return runBlocking {
            try {
                val contactModel = contactModelRepository.createFromLocal(
                    ContactModelData(
                        identity = result.identity,
                        publicKey = result.publicKey,
                        createdAt = now(),
                        firstName = "",
                        lastName = "",
                        nickname = null,
                        idColor = IdColor.ofIdentity(result.identity),
                        verificationLevel = verificationLevel,
                        workVerificationLevel = WorkVerificationLevel.NONE,
                        identityType = result.getIdentityType(),
                        acquaintanceLevel = acquaintanceLevel,
                        activityState = result.getIdentityState(),
                        syncState = ContactSyncState.INITIAL,
                        featureMask = result.featureMask.toULong(),
                        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                        isArchived = false,
                        androidContactLookupKey = null,
                        localAvatarExpires = null,
                        isRestored = false,
                        profilePictureBlobId = null,
                        jobTitle = null,
                        department = null,
                        notificationTriggerPolicyOverride = null,
                    ),
                )
                ContactCreated(contactModel)
            } catch (e: ContactCreateException) {
                logger.error("Could not insert new contact", e)

                val existingContact = contactModelRepository.getByIdentity(identity)
                if (existingContact != null) {
                    ContactExists(existingContact)
                } else {
                    GenericFailure(context)
                }
            }
        }
    }

    private fun updateContact(
        contactModel: ContactModel,
        currentData: ContactModelData,
        expectedPublicKey: ByteArray?,
    ): ContactResult {
        var verificationLevelChanged = false
        var contactVerifiedAgain = false
        var acquaintanceLevelChanged = false

        if (expectedPublicKey != null) {
            if (expectedPublicKey.contentEquals(currentData.publicKey)) {
                if (currentData.verificationLevel != VerificationLevel.FULLY_VERIFIED) {
                    contactModel.setVerificationLevelFromLocal(VerificationLevel.FULLY_VERIFIED)
                    verificationLevelChanged = true
                } else {
                    contactVerifiedAgain = true
                }
            } else {
                return LocalPublicKeyMismatch(contactModel, context)
            }
        }

        if (currentData.acquaintanceLevel != acquaintanceLevel) {
            contactModel.setAcquaintanceLevelFromLocal(acquaintanceLevel)
            acquaintanceLevelChanged = true
        }

        return when {
            acquaintanceLevelChanged || verificationLevelChanged -> ContactModified(
                contactModel,
                acquaintanceLevelChanged,
                verificationLevelChanged,
            )

            contactVerifiedAgain -> AlreadyVerified(contactModel)
            else -> ContactExists(contactModel)
        }
    }
}

fun FetchIdentityResult.getIdentityType(): IdentityType = when (type) {
    0 -> IdentityType.NORMAL
    1 -> IdentityType.WORK
    else -> {
        logger.warn("Identity fetch returned invalid identity type: {}", type)
        IdentityType.NORMAL
    }
}

fun FetchIdentityResult.getIdentityState(): IdentityState = when (state) {
    IdentityState.ACTIVE.value -> IdentityState.ACTIVE
    IdentityState.INACTIVE.value -> IdentityState.INACTIVE
    IdentityState.INVALID.value -> IdentityState.INVALID
    else -> {
        logger.warn("Identity fetch returned invalid identity state: {}", state)
        IdentityState.ACTIVE
    }
}

/**
 * Use this task for creating a new contact when no additional background work is required after the
 * contact has been created. The [ContactResult] is directly passed to [onFinished]. See
 * [AddOrUpdateContactBackgroundTask] for more information about contact creation.
 */
open class BasicAddOrUpdateContactBackgroundTask(
    identity: Identity,
    acquaintanceLevel: AcquaintanceLevel,
    myIdentity: Identity,
    apiConnector: APIConnector,
    contactModelRepository: ContactModelRepository,
    addContactRestrictionPolicy: AddContactRestrictionPolicy,
    context: Context,
    expectedPublicKey: ByteArray? = null,
) : AddOrUpdateContactBackgroundTask<ContactResult>(
    identity,
    acquaintanceLevel,
    myIdentity,
    apiConnector,
    contactModelRepository,
    addContactRestrictionPolicy,
    context,
    expectedPublicKey,
) {
    final override fun onContactAdded(result: ContactResult): ContactResult = result
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
    IGNORE,
}

/**
 * The result type of adding or updating a contact. The result is either [ContactAvailable] or
 * [Failed] or both.
 */
sealed interface ContactResult

/**
 * The contact is now available. This is the case when the contact has successfully been added or if
 * the contact already existed.
 */
sealed interface ContactAvailable : ContactResult {
    val contactModel: ContactModel
}

/**
 * Adding or updating the contact failed. Note that this does not necessarily mean that the contact
 * does not exist. E.g., this result can indicate that the provided public key does not match.
 */
sealed class Failed(context: Context, @StringRes resId: Int) : ContactResult {
    /**
     * A translated error message that can be shown to the user.
     */
    val message: String = context.getString(resId)
}

/**
 * The contact has been newly created. The new contact is provided.
 */
data class ContactCreated(override val contactModel: ContactModel) : ContactAvailable

/**
 * The contact already existed and has now been updated.
 */
data class ContactModified(
    /**
     * The updated contact model.
     */
    override val contactModel: ContactModel,
    /**
     * If true, the acquaintance level changed.
     */
    val acquaintanceLevelChanged: Boolean,
    /**
     * If true, the verification level has changed to [VerificationLevel.FULLY_VERIFIED].
     */
    val verificationLevelChanged: Boolean,
) : ContactAvailable

/**
 * The contact already exists. This is only returned, if no expected public key is given and the
 * contact already exists. This means, that neither the verification level nor the acquaintance
 * level did change.
 */
data class ContactExists(override val contactModel: ContactModel) : ContactAvailable

/**
 * The contact already exists and has been fully verified before.
 */
data class AlreadyVerified(override val contactModel: ContactModel) : ContactAvailable

/**
 * The locally stored public key of the contact does not match the provided public key.
 */
class LocalPublicKeyMismatch(
    override val contactModel: ContactModel,
    context: Context,
) : Failed(context, R.string.id_mismatch), ContactAvailable

/**
 * The contact did not exist locally and the fetched public key from the threema server does not
 * match the provided public key. This also means, that the contact is not available locally.
 */
class RemotePublicKeyMismatch(context: Context) : Failed(context, R.string.id_mismatch)

/**
 * The provided identity is invalid and the contact could not be added.
 */
class InvalidThreemaId(context: Context) : Failed(context, R.string.invalid_threema_id)

/**
 * The provided identity is the same as the user's identity and therefore the contact could not be
 * added.
 */
class UserIdentity(context: Context) : Failed(context, R.string.identity_already_exists)

/**
 * The contact could not be added due to a connection error.
 */
class ConnectionError(context: Context) : Failed(context, R.string.connection_error)

/**
 * A general error occurred while adding the contact.
 */
class GenericFailure(context: Context) : Failed(context, R.string.add_contact_failed)

/**
 * The contact could not be added since adding contacts is restricted.
 */
class PolicyViolation(context: Context) : Failed(context, R.string.disabled_by_policy_short)

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

package ch.threema.app.protocol

import androidx.annotation.WorkerThread
import ch.threema.app.services.license.LicenseService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.now
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.APIConnector.FetchIdentityResult
import ch.threema.domain.protocol.api.work.WorkContact
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.taskmanager.NetworkException
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.types.Identity

private val logger = getThreemaLogger("ValidContactsLookupSteps")

sealed class ContactOrInit(val identity: Identity)

data class Contact(val contactModel: ContactModel) : ContactOrInit(contactModel.identity)

data class SpecialContact(val cachedContact: BasicContact) : ContactOrInit(cachedContact.identity)

data class Init(val contactModelData: ContactModelData) : ContactOrInit(contactModelData.identity)

class Invalid(identity: Identity) : ContactOrInit(identity)

class UserContact(identity: Identity) : ContactOrInit(identity)

@WorkerThread
fun runValidContactsLookupSteps(
    identities: Set<Identity>,
    myIdentity: Identity,
    contactStore: ContactStore,
    contactModelRepository: ContactModelRepository,
    licenseService: LicenseService<*>,
    apiConnector: APIConnector,
): Map<Identity, ContactOrInit> {
    val contactOrInitMap = mutableMapOf<Identity, ContactOrInit>()
    val unknownIdentities = mutableSetOf<String>()

    for (identity in identities) {
        val result = try {
            checkLocally(identity, myIdentity, contactStore, contactModelRepository)
        } catch (e: InvalidIdentityException) {
            logger.error("Error while checking identity locally", e)
            continue
        }

        if (result != null) {
            contactOrInitMap[identity] = result
        } else {
            unknownIdentities.add(identity)
        }
    }

    if (unknownIdentities.isEmpty()) {
        return contactOrInitMap
    }

    val workContacts: Map<String, WorkContact> = if (ConfigUtils.isWorkBuild()) {
        val credentials = licenseService.loadCredentials()
        if (credentials !is UserCredentials) {
            logger.error("Work build without user credentials")
            emptyMap()
        } else {
            checkWorkAPI(unknownIdentities, apiConnector, credentials)
        }
    } else {
        emptyMap()
    }

    fetchIdentities(unknownIdentities, workContacts, apiConnector)
        .forEach {
            contactOrInitMap[it.identity] = it
        }

    // TODO(SE-173): Run the contact import flow for `contactOrInitMap` and update verification
    //  level for all whose associated phone number / email could be matched. Import first name and
    //  last name and set sync state to imported. Clarify precedence regarding work API.

    return contactOrInitMap
}

private fun checkLocally(
    identity: Identity,
    myIdentity: Identity,
    contactStore: ContactStore,
    contactModelRepository: ContactModelRepository,
): ContactOrInit? {
    // Skip the user's identity
    if (identity == myIdentity) {
        return UserContact(myIdentity)
    }

    // Add special contact
    if (contactStore.isSpecialContact(identity)) {
        val specialContact = contactStore.getContactForIdentityIncludingCache(identity)
        if (specialContact == null || specialContact !is BasicContact) {
            throw InvalidIdentityException("Special contact of unexpected type. Skipping identity $identity")
        }
        return SpecialContact(specialContact)
    }

    // Check if contact is known
    val contactModel = contactModelRepository.getByIdentity(identity)
    if (contactModel != null) {
        return Contact(contactModel)
    }

    // Check cache
    val cachedContact = contactStore.getContactForIdentityIncludingCache(identity)
    if (cachedContact != null) {
        if (cachedContact !is BasicContact) {
            throw InvalidIdentityException("Cached contact of unexpected type. Skipping identity $identity")
        }
        return Init(cachedContact.toContactModelData())
    }

    return null
}

@WorkerThread
private fun checkWorkAPI(
    unknownIdentities: Set<Identity>,
    apiConnector: APIConnector,
    credentials: UserCredentials,
): Map<String, WorkContact> {
    val workContacts =
        try {
            apiConnector.fetchWorkContacts(
                credentials.username,
                credentials.password,
                unknownIdentities.toTypedArray(),
            )
        } catch (e: Exception) {
            // TODO(ANDR-3262): Handle different work api server result codes
            logger.warn("Exception during work contact fetch", e)
            throw e
        }
    return workContacts.associateBy { it.threemaId }
}

@WorkerThread
private fun fetchIdentities(
    unknownIdentities: Set<Identity>,
    workContacts: Map<String, WorkContact>,
    apiConnector: APIConnector,
): Set<ContactOrInit> {
    val fetchResults = try {
        apiConnector.fetchIdentities(unknownIdentities.toList())
    } catch (e: Exception) {
        when (e) {
            is NetworkException -> throw ProtocolException(
                e.message ?: "Could not fetch identities",
            )

            is ThreemaException -> throw ProtocolException(
                e.message ?: "Could not fetch server url",
            )

            else -> throw e
        }
    }

    val fetchedContacts = fetchResults
        .map { it.toContactModelData(workContacts[it.identity]) }
        .map {
            if (it.activityState == IdentityState.INVALID) {
                Invalid(it.identity)
            } else {
                Init(it)
            }
        }
        .onEach {
            // TODO(ANDR-3262): Check whether the contact is a predefined contact and perform
            //  some additional checks for public key, nickname, first name, and last name
        }
        .toMutableSet()

    // Identities where we did not receive a response from the server are considered invalid
    (unknownIdentities - fetchedContacts.map { it.identity }.toSet()).forEach {
        fetchedContacts.add(Invalid(it))
    }

    return fetchedContacts
}

private fun BasicContact.toContactModelData() = ContactModelData(
    identity = identity,
    publicKey = publicKey,
    createdAt = now(),
    firstName = "",
    lastName = "",
    nickname = null,
    verificationLevel = VerificationLevel.UNVERIFIED,
    workVerificationLevel = WorkVerificationLevel.NONE,
    identityType = identityType,
    activityState = identityState,
    featureMask = featureMask,
    typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
    readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
    syncState = ContactSyncState.INITIAL,
    acquaintanceLevel = ch.threema.storage.models.ContactModel.AcquaintanceLevel.DIRECT,
    isArchived = false,
    localAvatarExpires = null,
    androidContactLookupInfo = null,
    profilePictureBlobId = null,
    isRestored = false,
    jobTitle = null,
    department = null,
    notificationTriggerPolicyOverride = null,
)

private fun FetchIdentityResult.toContactModelData(workContact: WorkContact?) = ContactModelData(
    identity = identity,
    publicKey = publicKey.also { fetchedPublicKey ->
        require(workContact == null || fetchedPublicKey.contentEquals(workContact.publicKey)) {
            "Public key mismatch for contact $identity"
        }
    },
    createdAt = now(),
    firstName = workContact?.firstName ?: "",
    lastName = workContact?.lastName ?: "",
    nickname = null,
    verificationLevel = workContact.getVerificationLevel(),
    workVerificationLevel = workContact.getWorkVerificationLevel(),
    identityType = getIdentityType(),
    activityState = getActivityState(),
    featureMask = featureMask.toULong(),
    typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
    readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
    syncState = ContactSyncState.INITIAL,
    acquaintanceLevel = ch.threema.storage.models.ContactModel.AcquaintanceLevel.DIRECT,
    isArchived = false,
    localAvatarExpires = null,
    androidContactLookupInfo = null,
    profilePictureBlobId = null,
    isRestored = false,
    jobTitle = workContact?.jobTitle,
    department = workContact?.department,
    notificationTriggerPolicyOverride = null,
)

private fun FetchIdentityResult.getIdentityType() = when (type) {
    0 -> IdentityType.NORMAL
    1 -> IdentityType.WORK
    else -> {
        logger.error("Got unexpected identity type {} for identity {}", type, identity)
        IdentityType.NORMAL
    }
}

private fun FetchIdentityResult.getActivityState() = when (state) {
    IdentityState.ACTIVE.value -> IdentityState.ACTIVE
    IdentityState.INACTIVE.value -> IdentityState.INACTIVE
    IdentityState.INVALID.value -> IdentityState.INVALID
    else -> {
        logger.error("Got unexpected activity state {} for identity {}", state, identity)
        IdentityState.ACTIVE
    }
}

private fun WorkContact?.getVerificationLevel() = if (this == null) {
    VerificationLevel.UNVERIFIED
} else {
    VerificationLevel.SERVER_VERIFIED
}

private fun WorkContact?.getWorkVerificationLevel() = if (this == null) {
    WorkVerificationLevel.NONE
} else {
    WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
}

private class InvalidIdentityException(msg: String) : ThreemaException(msg)

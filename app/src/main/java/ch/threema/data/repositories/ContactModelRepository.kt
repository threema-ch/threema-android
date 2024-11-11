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

package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.utils.ColorUtil
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.ContactModelDataFactory
import ch.threema.data.models.ModelDeletedException
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.ContactModel.State
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("data.ContactModelRepository")

class ContactModelRepository(
    private val cache: ModelTypeCache<String, ContactModel>, // Note: Synchronize access
    private val databaseBackend: DatabaseBackend,
) {
    private object ContactModelRepositoryToken : RepositoryToken

    init {
        // Register an "old" contact listener that updates the "new" models
        ListenerManager.contactListeners.add(object : ContactListener {
            override fun onModified(identity: String) {
                synchronized(this@ContactModelRepository) {
                    cache.get(identity)?.refreshFromDb(ContactModelRepositoryToken)
                }
            }

            override fun onAvatarChanged(contactModel: ch.threema.storage.models.ContactModel?) {
                // Ignored, avatars are not handled in contact model
            }

            override fun onRemoved(identity: String) {
                // Called when the "old model" was deleted. Propagate the deletion.
                synchronized(this@ContactModelRepository) {
                    cache.get(identity)?.let {
                        delete(it, true)
                    }
                }
            }
        })
    }

    /**
     * Create a new contact from local. This also reflects the contact if MD is active.
     *
     * @throws ContactCreateException if inserting the contact in the database failed
     */
    fun createFromLocal(
        identity: String,
        publicKey: ByteArray,
        date: Date,
        identityType: IdentityType,
        acquaintanceLevel: AcquaintanceLevel,
        activityState: State,
        featureMask: ULong,
        verificationLevel: VerificationLevel = VerificationLevel.UNVERIFIED,
    ): ContactModel = createAndReflect(
        identity,
        publicKey,
        date,
        identityType,
        acquaintanceLevel,
        activityState,
        featureMask,
        verificationLevel,
    )

    /**
     * Create a new contact from remote. This also reflects the contact if MD is active.
     *
     * @throws ContactCreateException if inserting the contact in the database failed
     */
    suspend fun createFromRemote(
        identity: String,
        publicKey: ByteArray,
        date: Date,
        identityType: IdentityType,
        acquaintanceLevel: AcquaintanceLevel,
        activityState: State,
        featureMask: ULong,
        verificationLevel: VerificationLevel = VerificationLevel.UNVERIFIED,
    ) = createAndReflect(
        identity,
        publicKey,
        date,
        identityType,
        acquaintanceLevel,
        activityState,
        featureMask,
        verificationLevel,
    )

    @Synchronized
    fun createFromSync(contactModelData: ContactModelData): ContactModel {
        databaseBackend.createContact(ContactModelDataFactory.toDbType(contactModelData))

        notifyDeprecatedListenersNew(contactModelData.identity)

        return getByIdentity(contactModelData.identity)
            ?: throw IllegalStateException("Contact must exist at this point")
    }

    /**
     * Create and reflect a new contact.
     *
     * @throws ContactCreateException if inserting the contact in the database failed
     */
    private fun createAndReflect(
        identity: String,
        publicKey: ByteArray,
        date: Date,
        identityType: IdentityType,
        acquaintanceLevel: AcquaintanceLevel,
        activityState: State,
        featureMask: ULong,
        verificationLevel: VerificationLevel
    ): ContactModel {
        val contactModelData = ContactModelData(
            identity = identity,
            publicKey = publicKey,
            createdAt = date,
            firstName = "",
            lastName = "",
            nickname = null,
            colorIndex = getIdColorIndex(identity),
            verificationLevel = verificationLevel,
            workVerificationLevel = WorkVerificationLevel.NONE,
            identityType = identityType,
            acquaintanceLevel = acquaintanceLevel,
            activityState = activityState,
            syncState = ContactSyncState.INITIAL,
            featureMask = featureMask,
            readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
            typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
            androidContactLookupKey = null,
            localAvatarExpires = null,
            isRestored = false,
            profilePictureBlobId = null,
            jobTitle = null,
            department = null
        )

        // TODO(ANDR-3002) and TODO(ANDR-3003): Reflect contact sync create

        val contactModel = synchronized(this) {
            try {
                databaseBackend.createContact(ContactModelDataFactory.toDbType(contactModelData))
            } catch (exception: SQLiteException) {
                // Note that in case the insertion fails, this is most likely because the identity
                // already exists.
                throw ContactCreateException(exception)
            }

            getByIdentity(identity)
                ?: throw IllegalStateException("Contact must exist at this point")
        }

        notifyDeprecatedListenersNew(identity)

        return contactModel
    }

    /**
     * Return the contact model for the specified identity.
     */
    @Synchronized
    fun getByIdentity(identity: String): ContactModel? {
        return cache.getOrCreate(identity) {
            val dbContact =
                databaseBackend.getContactByIdentity(identity) ?: return@getOrCreate null
            ContactModel(identity, ContactModelDataFactory.toDataType(dbContact), databaseBackend)
        }
    }

    /**
     * Remove the specified contact from the database and cache.
     */
    @Synchronized
    fun deleteByIdentity(identity: String) {
        // Look up model. If found, delete it.
        getByIdentity(identity)?.let { this.delete(it, false) }

        // TODO(ANDR-2835): Test that deletion works as intended, by opening the contact details,
        // and then deleting the contact via multi-device protocol.
    }

    /**
     * Remove the specified contact model from the database and cache.
     */
    @Synchronized
    fun delete(contact: ContactModel) {
        this.delete(contact, false)
    }

    /**
     * Remove the specified contact model from the cache and possibly from the database.
     *
     * @param indirect This parameter should be set to `false` when called directly by app code,
     *     and `true` when called as an effect of a `onRemoved` listener.
     */
    @Synchronized
    private fun delete(model: ContactModel, indirect: Boolean) {
        // Remove from cache
        cache.remove(model.identity)

        // Delete data from database and deactivate model
        try {
            model.delete(ContactModelRepositoryToken, !indirect)
        } catch (e: ModelDeletedException) {
            if (!indirect) {
                throw e
            } else {
                logger.warn("Model for ${model.identity} is already marked as deleted")
            }
        }
    }

    private fun notifyDeprecatedListenersNew(identity: String) {
        ListenerManager.contactListeners.handle { it.onNew(identity) }
    }

    /**
     * Compute the sha 256 hash of this identity and set the color index accordingly.
     */
    private fun getIdColorIndex(identity: String): UByte = try {
        val firstByte = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))[0]
        ColorUtil.getInstance().getIDColorIndex(firstByte).toUByte()
    } catch (e: NoSuchAlgorithmException) {
        throw IllegalStateException("Could not find hashing algorithm for id color", e)
    }
}

/**
 * This exception is thrown if the contact could not be added. A corrupt database or
 */
class ContactCreateException(e: SQLiteException) :
    ThreemaException("Failed to create the contact", e)

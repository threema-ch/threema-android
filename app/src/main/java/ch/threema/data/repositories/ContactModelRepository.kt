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

package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ListenerManager
import ch.threema.app.tasks.ReflectContactSyncCreateTask
import ch.threema.base.ThreemaException
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.ContactModelDataFactory
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbContact
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel.AcquaintanceLevel

private val logger = LoggingUtil.getThreemaLogger("data.ContactModelRepository")

class ContactModelRepository(
    // Note: Synchronize access
    private val cache: ModelTypeCache<String, ContactModel>,
    private val databaseBackend: DatabaseBackend,
    private val coreServiceManager: CoreServiceManager,
) {
    private object ContactModelRepositoryToken : RepositoryToken

    init {
        // Register an "old" contact listener that updates the "new" models
        ListenerManager.contactListeners.add(
            object : ContactListener {
                override fun onModified(identity: Identity) {
                    synchronized(this@ContactModelRepository) {
                        cache.get(identity)?.refreshFromDb(ContactModelRepositoryToken)
                    }
                }
            },
        )
    }

    /**
     * Create a new contact from local. This also reflects the contact if MD is active.
     *
     * @throws ContactReflectException if reflecting the contact failed
     * @throws ContactStoreException if inserting the contact in the database failed
     */
    suspend fun createFromLocal(contactModelData: ContactModelData): ContactModel {
        requireValidContact(contactModelData)

        val createContactLocally: () -> ContactModel = {
            createContactLocally(contactModelData)
        }

        return if (coreServiceManager.multiDeviceManager.isMultiDeviceActive) {
            try {
                coreServiceManager.taskManager.schedule(
                    ReflectContactSyncCreateTask(
                        contactModelData,
                        this,
                        coreServiceManager.nonceFactory,
                        createContactLocally,
                        coreServiceManager.multiDeviceManager,
                    ),
                ).await()
            } catch (e: TransactionScope.TransactionException) {
                logger.error("Could not reflect the contact")
                throw ContactReflectException(e)
            }
        } else {
            createContactLocally()
        }
    }

    /**
     * Create a new contact from remote. This also reflects the contact if MD is active.
     *
     * @throws ContactReflectException if reflecting the contact failed
     * @throws ContactStoreException if inserting the contact in the database failed
     */
    suspend fun createFromRemote(
        contactModelData: ContactModelData,
        handle: ActiveTaskCodec,
    ): ContactModel {
        requireValidContact(contactModelData)

        val createContactLocally: () -> ContactModel = {
            createContactLocally(contactModelData)
        }

        return if (coreServiceManager.multiDeviceManager.isMultiDeviceActive) {
            try {
                ReflectContactSyncCreateTask(
                    contactModelData,
                    this,
                    coreServiceManager.nonceFactory,
                    createContactLocally,
                    coreServiceManager.multiDeviceManager,
                ).invoke(handle)
            } catch (e: TransactionScope.TransactionException) {
                logger.error("Could not reflect the contact", e)
                throw ContactReflectException(e)
            }
        } else {
            createContactLocally()
        }
    }

    /**
     * Create a new group contact. Note that this does *not* reflect the changes.
     *
     * @throws ContactStoreException if inserting the contact in the database failed
     * @throws UnexpectedContactException if the provided contact has [AcquaintanceLevel.DIRECT]
     */
    fun persistGroupContactFromRemote(contactModelData: ContactModelData) {
        if (contactModelData.acquaintanceLevel == AcquaintanceLevel.DIRECT) {
            throw UnexpectedContactException("A contact with acquaintance level group was expected")
        }

        createContactLocally(contactModelData)
    }

    /**
     * Create a new contact from sync.
     *
     * @throws ContactStoreException if the contact could not be stored in the database
     */
    @Synchronized
    fun createFromSync(contactModelData: ContactModelData): ContactModel {
        try {
            databaseBackend.createContact(ContactModelDataFactory.toDbType(contactModelData))
        } catch (exception: SQLiteException) {
            throw ContactStoreException(exception)
        } catch (exception: IllegalArgumentException) {
            throw InvalidContactException(exception = exception)
        }

        notifyDeprecatedListenersOnNew(contactModelData.identity)

        return getByIdentity(contactModelData.identity)
            ?: throw IllegalStateException("Contact must exist at this point")
    }

    /**
     * Creates the contact with the given data locally. After adding the contact, the listeners are
     * fired.
     *
     * @throws ContactStoreException if inserting the contact in the database fails
     */
    private fun createContactLocally(contactModelData: ContactModelData): ContactModel {
        val contactModel = synchronized(this) {
            try {
                databaseBackend.createContact(ContactModelDataFactory.toDbType(contactModelData))
            } catch (exception: SQLiteException) {
                // Note that in case the insertion fails, this is most likely because the identity
                // already exists.
                throw ContactStoreException(exception)
            } catch (exception: IllegalArgumentException) {
                // In this case the identity or public key of the contact is invalid.
                throw InvalidContactException(exception = exception)
            }

            getByIdentity(contactModelData.identity)
                ?: throw IllegalStateException("Contact must exist at this point")
        }

        notifyDeprecatedListenersOnNew(contactModelData.identity)

        return contactModel
    }

    @Throws(InvalidContactException::class)
    private fun requireValidContact(contactModelData: ContactModelData) {
        if (contactModelData.identity.length != ProtocolDefines.IDENTITY_LEN) {
            throw InvalidContactException("Invalid identity: ${contactModelData.identity}")
        }
        if (contactModelData.publicKey.size != NaCl.PUBLIC_KEY_BYTES) {
            throw InvalidContactException("Invalid public key size (${contactModelData.publicKey.size}) for identity ${contactModelData.identity}")
        }
    }

    /**
     * Return the contact model for the specified identity.
     */
    @Synchronized
    fun getByIdentity(identity: Identity): ContactModel? = cache.getOrCreate(identity) {
        databaseBackend.getContactByIdentity(identity)?.toModel()
    }

    /**
     * Returns all contact models either from database or cached.
     */
    @Synchronized
    fun getAll(): List<ContactModel> = databaseBackend.getAllContacts().mapNotNull { dbContact ->
        cache.getOrCreate(dbContact.identity) {
            dbContact.toModel()
        }
    }

    @Synchronized
    fun existsByIdentity(identity: Identity): Boolean =
        (cache.get(identity) ?: databaseBackend.getContactByIdentity(identity)) != null

    private fun notifyDeprecatedListenersOnNew(identity: Identity) {
        ListenerManager.contactListeners.handle { it.onNew(identity) }
    }

    private fun DbContact.toModel(): ContactModel = ContactModel(
        identity = this.identity,
        data = ContactModelDataFactory.toDataType(this),
        databaseBackend = databaseBackend,
        contactModelRepository = this@ContactModelRepository,
        coreServiceManager = coreServiceManager,
    )
}

/**
 * This exception is thrown if the contact could not be added. This is either due to a failure
 * reflecting ([ContactReflectException]), storing ([ContactStoreException]), or validating
 * ([UnexpectedContactException]) the contact.
 */
sealed class ContactCreateException(msg: String, e: Exception? = null) : ThreemaException(msg, e)

/**
 * This exception is thrown if the contact could not be added because reflecting it failed.
 */
class ContactReflectException(e: TransactionScope.TransactionException) :
    ContactCreateException("Failed to reflect the contact", e)

/**
 * This exception is thrown if the contact could not be added. A corrupt database or a contact with
 * the same identity already exists.
 */
class ContactStoreException(e: SQLiteException) :
    ContactCreateException("Failed to store the contact", e)

/**
 * This exception is thrown if the contact could not be added due to an invalid identity or public key.
 */
class InvalidContactException(message: String = "Invalid contact", exception: Exception? = null) : ContactCreateException(message, exception)

/**
 * This exception is thrown if an unexpected contact should have been added.
 */
class UnexpectedContactException(msg: String) : ContactCreateException(msg)

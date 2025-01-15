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
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ListenerManager
import ch.threema.app.tasks.ReflectContactSyncCreateTask
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.ContactModelDataFactory
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TransactionScope

private val logger = LoggingUtil.getThreemaLogger("data.ContactModelRepository")

class ContactModelRepository(
    private val cache: ModelTypeCache<String, ContactModel>, // Note: Synchronize access
    private val databaseBackend: DatabaseBackend,
    private val coreServiceManager: CoreServiceManager,
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
        })
    }

    /**
     * Create a new contact from local. This also reflects the contact if MD is active.
     *
     * @throws ContactReflectException if reflecting the contact failed
     * @throws ContactStoreException if inserting the contact in the database failed
     */
    suspend fun createFromLocal(contactModelData: ContactModelData): ContactModel {
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
                    )
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
     * Create a new contact from sync.
     *
     * @throws ContactStoreException if the contact could not be stored in the database
     */
    @Synchronized
    fun createFromSync(contactModelData: ContactModelData): ContactModel {
        try {
            databaseBackend.createContact(ContactModelDataFactory.toDbType(contactModelData))
        } catch (e: SQLiteException) {
            throw ContactStoreException(e)
        }

        notifyDeprecatedListenersNew(contactModelData.identity)

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
            }

            getByIdentity(contactModelData.identity)
                ?: throw IllegalStateException("Contact must exist at this point")
        }

        notifyDeprecatedListenersNew(contactModelData.identity)

        return contactModel
    }

    /**
     * Return the contact model for the specified identity.
     */
    @Synchronized
    fun getByIdentity(identity: String): ContactModel? {
        return cache.getOrCreate(identity) {
            val dbContact = databaseBackend.getContactByIdentity(identity) ?: return@getOrCreate null
            ContactModel(
                identity = identity,
                data = ContactModelDataFactory.toDataType(dbContact),
                databaseBackend = databaseBackend,
                contactModelRepository = this,
                coreServiceManager = coreServiceManager
            )
        }
    }

    @Synchronized
    fun existsByIdentity(identity: String): Boolean =
        (cache.get(identity) ?: databaseBackend.getContactByIdentity(identity)) != null

    private fun notifyDeprecatedListenersNew(identity: String) {
        ListenerManager.contactListeners.handle { it.onNew(identity) }
    }
}

/**
 * This exception is thrown if the contact could not be added. This is either due to a failure
 * reflecting ([ContactReflectException]) or storing ([ContactStoreException]) the contact.
 */
sealed class ContactCreateException(msg: String, e: Exception) : ThreemaException(msg, e)

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
    ContactCreateException("Failed to create the contact", e)

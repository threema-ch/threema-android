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
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactModelRepository
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("AddContactBackgroundTask")

/**
 * This task simply adds the given contact model data if the contact does not exist. If there is a
 * contact with the same identity already, adding the new data is aborted.
 */
class AddContactBackgroundTask(
    private val contactModelData: ContactModelData,
    private val contactModelRepository: ContactModelRepository,
) : BackgroundTask<ContactModel?> {

    /**
     * Add the contact model data if the contact does not exist.
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

    /**
     * Do not call this method directly. Use [runSynchronously] or run this task using a
     * [BackgroundExecutor].
     */
    override fun runInBackground(): ContactModel? {
        if (contactModelRepository.getByIdentity(contactModelData.identity) != null) {
            logger.warn("Contact already exists")
            return null
        }

        return try {
            runBlocking {
                contactModelRepository.createFromLocal(contactModelData)
            }
        } catch (e: ContactCreateException) {
            logger.error("Contact could not be created", e)
            null
        }
    }
}

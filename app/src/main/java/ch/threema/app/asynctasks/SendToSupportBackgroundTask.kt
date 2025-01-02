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
import ch.threema.app.preference.SettingsAdvancedOptionsFragment.THREEMA_SUPPORT_IDENTITY
import ch.threema.app.services.ContactServiceImpl
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.storage.models.ContactModel.AcquaintanceLevel

/**
 * The result of sending some messages to the support.
 */
enum class SendToSupportResult {
    SUCCESS,
    FAILED,
}

/**
 * This class can be used to send messages to the support. It creates the support contact if not
 * already available.
 */
abstract class SendToSupportBackgroundTask(
    myIdentity: String,
    apiConnector: APIConnector,
    contactModelRepository: ContactModelRepository,
    context: Context,
) : AddOrUpdateContactBackgroundTask<SendToSupportResult>(
    THREEMA_SUPPORT_IDENTITY,
    AcquaintanceLevel.DIRECT,
    myIdentity,
    apiConnector,
    contactModelRepository,
    AddContactRestrictionPolicy.IGNORE,
    context,
    ContactServiceImpl.SUPPORT_PUBLIC_KEY,
) {
    final override fun onContactAdded(result: ContactResult): SendToSupportResult {
        return when (result) {
            is ContactAvailable -> onSupportAvailable(result.contactModel)
            else -> SendToSupportResult.FAILED
        }
    }

    /**
     * This method is called when the support contact is available. It is run on a background
     * thread.
     */
    abstract fun onSupportAvailable(contactModel: ContactModel): SendToSupportResult
}

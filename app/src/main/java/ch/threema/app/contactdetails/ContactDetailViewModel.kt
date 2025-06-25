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

package ch.threema.app.contactdetails

import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.threema.app.AppConstants
import ch.threema.app.ThreemaApplication
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData

class ContactDetailViewModel(val contactModel: ContactModel) : ViewModel() {

    @JvmField
    val contactModelData: LiveData<ContactModelData?> = contactModel.liveData()

    /**
     * Update the contact's first and last name.
     */
    fun updateContactName(firstName: String, lastName: String) {
        contactModel.setNameFromLocal(firstName, lastName)
    }

    /**
     * Whether or not to show the floating edit action button.
     */
    fun showEditFAB(): Boolean {
        // Don't show the edit button for contacts linked to an Android contact
        return contactModelData.value?.isLinkedToAndroidContact() ?: false
    }

    companion object {
        /**
         * View model must be initialized with the identity.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val modelRepositories = ThreemaApplication.getServiceManager()!!.modelRepositories
                val bundle = this[DEFAULT_ARGS_KEY]
                    ?: throw IllegalArgumentException("Bundle not passed to ContactDetailViewModel factory")
                val identity = bundle.getString(AppConstants.INTENT_DATA_CONTACT)
                    ?: throw IllegalArgumentException("Identity not passed to ContactDetailViewModel factory")
                val contactModel = modelRepositories.contacts.getByIdentity(identity)
                    ?: throw IllegalArgumentException("ContactDetailViewModel: Contact with identity $identity not found")
                ContactDetailViewModel(contactModel)
            }
        }
    }
}

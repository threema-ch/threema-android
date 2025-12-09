/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.fragments

import android.annotation.SuppressLint
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.AddContactActivity
import ch.threema.app.adapters.UserListAdapter
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DispatcherProvider
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class UserListFragment : RecipientListFragment() {
    private val contactService: ContactService by inject()
    private val blockedIdentitiesService: BlockedIdentitiesService by inject()
    private val conversationCategoryService: ConversationCategoryService by inject()
    private val preferenceService: PreferenceService by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    override fun isMultiSelectAllowed(): Boolean = multiSelect || multiSelectIdentity

    override fun getBundleName(): String = "UserListState"

    override fun getEmptyText(): Int = R.string.no_matching_contacts

    override fun getAddIcon(): Int = R.drawable.ic_person_add_outline

    override fun getAddText(): Int = R.string.menu_add_contact

    override fun getAddIntent(): Intent = buildActivityIntent<AddContactActivity>(requireActivity()) {
        putExtra(AddContactActivity.EXTRA_ADD_BY_ID, true)
    }

    @SuppressLint("StaticFieldLeak")
    override fun createListAdapter(checkedItemPositions: ArrayList<Int>?) {
        lifecycleScope.launch {
            val contactModels = withContext(dispatcherProvider.worker) {
                if (ConfigUtils.isWorkBuild()) {
                    // Excluding invalid and work contacts
                    contactService
                        .getAllDisplayed(ContactService.ContactSelection.EXCLUDE_INVALID)
                        .filterNot { contactModel -> contactModel.isWorkVerified }
                } else {
                    // Excluding invalid contacts because they cannot receive messages anyways
                    contactService.getAllDisplayed(ContactService.ContactSelection.EXCLUDE_INVALID)
                }
            }

            adapter = UserListAdapter(
                activity,
                contactModels,
                null,
                checkedItemPositions,
                contactService,
                blockedIdentitiesService,
                conversationCategoryService,
                preferenceService,
                this@UserListFragment,
                Glide.with(ThreemaApplication.getAppContext()),
                false,
            )
            setListAdapter(adapter)
            if (listInstanceState != null) {
                if (isAdded && view != null && getActivity() != null) {
                    listView.onRestoreInstanceState(listInstanceState)
                }
                listInstanceState = null
                restoreCheckedItems(checkedItemPositions)
            }
        }
    }
}

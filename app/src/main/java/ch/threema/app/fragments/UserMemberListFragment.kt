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
import androidx.lifecycle.lifecycleScope
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.adapters.UserListAdapter
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DispatcherProvider
import ch.threema.domain.models.IdentityState
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import com.bumptech.glide.Glide
import kotlin.getValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class UserMemberListFragment : MemberListFragment() {

    private val contactService: ContactService by inject()
    private val conversationCategoryService: ConversationCategoryService by inject()
    private val blockedIdentitiesService: BlockedIdentitiesService by inject()
    private val preferenceService: PreferenceService by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    override fun getBundleName(): String = "UserMemberListState"

    override fun getEmptyText(): Int = R.string.no_matching_contacts

    @SuppressLint("StaticFieldLeak")
    override fun createListAdapter(
        checkedItemPositions: ArrayList<Int>?,
        preselectedIdentities: ArrayList<Identity>?,
        excludedIdentities: ArrayList<Identity>?,
        groups: Boolean,
        profilePics: Boolean,
    ) {
        lifecycleScope.launch {
            val contactModels = withContext(dispatcherProvider.worker) {
                var contactModels: List<ContactModel> = if (groups) {
                    contactService.find(
                        object : ContactService.Filter {
                            override fun states() =
                                if (preferenceService.showInactiveContacts()) {
                                    arrayOf(IdentityState.ACTIVE, IdentityState.INACTIVE)
                                } else {
                                    arrayOf(IdentityState.ACTIVE)
                                }

                            override fun requiredFeature() = ThreemaFeature.GROUP_CHAT

                            override fun fetchMissingFeatureLevel() = true

                            override fun includeMyself() = false

                            override fun includeHidden() = false
                        },
                    )
                } else if (profilePics) {
                    contactService.canReceiveProfilePics
                } else {
                    // Don't include invalid contacts because they should not be added to groups
                    contactService.getAllDisplayed(ContactService.ContactSelection.EXCLUDE_INVALID)
                }

                if (ConfigUtils.isWorkBuild()) {
                    contactModels = contactModels.filterNot { contactModel ->
                        contactModel.isWorkVerified
                    }
                }

                if (excludedIdentities != null) {
                    contactModels = contactModels.filterNot { contactModel ->
                        excludedIdentities.contains(contactModel.identity)
                    }
                }
                contactModels
            }

            adapter = UserListAdapter(
                activity,
                contactModels,
                preselectedIdentities,
                checkedItemPositions,
                contactService,
                blockedIdentitiesService,
                conversationCategoryService,
                preferenceService,
                this@UserMemberListFragment,
                Glide.with(ThreemaApplication.getAppContext()),
                false,
            )
            setListAdapter(adapter)
            if (listInstanceState != null) {
                if (isAdded && view != null && getActivity() != null) {
                    listView.onRestoreInstanceState(listInstanceState)
                }
                listInstanceState = null
            }
            onAdapterCreated()
        }
    }
}

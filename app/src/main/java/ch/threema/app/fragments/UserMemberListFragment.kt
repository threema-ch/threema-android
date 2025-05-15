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
import android.os.AsyncTask
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.adapters.UserListAdapter
import ch.threema.app.services.ContactService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.IdentityState
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.storage.models.ContactModel
import com.bumptech.glide.Glide
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("UserMemberListFragment")

class UserMemberListFragment : MemberListFragment() {
    override fun getBundleName(): String = "UserMemberListState"

    override fun getEmptyText(): Int = R.string.no_matching_contacts

    @SuppressLint("StaticFieldLeak")
    override fun createListAdapter(
        checkedItemPositions: ArrayList<Int>?,
        preselectedIdentities: ArrayList<String>?,
        excludedIdentities: ArrayList<String>?,
        groups: Boolean,
        profilePics: Boolean,
    ) {
        @Suppress("DEPRECATION")
        object : AsyncTask<Void?, Void?, List<ContactModel>>() {
            @Deprecated("Deprecated in Java")
            override fun doInBackground(vararg params: Void?): List<ContactModel> {
                var contactModels: List<ContactModel> = if (groups) {
                    contactService.find(
                        object : ContactService.Filter {
                            override fun states(): Array<IdentityState> =
                                if (preferenceService.showInactiveContacts()) {
                                    arrayOf(IdentityState.ACTIVE, IdentityState.INACTIVE)
                                } else {
                                    arrayOf(IdentityState.ACTIVE)
                                }

                            // required!
                            override fun requiredFeature(): Long = ThreemaFeature.GROUP_CHAT

                            override fun fetchMissingFeatureLevel(): Boolean = true

                            override fun includeMyself(): Boolean = false

                            override fun includeHidden(): Boolean = false

                            override fun onlyWithReceiptSettings(): Boolean = false
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
                return contactModels
            }

            @Deprecated("Deprecated in Java")
            override fun onPostExecute(contactModels: List<ContactModel>) {
                if (conversationCategoryService == null) {
                    logger.error("Conversation category service is null")
                    return
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
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }
}

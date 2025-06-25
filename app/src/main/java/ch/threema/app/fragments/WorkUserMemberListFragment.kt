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
import android.widget.AbsListView
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.adapters.UserListAdapter
import ch.threema.app.services.ContactService
import ch.threema.app.utils.ContactUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.IdentityState
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.storage.models.ContactModel
import com.bumptech.glide.Glide

private val logger = LoggingUtil.getThreemaLogger("WorkUserMemberListFragment")

class WorkUserMemberListFragment : MemberListFragment() {
    override fun getBundleName(): String = "WorkerUserMemberListState"

    override fun getEmptyText(): Int = R.string.no_matching_work_contacts

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
                return contactService.find(
                    object : ContactService.Filter {
                        override fun states(): Array<IdentityState> =
                            if (preferenceService.showInactiveContacts()) {
                                arrayOf(IdentityState.ACTIVE, IdentityState.INACTIVE)
                            } else {
                                arrayOf(IdentityState.ACTIVE)
                            }

                        override fun requiredFeature(): Long? = if (groups) ThreemaFeature.GROUP_CHAT else null

                        override fun fetchMissingFeatureLevel(): Boolean = groups

                        override fun includeMyself(): Boolean = false

                        override fun includeHidden(): Boolean = false

                        override fun onlyWithReceiptSettings(): Boolean = false
                    },
                ).filter { contactModel: ContactModel ->
                    contactModel.isWorkVerified &&
                        (!profilePics || !ContactUtil.isEchoEchoOrGatewayContact(contactModel)) &&
                        (excludedIdentities == null || !excludedIdentities.contains(contactModel.identity))
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onPostExecute(contactModels: List<ContactModel>) {
                if (!isAdded) {
                    return
                } else if (conversationCategoryService == null) {
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
                    this@WorkUserMemberListFragment,
                    Glide.with(ThreemaApplication.getAppContext()),
                    true,
                )
                setListAdapter(adapter)
                listView.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
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

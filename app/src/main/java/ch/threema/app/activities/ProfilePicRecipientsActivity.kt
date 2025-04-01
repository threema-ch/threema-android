/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.activities

import android.os.Bundle
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.IdListService
import ch.threema.app.tasks.ReflectUserProfileShareWithAllowListSyncTask
import ch.threema.app.utils.LogUtil
import ch.threema.app.utils.equalsIgnoreOrder
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.models.ContactModel

class ProfilePicRecipientsActivity : MemberChooseActivity() {

    private lateinit var profilePicRecipientsService: IdListService
    private lateinit var taskManager: TaskManager

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        try {
            this.profilePicRecipientsService = serviceManager.profilePicRecipientsService
            this.taskManager = serviceManager.taskManager
        } catch (exception: Exception) {
            LogUtil.exception(exception, this)
            return false
        }

        initData(savedInstanceState)

        return true
    }

    override fun initData(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            val selectedIdentities: Array<String>? = profilePicRecipientsService.all
            if (!selectedIdentities.isNullOrEmpty()) {
                preselectedIdentities = ArrayList(listOf(*selectedIdentities))
            }
        }
        updateToolbarTitle(R.string.profile_picture, R.string.title_choose_recipient)
        initList()
    }

    override fun menuNext(selectedContacts: List<ContactModel?>) {
        val oldAllowedIdentities: Array<String> = profilePicRecipientsService.all
        val newAllowedIdentities: Array<String> =
            selectedContacts.mapNotNull { contactModel -> contactModel?.identity }
                .toTypedArray<String>()
        profilePicRecipientsService.replaceAll(newAllowedIdentities)

        // If data changed:
        // sync new policy setting with newly set allow list values into device group (if md is active)
        if (!oldAllowedIdentities.equalsIgnoreOrder(newAllowedIdentities)) {
            taskManager.schedule(
                ReflectUserProfileShareWithAllowListSyncTask(
                    allowedIdentities = newAllowedIdentities.toList(),
                    serviceManager = ThreemaApplication.requireServiceManager()
                )
            )
        }
        finish()
    }

    override fun getNotice(): Int = R.string.prefs_sum_receive_profilepics_recipients_list

    override fun getMode(): Int = MODE_PROFILE_PIC_RECIPIENTS

    override fun enableOnBackPressedCallback(): Boolean = true

    override fun handleOnBackPressed() {
        this.menuNext(selectedContacts)
    }
}

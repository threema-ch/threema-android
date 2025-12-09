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

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.tasks.ReflectUserProfileShareWithAllowListSyncTask
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.equalsIgnoreOrder
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ProfilePicRecipientsActivity")

class ProfilePicRecipientsActivity : MemberChooseActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val profilePictureRecipientsService: ProfilePictureRecipientsService by inject()
    private val taskManager: TaskManager by inject()

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        initData(savedInstanceState)

        return true
    }

    override fun initData(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            val selectedIdentities: Array<Identity>? = profilePictureRecipientsService.all
            if (!selectedIdentities.isNullOrEmpty()) {
                preselectedIdentities = ArrayList(listOf(*selectedIdentities))
            }
        }
        updateToolbarSubtitle(R.string.title_choose_recipient)
        initList()
    }

    override fun menuNext(selectedContacts: List<ContactModel?>) {
        val oldAllowedIdentities: Array<Identity> = profilePictureRecipientsService.all
        val newAllowedIdentities: Array<Identity> = selectedContacts
            .mapNotNull { contactModel -> contactModel?.identity }
            .toTypedArray<String>()
        profilePictureRecipientsService.replaceAll(newAllowedIdentities)

        // If data changed:
        // sync new policy setting with newly set allow list values into device group (if md is active)
        if (!oldAllowedIdentities.equalsIgnoreOrder(newAllowedIdentities)) {
            taskManager.schedule(
                ReflectUserProfileShareWithAllowListSyncTask(
                    allowedIdentities = newAllowedIdentities.toSet(),
                    serviceManager = ThreemaApplication.requireServiceManager(),
                ),
            )
        }
        finish()
    }

    @StringRes
    override fun getNotice(): Int = R.string.prefs_sum_receive_profilepics_recipients_list

    override fun getMode(): Int = MODE_PROFILE_PIC_RECIPIENTS

    override fun enableOnBackPressedCallback(): Boolean = true

    override fun handleOnBackPressed() {
        this.menuNext(selectedContacts)
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<ProfilePicRecipientsActivity>(context)
    }
}

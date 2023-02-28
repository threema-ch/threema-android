/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.utils

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.routines.UpdateFeatureLevelRoutine
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = LoggingUtil.getThreemaLogger("GroupCallUtil")

/**
 * Initiate a group call. If necessary, fetch the feature mask of the specified contact.
 *
 * @param activity The activity that triggered this call.
 * @param groupModel The group to call
 * @return true if the call could be initiated, false otherwise
 */
fun initiateCall(
        activity: AppCompatActivity,
        groupModel: GroupModel
) {
    val serviceManager = ThreemaApplication.getServiceManager() ?: return
    val userService: UserService
    val groupService: GroupService
    val contactService: ContactService
    val apiConnector: APIConnector
    try {
        userService = serviceManager.userService
        groupService = serviceManager.groupService
        contactService = serviceManager.contactService
        apiConnector = serviceManager.apiConnector
    } catch (e: Exception) {
        logger.error("Services not available", e)
        return
    }

    if (!ConfigUtils.isGroupCallsEnabled()) {
        // we should never get here
        logger.debug("Attempting to initiate group call despite being disabled")
        return
    }

    // Check for internet connection
    if (!serviceManager.deviceService.isOnline) {
        SimpleStringAlertDialog.newInstance(R.string.internet_connection_required, R.string.connection_error).show(activity.supportFragmentManager, "err")
        return
    }

    val otherMemberIdentities = groupService.getGroupIdentities(groupModel).filter { !userService.isMe(it) }
    val otherMembers = contactService.getByIdentities(otherMemberIdentities)

    // Disallow group calls in empty groups
    if (otherMembers.isEmpty()) {
        SimpleStringAlertDialog.newInstance(R.string.group_calls, R.string.group_no_members).show(activity.supportFragmentManager, "err")
        return
    }

    // Refresh members that support group calls if some have been known to not support group calls.
    //
    // Note: This will disregard the edge case where all members downgraded.
    if (otherMembers.any { !ThreemaFeature.canGroupCalls(it.featureMask) }) {
        activity.lifecycleScope.launch {
            val dialogTagFetchingFeatureMask = "fetchMask"
            GenericProgressDialog.newInstance(R.string.please_wait, R.string.checking_compatibility)
                .show(activity.supportFragmentManager, dialogTagFetchingFeatureMask)

            withContext(Dispatchers.Default) {
                otherMembers.forEach { UpdateFeatureLevelRoutine.removeTimeCache(it) }
                UpdateFeatureLevelRoutine(contactService, apiConnector, otherMembers).run()
            }

            DialogUtil.dismissDialog(activity.supportFragmentManager, dialogTagFetchingFeatureMask, true)

            launchGroupCallWithSupportedMembers(activity, groupModel, otherMembers)
        }
    } else {
        launchGroupCallWithSupportedMembers(activity, groupModel, otherMembers)
    }
}

private fun launchGroupCallWithSupportedMembers(activity: AppCompatActivity, groupModel: GroupModel, otherMembers: List<ContactModel>) {
    val otherMembersNotSupportingGroupCallsCount = otherMembers.count { !ThreemaFeature.canGroupCalls(it.featureMask) }

    // Disallow group calls in case no other member supports group calls
    //
    // TODO(ANDR-1896): Discuss whether the UX benefit outweighs the technical impact
    if (otherMembersNotSupportingGroupCallsCount == otherMembers.size) {
        SimpleStringAlertDialog.newInstance(R.string.group_call, R.string.no_members_support_group_calls).show(activity.supportFragmentManager, "err")
    } else {
        launchActivity(activity, groupModel, otherMembersNotSupportingGroupCallsCount)
    }
}

private fun launchActivity(context: Context, groupModel: GroupModel, otherMembersNotSupportingGroupCallsCount: Int) {
    if (otherMembersNotSupportingGroupCallsCount > 0) {
        Toast.makeText(
            context,
            ConfigUtils.getSafeQuantityString(
                context,
                R.plurals.n_members_dont_support_group_calls,
                otherMembersNotSupportingGroupCallsCount,
                otherMembersNotSupportingGroupCallsCount
            ),
            Toast.LENGTH_LONG
        ).show()
    }
    ContextCompat.startActivity(context, GroupCallActivity.getStartCallIntent(context, groupModel.id), null)
}

fun qualifiesForGroupCalls(groupService: GroupService, groupModel: GroupModel): Boolean {
    return ConfigUtils.isGroupCallsEnabled() && groupService.countMembers(groupModel) > 1
}

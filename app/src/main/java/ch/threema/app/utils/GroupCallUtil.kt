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

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
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
import ch.threema.app.voip.groupcall.GroupCallDescription
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
 * Get the time since the group call is running. If the time on the phone is potentially wrong as it
 * is not synchronized or the given context is null, the time since the group call start message has
 * been processed is displayed.
 *
 * If the device time is synchronized and the context is not null, we assume that the time is
 * correct and return [GroupCallDescription.getRunningSince]. Otherwise we assume a wrong device
 * time and return [GroupCallDescription.getRunningSinceProcessed].
 *
 * The running time is relative to [SystemClock.elapsedRealtime].
 *
 * @param call    the group call description
 * @param context the context
 * @return the time in milliseconds since the group call has been started or processed
 */
fun getRunningSince(call: GroupCallDescription, context: Context?): Long {
    val isAutoTime = context != null && Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
    return if (isAutoTime) {
        call.getRunningSince() ?: call.getRunningSinceProcessed()
    } else {
        call.getRunningSinceProcessed()
    }
}

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
    if (context is Activity) {
        context.overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_close_exit)
    }
}

fun qualifiesForGroupCalls(groupService: GroupService, groupModel: GroupModel): Boolean =
    ConfigUtils.isGroupCallsEnabled()                    // group calls are enabled
            && groupService.countMembers(groupModel) > 1 // there is more than one member
            && groupService.isGroupMember(groupModel)    // the user is a member of the group

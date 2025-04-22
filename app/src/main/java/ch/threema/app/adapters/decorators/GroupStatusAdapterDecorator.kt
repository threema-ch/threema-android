/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.adapters.decorators

import android.content.Context
import ch.threema.app.R
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.ui.listitemholder.ComposeMessageHolder
import ch.threema.app.utils.TestUtil
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.data.status.GroupStatusDataModel
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.CREATED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.FIRST_VOTE
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.GROUP_DESCRIPTION_CHANGED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.IS_NOTES_GROUP
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.IS_PEOPLE_GROUP
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.MEMBER_ADDED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.MEMBER_KICKED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.MEMBER_LEFT
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.MODIFIED_VOTE
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.ORPHANED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.PROFILE_PICTURE_UPDATED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.RECEIVED_VOTE
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.RENAMED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.VOTES_COMPLETE

class GroupStatusAdapterDecorator(
    context: Context,
    messageModel: AbstractMessageModel,
    helper: Helper?,
) : ChatAdapterDecorator(context, messageModel, helper) {
    override fun configureChatMessage(holder: ComposeMessageHolder, position: Int) {
        val statusDataModel = messageModel.groupStatusData ?: return
        val statusText = getStatusText(statusDataModel, userService, contactService, context)
        if (showHide(holder.bodyTextView, !TestUtil.isEmptyOrNull(statusText))) {
            holder.bodyTextView.text = statusText
        }
        setOnClickListener({
            // no action on onClick
        }, holder.messageBlockView)
    }

    companion object {
        /**
         * Get the display name of the identity contained in the group status data model.
         */
        private fun getDisplayName(
            statusDataModel: GroupStatusDataModel,
            userService: UserService?,
            contactService: ContactService?,
            context: Context,
        ): String {
            val identity = statusDataModel.identity ?: return ""
            // Get the me representation directly from strings to get it in the current language
            if (userService?.isMe(identity) == true) return context.getString(R.string.me_myself_and_i)
            if (contactService == null) return identity
            val contactModel = contactService.getByIdentity(identity) ?: return identity
            val contactMessageReceiver =
                contactService.createReceiver(contactModel) ?: return identity
            return contactMessageReceiver.displayName ?: identity
        }

        fun getStatusText(
            statusDataModel: GroupStatusDataModel,
            userService: UserService?,
            contactService: ContactService?,
            context: Context,
        ): String {
            val displayName = getDisplayName(statusDataModel, userService, contactService, context)
            val ballotName = statusDataModel.ballotName ?: ""
            val newGroupName = statusDataModel.newGroupName ?: ""
            return when (statusDataModel.statusType) {
                CREATED -> context.getString(R.string.status_create_group)
                RENAMED -> context.getString(R.string.status_rename_group, newGroupName)
                PROFILE_PICTURE_UPDATED -> context.getString(R.string.status_group_new_photo)
                MEMBER_ADDED -> context.getString(R.string.status_group_new_member, displayName)
                MEMBER_LEFT -> context.getString(R.string.status_group_member_left, displayName)
                MEMBER_KICKED -> context.getString(R.string.status_group_member_kicked, displayName)
                IS_NOTES_GROUP -> context.getString(R.string.status_create_notes)
                IS_PEOPLE_GROUP -> context.getString(R.string.status_create_notes_off)
                FIRST_VOTE -> context.getString(
                    R.string.status_ballot_user_first_vote,
                    displayName,
                    ballotName,
                )

                MODIFIED_VOTE -> context.getString(
                    R.string.status_ballot_user_modified_vote,
                    displayName,
                    ballotName,
                )

                RECEIVED_VOTE -> context.getString(
                    R.string.status_ballot_voting_changed,
                    ballotName,
                )

                VOTES_COMPLETE -> context.getString(R.string.status_ballot_all_votes, ballotName)
                GROUP_DESCRIPTION_CHANGED -> "" // TODO(ANDR-2386)
                ORPHANED -> context.getString(R.string.status_orphaned_group)
            }
        }
    }
}

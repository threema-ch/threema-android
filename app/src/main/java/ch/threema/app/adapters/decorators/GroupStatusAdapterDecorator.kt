package ch.threema.app.adapters.decorators

import android.content.Context
import ch.threema.app.R
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.ui.listitemholder.ComposeMessageHolder
import ch.threema.app.utils.LinkifyUtil
import ch.threema.data.datatypes.ContactNameFormat
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
    messageModel: AbstractMessageModel,
    chatAdapterDecoratorListener: ChatAdapterDecoratorListener,
    linkifyListener: LinkifyUtil.LinkifyListener,
    helper: Helper?,
) : ChatAdapterDecorator(messageModel, chatAdapterDecoratorListener, linkifyListener, helper) {
    override fun configureChatMessage(holder: ComposeMessageHolder, context: Context, position: Int) {
        val statusDataModel = messageModel.groupStatusData ?: return
        val statusText = getStatusText(
            statusDataModel = statusDataModel,
            userService = userService,
            contactService = contactService,
            contactNameFormat = helper.preferenceService.getContactNameFormat(),
            context = context,
        )
        if (showHide(holder.bodyTextView, statusText.isNotEmpty())) {
            holder.bodyTextView.text = statusText
        }
        setOnClickListener(
            {
                // no action on onClick
            },
            holder.messageBlockView,
        )
    }

    companion object {
        /**
         * Get the display name of the identity contained in the group status data model.
         */
        private fun getDisplayName(
            statusDataModel: GroupStatusDataModel,
            userService: UserService?,
            contactService: ContactService?,
            contactNameFormat: ContactNameFormat,
            context: Context,
        ): String {
            val identity = statusDataModel.identity ?: return ""
            // Get the me representation directly from strings to get it in the current language
            if (userService?.isMe(identity) == true) return context.getString(R.string.me_myself_and_i)
            if (contactService == null) return identity
            val contactModel = contactService.getByIdentity(identity) ?: return identity
            val contactMessageReceiver = contactService.createReceiver(contactModel)
            return contactMessageReceiver.getDisplayName(contactNameFormat)
        }

        @JvmStatic
        fun getStatusText(
            statusDataModel: GroupStatusDataModel,
            userService: UserService?,
            contactService: ContactService?,
            contactNameFormat: ContactNameFormat,
            context: Context,
        ): String {
            val displayName = getDisplayName(statusDataModel, userService, contactService, contactNameFormat, context)
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

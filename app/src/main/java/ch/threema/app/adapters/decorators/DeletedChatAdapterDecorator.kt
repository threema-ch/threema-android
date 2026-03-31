package ch.threema.app.adapters.decorators

import android.content.Context
import ch.threema.app.ui.listitemholder.ComposeMessageHolder
import ch.threema.app.utils.LinkifyUtil
import ch.threema.app.utils.MessageUtil
import ch.threema.storage.models.AbstractMessageModel

class DeletedChatAdapterDecorator(
    messageModel: AbstractMessageModel,
    chatAdapterDecoratorListener: ChatAdapterDecoratorListener,
    linkifyListener: LinkifyUtil.LinkifyListener,
    helper: Helper,
) : ChatAdapterDecorator(messageModel, chatAdapterDecoratorListener, linkifyListener, helper) {
    override fun configureChatMessage(holder: ComposeMessageHolder, context: Context, position: Int) {
        holder.dateView.text = MessageUtil.getDisplayDate(
            /* context = */
            context,
            /* postedAt = */
            messageModel.postedAt,
            /* isOutbox = */
            messageModel.isOutbox,
            /* modifiedAt = */
            messageModel.modifiedAt,
            /* full = */
            true,
        )
        setOnClickListener({}, holder.messageBlockView)
    }
}

package ch.threema.app.adapters.decorators

import android.content.Context
import ch.threema.app.ui.listitemholder.ComposeMessageHolder
import ch.threema.app.utils.MessageUtil
import ch.threema.storage.models.AbstractMessageModel

class DeletedChatAdapterDecorator(
    context: Context,
    messageModel: AbstractMessageModel,
    helper: Helper,
) : ChatAdapterDecorator(context, messageModel, helper) {
    override fun configureChatMessage(holder: ComposeMessageHolder, position: Int) {
        holder.dateView.text = MessageUtil.getDisplayDate(context, messageModel, true)
        setOnClickListener({
        }, holder.messageBlockView)
    }
}

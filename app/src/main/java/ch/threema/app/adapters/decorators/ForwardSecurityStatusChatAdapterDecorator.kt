package ch.threema.app.adapters.decorators

import android.content.Context
import ch.threema.app.R
import ch.threema.app.ui.listitemholder.ComposeMessageHolder
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.LinkifyUtil
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel.ForwardSecurityStatusType

class ForwardSecurityStatusChatAdapterDecorator(
    messageModel: AbstractMessageModel?,
    chatAdapterDecoratorListener: ChatAdapterDecoratorListener,
    linkifyListener: LinkifyUtil.LinkifyListener,
    helper: Helper?,
) : ChatAdapterDecorator(messageModel, chatAdapterDecoratorListener, linkifyListener, helper) {
    override fun configureChatMessage(holder: ComposeMessageHolder, context: Context, position: Int) {
        val statusDataModel = messageModel.forwardSecurityStatusData ?: return
        val body: String? = when (statusDataModel.status) {
            ForwardSecurityStatusType.STATIC_TEXT -> statusDataModel.staticText
            ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY ->
                context.getString(R.string.message_without_forward_security)

            ForwardSecurityStatusType.FORWARD_SECURITY_RESET ->
                context.getString(R.string.forward_security_reset)

            ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED ->
                context.getString(R.string.forward_security_established)

            ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED_RX ->
                context.getString(R.string.forward_security_established_rx)

            ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER ->
                context.getString(R.string.forward_security_message_out_of_order)

            ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED ->
                ConfigUtils.getSafeQuantityString(
                    context,
                    R.plurals.forward_security_messages_skipped,
                    statusDataModel.quantity,
                    statusDataModel.quantity,
                )

            ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE ->
                context.getString(R.string.forward_security_downgraded_status_message)

            ForwardSecurityStatusType.FORWARD_SECURITY_ILLEGAL_SESSION_STATE ->
                context.getString(R.string.forward_security_illegal_session_status_message)

            // TODO(ANDR-2519): Can this be removed when md supports fs? Maybe not, because theses statuses won't be rendered correctly if they have already been created
            ForwardSecurityStatusType.FORWARD_SECURITY_DISABLED ->
                context.getString(R.string.forward_security_disabled)

            else -> null
        }
        if (showHide(holder.bodyTextView, !body.isNullOrEmpty())) {
            holder.bodyTextView.text = body
        }
        setOnClickListener({
            // no action on onClick
        }, holder.messageBlockView)
    }
}

package ch.threema.app.adapters.decorators;

import android.content.Context;

import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.MessageUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class GroupCallStatusDataChatAdapterDecorator extends ChatAdapterDecorator {

    public GroupCallStatusDataChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
        super(context, messageModel, helper);
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
        if (holder.bodyTextView != null) {
            MessageUtil.MessageViewElement viewElement = MessageUtil.getViewElement(this.getContext(), this.getMessageModel());
            if (viewElement.text != null) {
                holder.bodyTextView.setText(viewElement.text);
            }
        }
        this.setOnClickListener(view -> {
        }, holder.messageBlockView);
    }
}

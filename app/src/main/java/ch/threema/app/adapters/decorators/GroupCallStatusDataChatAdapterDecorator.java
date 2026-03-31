package ch.threema.app.adapters.decorators;

import android.content.Context;

import androidx.annotation.NonNull;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.ui.models.MessageViewElement;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class GroupCallStatusDataChatAdapterDecorator extends ChatAdapterDecorator {

    public GroupCallStatusDataChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        Helper helper
    ) {
        super(messageModel, chatAdapterDecoratorListener, linkifyListener, helper);
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, Context context, final int position) {
        if (holder.bodyTextView != null) {
            final @NonNull MessageViewElement viewElement = MessageUtil.getViewElement(
                context,
                this.getMessageModel(),
                this.helper.getPreferenceService().getContactNameFormat()
            );
            if (viewElement.text != null) {
                holder.bodyTextView.setText(viewElement.text);
            }
        }
        this.setOnClickListener(view -> {
        }, holder.messageBlockView);
    }
}

package ch.threema.app.adapters.decorators;

import android.content.Context;

import androidx.annotation.NonNull;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class StatusChatAdapterDecorator extends ChatAdapterDecorator {
    public StatusChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        Helper helper
    ) {
        super(messageModel, chatAdapterDecoratorListener, linkifyListener, helper);
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, Context context, final int position) {
        String s = this.getMessageModel().getBody();

        if (this.showHide(holder.bodyTextView, !TestUtil.isEmptyOrNull(s))) {
            holder.bodyTextView.setText(s);
        }

        this.setOnClickListener(view -> {
            // no action on onClick
        }, holder.messageBlockView);
    }
}

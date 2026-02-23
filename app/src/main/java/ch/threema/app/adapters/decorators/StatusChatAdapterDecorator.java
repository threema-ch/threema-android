package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.view.View;

import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class StatusChatAdapterDecorator extends ChatAdapterDecorator {
    public StatusChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
        super(context, messageModel, helper);
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
        String s = this.getMessageModel().getBody();

        if (this.showHide(holder.bodyTextView, !TestUtil.isEmptyOrNull(s))) {
            holder.bodyTextView.setText(s);
        }

        this.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // no action on onClick
            }
        }, holder.messageBlockView);
    }
}

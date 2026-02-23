package ch.threema.app.adapters.decorators;

import android.content.Context;

import ch.threema.app.R;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class FirstUnreadChatAdapterDecorator extends ChatAdapterDecorator {
    private int unreadMessagesCount;

    public FirstUnreadChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper, final int unreadMessagesCount) {
        super(context, messageModel, helper);

        this.unreadMessagesCount = unreadMessagesCount;
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
        String s = ConfigUtils.getSafeQuantityString(getContext(), R.plurals.unread_messages, unreadMessagesCount, unreadMessagesCount);

        if (this.showHide(holder.bodyTextView, !TestUtil.isEmptyOrNull(s))) {
            holder.bodyTextView.setText(s);
        }
    }
}

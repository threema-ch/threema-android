package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.content.res.ColorStateList;

import java.util.Date;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class DateSeparatorChatAdapterDecorator extends ChatAdapterDecorator {
    public DateSeparatorChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
        super(context, messageModel, helper);
    }

    @Override
    protected void applyContentColor(
        final @NonNull ComposeMessageHolder viewHolder,
        final @NonNull ColorStateList contentColor
    ) {
        super.applyContentColor(viewHolder, contentColor);
        viewHolder.bodyTextView.setTextColor(
            ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnSurface)
        );
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
        Date date = this.getMessageModel().getCreatedAt();

        if (this.showHide(holder.bodyTextView, true)) {
            if (date != null) {
                holder.bodyTextView.setText(LocaleUtil.formatDateRelative(date.getTime()));
            }
        }
    }
}

package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.content.res.ColorStateList;

import java.util.Date;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class DateSeparatorChatAdapterDecorator extends ChatAdapterDecorator {
    public DateSeparatorChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        Helper helper
    ) {
        super(messageModel, chatAdapterDecoratorListener, linkifyListener, helper);
    }

    @Override
    protected void applyContentColor(
        final @NonNull ComposeMessageHolder viewHolder,
        final @NonNull ColorStateList contentColor
    ) {
        super.applyContentColor(viewHolder, contentColor);
        viewHolder.bodyTextView.setTextColor(
            ConfigUtils.getColorFromAttribute(viewHolder.bodyTextView.getContext(), R.attr.colorOnSurface)
        );
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, Context context, final int position) {
        Date date = this.getMessageModel().getCreatedAt();

        if (this.showHide(holder.bodyTextView, true)) {
            if (date != null) {
                holder.bodyTextView.setText(LocaleUtil.formatDateRelative(date.getTime()));
            }
        }
    }
}

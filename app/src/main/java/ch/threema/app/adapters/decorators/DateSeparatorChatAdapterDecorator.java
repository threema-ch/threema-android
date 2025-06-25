/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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

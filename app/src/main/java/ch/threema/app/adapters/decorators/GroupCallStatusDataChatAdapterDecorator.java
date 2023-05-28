/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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
		this.setOnClickListener(view -> {}, holder.messageBlockView);
	}
}

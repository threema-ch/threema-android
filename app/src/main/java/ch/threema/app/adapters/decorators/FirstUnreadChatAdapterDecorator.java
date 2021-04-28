/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import ch.threema.app.R;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class FirstUnreadChatAdapterDecorator extends ChatAdapterDecorator {
	private int unreadMessagesCount = 0;

	public FirstUnreadChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper, final int unreadMessagesCount) {
		super(context, messageModel, helper);

		this.unreadMessagesCount = unreadMessagesCount;
	}

	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		if (this.unreadMessagesCount < 1) {
			return;
		}

		String s;
		if (this.unreadMessagesCount > 1) {
			s = getContext().getString(R.string.unread_messages, unreadMessagesCount);
		} else {
			s = getContext().getString(R.string.one_unread_message);
		}

		if(this.showHide(holder.bodyTextView, !TestUtil.empty(s))) {
			holder.bodyTextView.setText(s);
		}
	}
}

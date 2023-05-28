/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2023 Threema GmbH
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

package ch.threema.app.utils;

import android.graphics.Paint;
import android.widget.TextView;

import ch.threema.app.services.GroupService;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.GroupModel;

public class AdapterUtil {

	/**
	 * Style a TextView by means of the state
	 * @param view
	 * @param contactModel
	 */
	public static void styleContact(TextView view, ContactModel contactModel)
	{
		if(view != null) {
			int paintFlags = view.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG);

			float alpha = 1f;
			if (contactModel != null) {
				switch (contactModel.getState()) {
					case INACTIVE:
						alpha = 0.4f;
						break;

					case INVALID:
						paintFlags = paintFlags | Paint.STRIKE_THRU_TEXT_FLAG;
						break;
				}
			}
			view.setAlpha(alpha);
			view.setPaintFlags(paintFlags);
		}
	}

	public static void styleGroup(TextView view, GroupService groupService, GroupModel groupModel)
	{
		if(view != null) {
			int paintFlags = view.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG);

			if (groupModel != null && !groupService.isGroupMember(groupModel)) {
				paintFlags = paintFlags | Paint.STRIKE_THRU_TEXT_FLAG;
			}
			view.setAlpha(1f);
			view.setPaintFlags(paintFlags);
		}
	}

	public static void styleConversation(TextView view, GroupService groupService, ConversationModel conversationModel) {
		if (conversationModel.isContactConversation()) {
			styleContact(view, conversationModel.getContact());
		} else {
			styleGroup(view, groupService, conversationModel.getGroup());
		}
	}
}

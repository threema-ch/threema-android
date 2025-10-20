/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.GroupService;
import ch.threema.data.models.GroupModelData;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.GroupModel;

import static ch.threema.app.compose.conversation.models.ConversationNameStyleKt.INACTIVE_CONTACT_ALPHA;

public class AdapterUtil {

    /**
     * Style a TextView by means of the state
     *
     * @param view
     * @param contactModel
     */
    public static void styleContact(TextView view, ContactModel contactModel) {
        if (view != null) {
            int paintFlags = view.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG);

            float alpha = 1f;
            if (contactModel != null) {
                switch (contactModel.getState()) {
                    case INACTIVE:
                        alpha = INACTIVE_CONTACT_ALPHA;
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

    public static void styleGroup(
        @Nullable TextView textView,
        @NonNull GroupService groupService,
        @Nullable GroupModel groupModel
    ) {
        if (textView != null) {
            if (groupModel != null && !groupService.isGroupMember(groupModel)) {
                styleStrikethrough(textView);
            } else {
                styleNormal(textView);
            }
        }
    }

    public static void styleGroup(@Nullable TextView textView, @Nullable ch.threema.data.models.GroupModel groupModel) {
        if (textView == null) {
            return;
        }

        boolean isMemberOrUnknownGroup = true;
        if (groupModel != null) {
            GroupModelData groupModelData = groupModel.getData();
            if (groupModelData != null) {
                isMemberOrUnknownGroup = groupModelData.isMember();
            }
        }
        // If the group is null or deleted, do not strike through the text view
        if (isMemberOrUnknownGroup) {
            styleNormal(textView);
        } else {
            styleStrikethrough(textView);
        }
    }

    public static void styleConversation(TextView view, ConversationModel conversationModel) {
        if (conversationModel.isContactConversation()) {
            styleContact(view, conversationModel.getContact());
        } else {
            styleGroup(view, conversationModel.getGroupModel());
        }
    }

    private static void styleNormal(@NonNull TextView textView) {
        int paintFlags = textView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG);
        textView.setAlpha(1f);
        textView.setPaintFlags(paintFlags);
    }

    private static void styleStrikethrough(@NonNull TextView textView) {
        int paintFlags = textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG;
        textView.setAlpha(1f);
        textView.setPaintFlags(paintFlags);
    }
}

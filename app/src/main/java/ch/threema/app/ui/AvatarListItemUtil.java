/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.ui;

import android.view.View;
import android.widget.ImageView;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.services.AvatarService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ReceiverModel;

public class AvatarListItemUtil {

	public static void loadAvatar(
		final ConversationModel conversationModel,
		final ContactService contactService,
		final GroupService groupService,
		final DistributionListService distributionListService,
		AvatarListItemHolder holder) {

		// load avatars asynchronously
		ImageView avatarView = holder.avatarView.getAvatarView();
		if (conversationModel.isContactConversation()) {
			holder.avatarView.setContentDescription(
				ThreemaApplication.getAppContext().getString(R.string.edit_type_content_description,
					ThreemaApplication.getAppContext().getString(R.string.mime_contact),
					NameUtil.getDisplayNameOrNickname(conversationModel.getContact(), true)));
			contactService.loadAvatarIntoImage(conversationModel.getContact(), avatarView, AvatarOptions.PRESET_RESPECT_SETTINGS);
		} else if (conversationModel.isGroupConversation()) {
			holder.avatarView.setContentDescription(
				ThreemaApplication.getAppContext().getString(R.string.edit_type_content_description,
					ThreemaApplication.getAppContext().getString(R.string.group),
					NameUtil.getDisplayName(conversationModel.getGroup(), groupService)));
			groupService.loadAvatarIntoImage(conversationModel.getGroup(), avatarView, AvatarOptions.PRESET_DEFAULT_FALLBACK);
		} else if (conversationModel.isDistributionListConversation()) {
			holder.avatarView.setContentDescription(
				ThreemaApplication.getAppContext().getString(R.string.edit_type_content_description,
					ThreemaApplication.getAppContext().getString(R.string.distribution_list),
					NameUtil.getDisplayName(conversationModel.getDistributionList(), distributionListService)));
			distributionListService.loadAvatarIntoImage(conversationModel.getDistributionList(), avatarView, AvatarOptions.PRESET_DEFAULT_AVATAR_NO_CACHE);
		}

		// Set work badge
		boolean isWork = contactService.showBadge(conversationModel.getContact());
		holder.avatarView.setBadgeVisible(isWork);
	}

	public static <M extends ReceiverModel> void loadAvatar(
		final M model,
		final AvatarService<M> avatarService,
		AvatarListItemHolder holder
	) {

		//do nothing
		if (!TestUtil.required(model, avatarService, holder) || holder.avatarView == null) {
			return;
		}

		if (model instanceof ContactModel) {
			holder.avatarView.setBadgeVisible(((ContactService) avatarService).showBadge((ContactModel) model));
		} else {
			holder.avatarView.setBadgeVisible(false);
		}

		AvatarOptions options;
		if (model instanceof ContactModel) {
			options = AvatarOptions.PRESET_RESPECT_SETTINGS;
		} else if (model instanceof GroupModel) {
			options = AvatarOptions.PRESET_DEFAULT_FALLBACK;
		} else {
			options = AvatarOptions.PRESET_DEFAULT_AVATAR_NO_CACHE;
		}

		avatarService.loadAvatarIntoImage(model, holder.avatarView.getAvatarView(), options);

		holder.avatarView.setVisibility(View.VISIBLE);
	}

}

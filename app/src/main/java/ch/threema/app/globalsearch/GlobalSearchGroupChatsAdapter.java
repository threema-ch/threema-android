/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.app.globalsearch;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;

public class GlobalSearchGroupChatsAdapter extends GlobalSearchAdapter {
	private static final Logger logger = LoggerFactory.getLogger(GlobalSearchGroupChatsAdapter.class);

	private GroupService groupService;
	private ContactService contactService;

	GlobalSearchGroupChatsAdapter(Context context, String headerString) {
		super(context, headerString);
		try {
			this.groupService = ThreemaApplication.getServiceManager().getGroupService();
			this.contactService = ThreemaApplication.getServiceManager().getContactService();
		} catch (Exception e) {
			logger.error("Unable to get GroupService", e);
		}
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		if (holder instanceof GlobalSearchGroupChatsAdapter.ItemHolder) {
			GlobalSearchGroupChatsAdapter.ItemHolder itemHolder = (GlobalSearchGroupChatsAdapter.ItemHolder) holder;

			if (messageModels != null) {
				GroupMessageModel current = (GroupMessageModel) getItem(position);

				final ContactModel contactModel = current.isOutbox() ? this.contactService.getMe() : this.contactService.getByIdentity(current.getIdentity());
				final GroupModel groupModel = groupService.getById(current.getGroupId());
				// load avatars asynchronously
				AvatarListItemUtil.loadAvatar(position, groupModel, null, groupService, itemHolder.avatarListItemHolder);

				String groupName = NameUtil.getDisplayName(groupModel, groupService);
				itemHolder.titleView.setText(
					String.format("%s %s %s", NameUtil.getDisplayNameOrNickname(contactModel, true), FLOW_CHARACTER, groupName)
				);
				itemHolder.dateView.setText(LocaleUtil.formatDateRelative(context, current.getCreatedAt().getTime()));

				setSnippetToTextView(current, itemHolder);

				if (this.onClickItemListener != null) {
					itemHolder.itemView.setOnClickListener(v -> onClickItemListener.onClick(current, itemHolder.itemView));
				}
			} else {
				// Covers the case of data not being ready yet.
				itemHolder.titleView.setText("No data");
				itemHolder.dateView.setText("");
				itemHolder.snippetView.setText("");
			}
		} else {
			GlobalSearchGroupChatsAdapter.HeaderHolder headerHolder = (GlobalSearchGroupChatsAdapter.HeaderHolder) holder;

			headerHolder.headerText.setText(headerString);
		}
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.services;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTagModel;
import ch.threema.storage.models.TagModel;

public class ConversationTagServiceImpl implements ConversationTagService {
	// Do not change this tag before db entries not changed
	public static final String FIXED_TAG_PIN = "star";
	public static final String FIXED_TAG_UNREAD = "unread"; // chats deliberately marked as unread

	private final DatabaseServiceNew databaseService;

	private final List<TagModel> tagModels = new ArrayList<>();

	public ConversationTagServiceImpl(Context context, DatabaseServiceNew databaseService) {
		this.databaseService = databaseService;

		// Initalize Tag Models
		this.tagModels.add(new TagModel(
			FIXED_TAG_PIN,
			1,
			2,
			context.getString(R.string.pin)
		));

		// Initalize Tag Models
		this.tagModels.add(new TagModel(
			FIXED_TAG_UNREAD,
			0xFFFF0000,
			0xFFFFFFFF,
			context.getString(R.string.unread_messages)
		));
	}

	@Override
	public List<TagModel> getTagModels() {
		return this.tagModels;
	}

	@Override
	public TagModel getTagModel(final String tagKey) {
		return Functional.select(this.tagModels, new IPredicateNonNull<TagModel>() {
			@Override
			public boolean apply(@NonNull TagModel tagModel) {
				return TestUtil.compare(tagModel.getTag(), tagKey);
			}
		});
	}

	@Override
	public List<ConversationTagModel> getTagsForConversation(final ConversationModel conversation) {
		return this.databaseService.getConversationTagFactory()
			.getByConversationUid(conversation.getUid());
	}

	@Override
	public boolean tag(ConversationModel conversation, TagModel tagModel) {
		if (conversation != null && tagModel != null) {
			if (!this.isTaggedWith(conversation, tagModel)) {
				this.databaseService.getConversationTagFactory()
					.create(new ConversationTagModel(conversation.getUid(), tagModel.getTag()));
				this.triggerChange(conversation);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean unTag(ConversationModel conversation, TagModel tagModel) {
		if (conversation != null && tagModel != null) {
			if (this.isTaggedWith(conversation, tagModel)) {
				this.databaseService.getConversationTagFactory()
					.deleteByConversationUidAndTag(conversation.getUid(), tagModel.getTag());
				this.triggerChange(conversation);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean toggle(ConversationModel conversation, TagModel tagModel, boolean silent) {
		if (conversation != null && tagModel != null) {
			if (this.isTaggedWith(conversation, tagModel)) {
				// remove
				this.databaseService.getConversationTagFactory()
					.deleteByConversationUidAndTag(conversation.getUid(), tagModel.getTag());
				if (!silent) {
					this.triggerChange(conversation);
				}
			} else {
				// Add
				this.databaseService.getConversationTagFactory()
					.create(new ConversationTagModel(conversation.getUid(), tagModel.getTag()));
				if (!silent) {
					this.triggerChange(conversation);
				}
			}
		}
		return false;
	}

	@Override
	public boolean isTaggedWith(ConversationModel conversation, TagModel tagModel) {
		if (conversation == null || tagModel == null) {
			return false;
		}

		return this.databaseService.getConversationTagFactory()
			.getByConversationUidAndTag(conversation.getUid(), tagModel.getTag()) != null;
	}

	@Override
	public void removeAll(ConversationModel conversation) {
		if (conversation != null) {
			this.databaseService.getConversationTagFactory()
				.deleteByConversationUid(conversation.getUid());
		}
	}

	@Override
	public void removeAll(TagModel tagModel) {
		if (tagModel != null) {
			this.databaseService.getConversationTagFactory()
				.deleteByConversationTag(tagModel.getTag());
		}
	}

	@Override
	public List<ConversationTagModel> getAll() {
		return this.databaseService.getConversationTagFactory().getAll();
	}

	@Override
	public long getCount(@NonNull TagModel tagModel) {
		return this.databaseService.getConversationTagFactory().countByTag(tagModel.getTag());
	}

	private void triggerChange(final ConversationModel conversationModel) {
		ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
			@Override
			public void handle(ConversationListener listener) {
				listener.onModified(conversationModel, conversationModel.getPosition());
			}
		});
	}
}

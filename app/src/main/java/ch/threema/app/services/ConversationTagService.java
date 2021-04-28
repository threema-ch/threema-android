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

import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTagModel;
import ch.threema.storage.models.TagModel;

public interface ConversationTagService {
	/**
	 * Return all available {@link TagModel}
	 */
	List<TagModel> getTagModels();

	/**
	 * Select a {@link TagModel} by the key
	 */
	TagModel getTagModel(String tagKey);

	/**
	 * Return all tags for the specified  {@link ConversationModel}.
	 */
	List<ConversationTagModel> getTagsForConversation(final ConversationModel conversation);

	/**
	 * Tag the {@link ConversationModel} with the given {@link TagModel}
	 */
	boolean tag(ConversationModel conversation, TagModel tagModel);

	/**
	 * Untag the {@link ConversationModel} with the given {@link TagModel}
	 */
	boolean unTag(ConversationModel conversation, TagModel tagModel);

	/**
	 * Toggle the {@link TagModel} of the {@link ConversationModel}
	 */
	boolean toggle(ConversationModel ConversationModel, TagModel tagModel, boolean silent);

	/**
	 * Return true, if the {@link ConversationModel} is tagged with {@link TagModel}
	 */
	boolean isTaggedWith(ConversationModel ConversationModel, TagModel tagModel);

	/**
	 * Remove all tags linked with the given {@link ConversationModel}
	 */
	void removeAll(ConversationModel conversation);

	/**
	 * Remove all tags linked with the given {@link TagModel}
	 */
	void removeAll(TagModel tagModel);

	/**
	 * Get all tags regardless of type
	 */
	List<ConversationTagModel> getAll();

	/**
	 * Return the number of conversations with the provided tag
	 * @param tagModel tag
	 * @return number of conversations or 0 if there is none
	 */
	long getCount(@NonNull TagModel tagModel);
}


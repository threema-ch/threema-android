/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTagModel;
import ch.threema.storage.models.TagModel;

public interface ConversationTagService {

	/**
	 * Select a {@link TagModel} by the key
	 */
	@Nullable TagModel getTagModel(@NonNull String tagKey);

	/**
	 * Tag the {@link ConversationModel} with the given {@link TagModel}
	 */
	void addTagAndNotify(@Nullable ConversationModel conversation, @Nullable TagModel tagModel);

	/**
	 * Untag the {@link ConversationModel} with the given {@link TagModel}
	 */
	void removeTagAndNotify(@Nullable ConversationModel conversation, @Nullable TagModel tagModel);

	/**
	 * Remove the given tag of the conversation with the provided conversation uid.
	 */
	void removeTag(@NonNull String conversationUid, @NonNull TagModel tagModel);

	/**
	 * Toggle the {@link TagModel} of the {@link ConversationModel}
	 */
	boolean toggle(@Nullable ConversationModel ConversationModel, @Nullable TagModel tagModel, boolean silent);

	/**
	 * Return true, if the {@link ConversationModel} is tagged with {@link TagModel}
	 */
	boolean isTaggedWith(@Nullable ConversationModel ConversationModel, @Nullable TagModel tagModel);

	/**
	 * Remove all tags linked with the given {@link ConversationModel}
	 */
	void removeAll(@Nullable ConversationModel conversation);

	/**
	 * Remove all tags linked with the given conversation uid
	 */
	void removeAll(@NonNull String conversationUid);

	/**
	 * Get all tags regardless of type
	 */
	List<ConversationTagModel> getAll();

	/**
	 * Get all conversation uids that are tagged with the provided type.
	 */
	@NonNull
	List<String> getConversationUidsByTag(@NonNull TagModel tagModel);

	/**
	 * Return the number of conversations with the provided tag
	 * @param tagModel tag
	 * @return number of conversations or 0 if there is none
	 */
	long getCount(@NonNull TagModel tagModel);
}


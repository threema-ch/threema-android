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

package ch.threema.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.storage.models.GroupModel;

/**
 * A service for sending messages to groups.
 */
public interface GroupMessagingService {
	/**
	 * Callback which creates and returns an {@link AbstractGroupMessage}
	 * with the specified {@link MessageId}.
	 */
	interface CreateApiMessage {
		AbstractGroupMessage create(MessageId messageId);
	}

	/**
	 * Callback which is invoked once a group message is queued.
	 */
	interface GroupMessageQueued {
		void onQueued(@NonNull AbstractGroupMessage queuedGroupMessage);
	}

	/**
	 * Send the group message returned by {@param createApiMessage}
	 * to the specified {@param group}.
	 *
	 * @return the number of messages enqueued
	 */
	int sendMessage(
		@NonNull GroupModel group,
		@NonNull String[] identities,
		@NonNull CreateApiMessage createApiMessage
	) throws ThreemaException;

	/**
	 * Send the group message returned by {@param createApiMessage}
	 * to the specified {@param group}.
	 *
	 * @return the number of messages enqueued
	 */
	int sendMessage(
		@NonNull GroupModel group,
		@NonNull String[] identities,
		@NonNull CreateApiMessage createApiMessage,
		@Nullable GroupMessageQueued groupMessageQueued
	) throws ThreemaException;

	/**
	 * Send the group message returned by {@param createApiMessage}
	 * to the group identified by {@param groupId} and {@param groupCreatorId}.
	 *
	 * @return the number of messages enqueued
	 */
	int sendMessage(
		@NonNull GroupId groupId,
		@NonNull String groupCreatorId,
		@NonNull String[] identities,
		@NonNull CreateApiMessage createApiMessage,
	    @Nullable GroupMessageQueued queued
	) throws ThreemaException;
}

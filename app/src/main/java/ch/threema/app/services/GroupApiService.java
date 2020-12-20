/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

import ch.threema.base.ThreemaException;
import ch.threema.client.AbstractGroupMessage;
import ch.threema.client.GroupId;
import ch.threema.client.MessageId;
import ch.threema.storage.models.GroupModel;

public interface GroupApiService {
	interface CreateApiMessage {
		AbstractGroupMessage create(MessageId messageId);
	}

	interface GroupMessageQueued {
		void onQueued(AbstractGroupMessage queuedGroupMessage);
	};

	/**
	 * Message Ids in the order of the identities
	 */
	int sendMessage(GroupModel group, String[] identities, CreateApiMessage createApiMessage) throws ThreemaException;

	/**
	 * Message Ids in the order of the identities
	 */
	int sendMessage(GroupModel group, String[] identities, CreateApiMessage createApiMessage, GroupMessageQueued groupMessageQueued) throws ThreemaException;

	/**
	 * Message Ids in the order of the identities
	 */
	int sendMessage(GroupId groupId, String groupCreatorId, String[] identities, CreateApiMessage createApiMessage) throws ThreemaException;
	/**
	 * Message Ids in the order of the identities
	 */
	int sendMessage(final GroupId groupId,
	                                          final String groupCreatorId,
	                                          String[] identities,
	                                          CreateApiMessage createApiMessage,
	                                          GroupMessageQueued queued) throws ThreemaException;
}

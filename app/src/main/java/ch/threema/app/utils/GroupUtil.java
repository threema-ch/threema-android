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

package ch.threema.app.utils;


import androidx.annotation.NonNull;
import ch.threema.storage.models.GroupModel;

public class GroupUtil {
	private static String CENTRALLY_MANAGED_GATEWAY_GROUP_PREFIX = "☁";

	/**
	 * Return true, if the group is created by a normal threema user
	 * or by a gateway id and marked with a special prefix (cloud emoji {@link CENTRALLY_MANAGED_GATEWAY_GROUP_PREFIX}) as "centrally managed group"
	 *
	 * @see <a href="https://broadcast.threema.ch/en/faq#central-groups">What are centrally managed group chats?</a>
	 */
	public static boolean sendMessageToCreator(@NonNull GroupModel groupModel) {
		return sendMessageToCreator(groupModel.getCreatorIdentity(), groupModel.getName());
	}

	public static boolean sendMessageToCreator(String groupCreator, String groupName ) {
		return
			!ContactUtil.isChannelContact(groupCreator)
				|| (groupName != null && groupName.startsWith(CENTRALLY_MANAGED_GATEWAY_GROUP_PREFIX));
	}

}

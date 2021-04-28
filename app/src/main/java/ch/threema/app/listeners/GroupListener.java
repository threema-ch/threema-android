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

package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.storage.models.GroupModel;

public interface GroupListener {
	@AnyThread default void onCreate(GroupModel newGroupModel) { }
	@AnyThread default void onRename(GroupModel groupModel) { }
	@AnyThread default void onUpdatePhoto(GroupModel groupModel) { }
	@AnyThread default void onRemove(GroupModel groupModel) { }

	@AnyThread default void onNewMember(GroupModel group, String newIdentity, int previousMemberCount) { }
	@AnyThread default void onMemberLeave(GroupModel group, String identity, int previousMemberCount) { }
	@AnyThread default void onMemberKicked(GroupModel group, String identity, int previousMemberCount) { }

	/**
	 * Group was updated.
	 */
	@AnyThread default void onUpdate(GroupModel groupModel) { }

	/**
	 * User left his own group.
	 */
	@AnyThread default void onLeave(GroupModel groupModel) { }
}

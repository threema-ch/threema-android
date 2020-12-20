/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

import android.content.pm.ShortcutInfo;

import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

public interface ShortcutService {
	public static final int TYPE_NONE = 0;
	public static final int TYPE_CHAT = 1;
	public static final int TYPE_CALL = 2;

	void updateShortcut(ContactModel contactModel);
	void updateShortcut(GroupModel groupModel);
	void updateShortcut(DistributionListModel distributionListModel);

	void createShortcut(ContactModel contactModel, int type);
	void createShortcut(GroupModel groupModel);
	void createShortcut(DistributionListModel distributionListModel);

	ShortcutInfo getShortcutInfo(ContactModel contactModel, int type);
	ShortcutInfo getShortcutInfo(GroupModel groupModel);
	ShortcutInfo getShortcutInfo(DistributionListModel distributionListModel);
}

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

import android.content.pm.ShortcutInfo;

import androidx.annotation.WorkerThread;
import androidx.core.content.pm.ShortcutInfoCompat;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

public interface ShortcutService {
	int TYPE_NONE = 0;
	int TYPE_CHAT = 1;
	int TYPE_CALL = 2;
	int TYPE_SHARE_SHORTCUT_CONTACT = 3;
	int TYPE_SHARE_SHORTCUT_GROUP = 4;
	int TYPE_SHARE_SHORTCUT_DISTRIBUTION_LIST = 5;

	@WorkerThread void publishRecentChatsAsSharingTargets();

	@WorkerThread void updateShortcut(ContactModel contactModel);
	@WorkerThread void updateShortcut(GroupModel groupModel);
	@WorkerThread void updateShortcut(DistributionListModel distributionListModel);

	@WorkerThread void createShortcut(ContactModel contactModel, int type);
	@WorkerThread void createShortcut(GroupModel groupModel);
	@WorkerThread void createShortcut(DistributionListModel distributionListModel);

	ShortcutInfo getShortcutInfo(ContactModel contactModel, int type);
	ShortcutInfo getShortcutInfo(GroupModel groupModel);
	ShortcutInfo getShortcutInfo(DistributionListModel distributionListModel);

	@WorkerThread void createShareTargetShortcut(ContactModel contactModel);
	@WorkerThread void createShareTargetShortcut(GroupModel groupModel);
	@WorkerThread void createShareTargetShortcut(DistributionListModel distributionListModel);

	@WorkerThread void deleteShortcut(ContactModel contactModel);
	@WorkerThread void deleteShortcut(GroupModel groupModel);
	@WorkerThread void deleteShortcut(DistributionListModel distributionListModel);
	@WorkerThread void deleteDynamicShortcuts();

	ShortcutInfoCompat getShortcutInfoCompat(ContactModel contactModel, int type);
	ShortcutInfoCompat getShortcutInfoCompat(GroupModel groupModel);
	ShortcutInfoCompat getShortcutInfoCompat(DistributionListModel distributionListModel);
}
